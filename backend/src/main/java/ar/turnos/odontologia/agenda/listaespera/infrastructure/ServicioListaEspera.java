package ar.turnos.odontologia.agenda.listaespera.infrastructure;

import ar.turnos.odontologia.agenda.application.HorariosLiberados;
import ar.turnos.odontologia.agenda.domain.EstadoTurno;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.agenda.listaespera.application.AgendaListaEspera;
import ar.turnos.odontologia.agenda.listaespera.application.AgendaListaEspera.TurnoReservado;
import ar.turnos.odontologia.agenda.listaespera.application.ListaEspera;
import ar.turnos.odontologia.agenda.listaespera.application.ListaEspera.Aceptacion;
import ar.turnos.odontologia.agenda.listaespera.application.ListaEspera.AvisoAEnviar;
import ar.turnos.odontologia.agenda.listaespera.application.ListaEspera.HorarioLiberado;
import ar.turnos.odontologia.agenda.listaespera.application.ListaEsperaRepository.FilaInscripcion;
import ar.turnos.odontologia.agenda.listaespera.application.ListaEsperaRepository.VistaOferta;
import ar.turnos.odontologia.agenda.listaespera.application.PuertosListaEspera.AvisoOferta;
import ar.turnos.odontologia.agenda.listaespera.application.PuertosListaEspera.PlazoVigente;
import ar.turnos.odontologia.agenda.listaespera.domain.CodigoListaEspera;
import ar.turnos.odontologia.agenda.listaespera.domain.Inscripcion;
import ar.turnos.odontologia.agenda.listaespera.domain.ListaEsperaException;
import ar.turnos.odontologia.agenda.listaespera.domain.PlazoOferta;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Borde transaccional de la lista de espera (C-15). Cada caso de uso corre en su transacción; los avisos salen después
 * del commit, así un mensaje nunca lleva el token de una oferta que no se guardó. La aceptación usa tres pasos (design
 * D4): tomar la oferta, crear el turno por la reserva online y registrar el resultado.
 */
public class ServicioListaEspera implements HorariosLiberados {

    /** Actor de la auditoría del turno aceptado: el paciente puede no tener cuenta (RN-LE-07). */
    static final String PREFIJO_ACTOR = "lista-espera:";

    private static final Logger LOG = LoggerFactory.getLogger(ServicioListaEspera.class);

    private final ListaEspera casos;
    private final AgendaListaEspera agenda;
    private final PlazoVigente plazo;
    private final AvisoOferta aviso;
    private final TransactionTemplate transaccion;

