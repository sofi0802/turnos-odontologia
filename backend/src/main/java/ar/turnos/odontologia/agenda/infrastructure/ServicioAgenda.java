package ar.turnos.odontologia.agenda.infrastructure;

import ar.turnos.odontologia.agenda.application.AuditoriaTurnos;
import ar.turnos.odontologia.agenda.application.AuditoriaTurnos.Accion;
import ar.turnos.odontologia.agenda.application.CancelarTurno;
import ar.turnos.odontologia.agenda.application.CancelarTurnoCommand;
import ar.turnos.odontologia.agenda.application.ConfirmarTurno;
import ar.turnos.odontologia.agenda.application.ConsultaAgenda;
import ar.turnos.odontologia.agenda.application.CrearSobreturno;
import ar.turnos.odontologia.agenda.application.CrearSobreturnoCommand;
import ar.turnos.odontologia.agenda.application.CrearTurno;
import ar.turnos.odontologia.agenda.application.CrearTurnoCommand;
import ar.turnos.odontologia.agenda.application.ExtenderTurno;
import ar.turnos.odontologia.agenda.application.ExtenderTurnoCommand;
import ar.turnos.odontologia.agenda.application.MarcarAusencia;
import ar.turnos.odontologia.agenda.application.MarcarAusenciaCommand;
import ar.turnos.odontologia.agenda.application.ReasignarTurno;
import ar.turnos.odontologia.agenda.application.ReasignarTurnoCommand;
import ar.turnos.odontologia.agenda.application.ReprogramarTurno;
import ar.turnos.odontologia.agenda.application.ReprogramarTurnoCommand;
import ar.turnos.odontologia.agenda.application.SillonTomadoException;
import ar.turnos.odontologia.agenda.domain.EstadoTurno;
import ar.turnos.odontologia.agenda.domain.Turno;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Borde transaccional de la agenda (design D5): cada operación y su registro de auditoría ocurren juntos o no
 * ocurre ninguno. Si un caso de uso o la base rechazan la operación, se deshace todo (RN-AG-08, RN-AG-12).
 *
 * <p>Dos inserciones simultáneas que se pisan pueden quedar esperándose entre sí al verificar el {@code EXCLUDE},
 * y PostgreSQL aborta una por deadlock. Esa transacción se reintenta entera: en el reintento el otro turno ya
 * está confirmado y el dominio responde el rechazo correcto, nunca un error 500 (design D6).
 */
@Service
public class ServicioAgenda {

    static final int INTENTOS = 3;

    /** C-43: actor de la auditoría del paso automático a {@code ATENDIDO}. */
    static final String ACTOR_SISTEMA = "sistema";

    private final CrearTurno crearTurno;
    private final CrearSobreturno crearSobreturno;
    private final ExtenderTurno extenderTurno;
    private final CancelarTurno cancelarTurno;
    private final ReprogramarTurno reprogramarTurno;
    private final ConfirmarTurno confirmarTurno;
    private final ReasignarTurno reasignarTurno;
    private final MarcarAusencia marcarAusencia;
    private final CasosAsistencia asistencia;
    private final ConsultaAgenda consulta;
    private final AuditoriaTurnos auditoria;
    private final TransactionTemplate transaccion;
    private final TransactionTemplate lectura;

    ServicioAgenda(CrearTurno crearTurno, CrearSobreturno crearSobreturno, ExtenderTurno extenderTurno,
                   CancelarTurno cancelarTurno, ReprogramarTurno reprogramarTurno, ConfirmarTurno confirmarTurno,
                   ReasignarTurno reasignarTurno, MarcarAusencia marcarAusencia, CasosAsistencia asistencia,
                   ConsultaAgenda consulta, AuditoriaTurnos auditoria, PlatformTransactionManager transacciones) {
        this.crearTurno = crearTurno;
        this.crearSobreturno = crearSobreturno;
        this.extenderTurno = extenderTurno;
        this.cancelarTurno = cancelarTurno;
        this.reprogramarTurno = reprogramarTurno;
        this.confirmarTurno = confirmarTurno;
        this.reasignarTurno = reasignarTurno;
        this.marcarAusencia = marcarAusencia;
        this.asistencia = asistencia;
        this.consulta = consulta;
        this.auditoria = auditoria;
        this.transaccion = new TransactionTemplate(transacciones);
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setReadOnly(true);
    }

    public Turno crear(CrearTurnoCommand comando, String actor) {
        return enTransaccion(() -> {
            Turno turno = crearTurno.ejecutar(comando);
            auditoria.registrar(turno.id(), Accion.CREAR, actor, null);
            return turno;
        });
    }

    public Turno crearSobreturno(CrearSobreturnoCommand comando, String actor) {
        // Si la base rechaza un sillón que el dominio vio libre (otra operación lo tomó), esa transacción ya está
        // abortada: se rehace entera sin ese sillón. Cada vuelta suma un sillón descartado, así que termina.
        Set<Long> descartados = new HashSet<>();
        while (true) {
            try {
                return enTransaccion(() -> {
                    Turno turno = crearSobreturno.ejecutar(comando, descartados);
                    auditoria.registrar(turno.id(), Accion.SOBRETURNO, actor, null);
                    return turno;
                });
            } catch (SillonTomadoException e) {
                if (!descartados.add(e.sillonId())) {
                    throw e;
                }
            }
        }
    }