    public ServicioListaEspera(ListaEspera casos, AgendaListaEspera agenda, PlazoVigente plazo, AvisoOferta aviso,
                               PlatformTransactionManager transacciones) {
        this.casos = casos;
        this.agenda = agenda;
        this.plazo = plazo;
        this.aviso = aviso;
        this.transaccion = new TransactionTemplate(transacciones);
        // La agenda puede llamar a liberado() desde afterCommit de su transacción: cada caso de uso abre la suya.
        this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ---------- inscripciones ----------

    public Inscripcion inscribir(Long pacienteId, Long prestacionId, Long profesionalId, LocalDate desde,
                                 LocalDate hasta, String actor) {
        return enTransaccion(() -> casos.inscribir(pacienteId, prestacionId, profesionalId, desde, hasta, actor));
    }

    public List<FilaInscripcion> activas() {
        return enTransaccion(casos::activas);
    }

    public List<FilaInscripcion> activasDe(Long pacienteId) {
        return enTransaccion(() -> casos.activasDe(pacienteId));
    }

    /** @param duenioId el paciente de la sesión, o {@code null} si da la baja el personal */
    public void darDeBaja(Long inscripcionId, Long duenioId) {
        enTransaccion(() -> casos.darDeBaja(inscripcionId, duenioId)).ifPresent(this::ofrecerYAvisar);
    }

    // ---------- ofertas ----------

    @Override
    public void liberado(Turno turnoCancelado) {
        if (turnoCancelado.estado() != EstadoTurno.CANCELADO) {
            return;
        }
        ofrecerYAvisar(new HorarioLiberado(turnoCancelado.id(), turnoCancelado.profesionalId(),
                turnoCancelado.intervalo().inicio()));
    }

    public VistaOferta ver(String token) {
        return enTransaccion(() -> casos.ver(token));
    }

    /**
     * Acepta la oferta: crea el turno con las validaciones de siempre. Si el horario ya no está, la oferta queda
     * {@code NO_DISPONIBLE}, la inscripción sigue activa y se responde {@code OFERTA_NO_DISPONIBLE}.
     */
    public TurnoReservado aceptar(String token) {
        Aceptacion aceptacion = enTransaccion(() -> casos.tomarParaAceptar(token));
        Inscripcion inscripcion = aceptacion.inscripcion();
        TurnoReservado turno;
        try {
            turno = agenda.reservar(inscripcion.pacienteId(), inscripcion.prestacionId(),
                    aceptacion.oferta().profesionalId(), aceptacion.oferta().inicio(),
                    PREFIJO_ACTOR + aceptacion.oferta().id());
        } catch (ListaEsperaException e) {
            if (e.codigo() == CodigoListaEspera.OFERTA_NO_DISPONIBLE) {
                enTransaccion(() -> {
                    casos.marcarNoDisponible(aceptacion);
                    return null;
                });
            } else {
                deshacer(aceptacion);
            }
            throw e;
        } catch (RuntimeException e) {
            deshacer(aceptacion);
            throw e;
        }
        Long turnoId = turno.turnoId();
        enTransaccion(() -> {
            casos.confirmarAceptacion(aceptacion, turnoId);
            return null;
        });
        return turno;
    }

    /** "No me interesa": el horario pasa al siguiente compatible. */
    public void rechazar(String token) {
        HorarioLiberado horario = enTransaccion(() -> casos.rechazar(token));
        ofrecerYAvisar(horario);
    }

    /**
     * La pasada de la tarea programada: vence ofertas e inscripciones, ofrece los horarios vencidos al siguiente y
     * reintenta los avisos que fallaron.
     */
    public void procesarVencimientos() {
        List<HorarioLiberado> horarios = enTransaccion(casos::vencer);
        horarios.forEach(this::ofrecerYAvisar);
        List<AvisoAEnviar> reintentos = enTransaccion(casos::avisosParaReintentar);
        reintentos.forEach(this::avisar);
    }

    // ---------- plazo ----------

    public PlazoOferta plazo() {
        return enTransaccion(plazo::actual);
    }

    public PlazoOferta guardarPlazo(int horas) {
        PlazoOferta nuevo = PlazoOferta.deHoras(horas);
        enTransaccion(() -> {
            plazo.guardar(nuevo);
            return null;
        });
        return nuevo;
    }

    // ---------- auxiliares ----------

    private void ofrecerYAvisar(HorarioLiberado horario) {
        Optional<AvisoAEnviar> creada = enTransaccion(() -> casos.ofrecer(horario));
        creada.ifPresent(this::avisar);
    }

    /** Manda el aviso fuera de toda transacción y guarda el resultado; si falla, la tarea lo reintenta. */
    private void avisar(AvisoAEnviar pendiente) {
        boolean enviado;
        try {
            enviado = aviso.avisar(pendiente.pacienteId(), pendiente.inicio(), pendiente.venceEn(), pendiente.token());
        } catch (RuntimeException e) {
            LOG.warn("Falló el aviso de la oferta {} de la lista de espera", pendiente.ofertaId());
            enviado = false;
        }
        boolean resultado = enviado;
        enTransaccion(() -> {
            casos.registrarAviso(pendiente.ofertaId(), resultado);
            return null;
        });
    }

    private void deshacer(Aceptacion aceptacion) {
        enTransaccion(() -> {
            casos.deshacerAceptacion(aceptacion);
            return null;
        });
    }

    private <T> T enTransaccion(Supplier<T> operacion) {
        return transaccion.execute(estado -> operacion.get());
    }
}