    public Turno extender(ExtenderTurnoCommand comando, String actor) {
        return enTransaccion(() -> {
            Turno turno = extenderTurno.ejecutar(comando);
            auditoria.registrar(turno.id(), Accion.EXTENDER, actor, null);
            return turno;
        });
    }

    public Turno cancelar(CancelarTurnoCommand comando, String actor) {
        return enTransaccion(() -> {
            Turno turno = cancelarTurno.ejecutar(comando);
            auditoria.registrar(turno.id(), Accion.CANCELAR, actor, null);
            return turno;
        });
    }

    public Turno reprogramar(ReprogramarTurnoCommand comando, String actor) {
        return enTransaccion(() -> {
            ReprogramarTurno.Reprogramacion resultado = reprogramarTurno.ejecutar(comando);
            auditoria.registrar(resultado.original().id(), Accion.REPROGRAMAR, actor, resultado.nuevo().id());
            auditoria.registrar(resultado.nuevo().id(), Accion.CREAR, actor, null);
            return resultado.nuevo();
        });
    }

    /**
     * Confirma un turno {@code RESERVADO} (C-11) y registra la auditoría {@code CONFIRMAR} en la misma transacción.
     * Si el turno no está reservado el caso de uso lo rechaza y no queda ningún registro. Si ya hay una
     * transacción abierta (la confirmación por token), participa de ella.
     */
    public Turno confirmar(Long turnoId, String actor) {
        return enTransaccion(() -> {
            Turno turno = confirmarTurno.ejecutar(turnoId);
            auditoria.registrar(turno.id(), Accion.CONFIRMAR, actor, null);
            return turno;
        });
    }

    /**
     * C-07: reasigna un turno afectado por una ausencia, con la misma auditoría que una reprogramación
     * ({@code REPROGRAMAR} en el original y {@code CREAR} en el nuevo, design D10). Devuelve el turno nuevo.
     */
    public Turno reasignar(ReasignarTurnoCommand comando, String actor) {
        return enTransaccion(() -> {
            ReprogramarTurno.Reprogramacion resultado = reasignarTurno.ejecutar(comando);
            auditoria.registrar(resultado.original().id(), Accion.REPROGRAMAR, actor, resultado.nuevo().id());
            auditoria.registrar(resultado.nuevo().id(), Accion.CREAR, actor, null);
            return resultado.nuevo();
        });
    }

    /**
     * C-13: marca la ausencia del paciente y registra la auditoría {@code AUSENTE} en la misma transacción. Si el
     * caso de uso la rechaza, no queda ningún registro (design D6).
     */
    public Turno marcarAusencia(MarcarAusenciaCommand comando, String actor) {
        return enTransaccion(() -> {
            Turno turno = marcarAusencia.ejecutar(comando);
            auditoria.registrar(turno.id(), Accion.AUSENTE, actor, null);
            return turno;
        });
    }

    /**
     * C-43: registra la llegada con la auditoría {@code LLEGADA}. Si el turno pasó a {@code ATENDIDO} en la misma
     * operación (la llegada se registró después de su fin con margen), también registra {@code ATENDIDO} con el mismo
     * actor.
     */
    public Turno registrarLlegada(Long turnoId, String actor) {
        return enTransaccion(() -> {
            Turno turno = asistencia.registrarLlegada().ejecutar(turnoId);
            auditoria.registrar(turno.id(), Accion.LLEGADA, actor, null);
            if (turno.estado() == EstadoTurno.ATENDIDO) {
                auditoria.registrar(turno.id(), Accion.ATENDIDO, actor, null);
            }
            return turno;
        });
    }

    /** C-43: deshace la ausencia con la auditoría {@code DESHACER_AUSENCIA}; si se rechaza, no queda registro. */
    public Turno deshacerAusencia(Long turnoId, String actor) {
        return enTransaccion(() -> {
            Turno turno = asistencia.deshacerAusencia().ejecutar(turnoId);
            auditoria.registrar(turno.id(), Accion.DESHACER_AUSENCIA, actor, null);
            return turno;
        });
    }

    /** C-43: el paso automático a {@code ATENDIDO}, con un registro por turno y el actor {@code sistema}. */
    public int pasarAAtendidos() {
        return enTransaccion(() -> {
            List<Long> cambiados = asistencia.pasarAAtendidos().ejecutar();
            cambiados.forEach(id -> auditoria.registrar(id, Accion.ATENDIDO, ACTOR_SISTEMA, null));
            return cambiados.size();
        });
    }

    public List<ConsultaAgenda.TurnoEnAgenda> agenda(Instant desde, Instant hasta, Long profesionalId, Long sillonId) {
        return lectura.execute(estado -> consulta.turnosDeAgenda(desde, hasta, profesionalId, sillonId));
    }

    /** Ejecuta la operación en una transacción y la reintenta entera si la base la abortó por un bloqueo. */
    private <T> T enTransaccion(Supplier<T> operacion) {
        for (int intento = 1; ; intento++) {
            try {
                return transaccion.execute(estado -> operacion.get());
            } catch (PessimisticLockingFailureException e) {
                if (intento >= INTENTOS) {
                    throw e;
                }
            }
        }
    }
}
