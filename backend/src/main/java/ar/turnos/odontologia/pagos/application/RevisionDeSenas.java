package ar.turnos.odontologia.pagos.application;

import ar.turnos.odontologia.caja.application.ServicioCobros;
import ar.turnos.odontologia.pagos.application.Senas.Movimiento;
import ar.turnos.odontologia.pagos.application.TurnosDeSenas.Cancelacion;
import ar.turnos.odontologia.pagos.application.TurnosDeSenas.CargoDelTurno;
import ar.turnos.odontologia.pagos.domain.CalculoSena;
import ar.turnos.odontologia.pagos.domain.CalculoSena.Imputacion;
import ar.turnos.odontologia.pagos.domain.Causa;
import ar.turnos.odontologia.pagos.domain.EstadoSena;
import ar.turnos.odontologia.pagos.domain.ReglaFinal;
import ar.turnos.odontologia.pagos.domain.ReglaFinal.Destino;
import ar.turnos.odontologia.pagos.domain.ReglaFinal.FinDelTurno;
import ar.turnos.odontologia.pagos.domain.Sena;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Revisión periódica de las señas (design D4): vence las pendientes, las traslada al turno reprogramado, reintegra o
 * retiene según cómo terminó el turno, las imputa al cargo y pide los reintegros pendientes. Cada seña va en su propia
 * transacción, tomada con {@code FOR UPDATE SKIP LOCKED}, así no choca con la notificación ni con otra revisión.
 */
public class RevisionDeSenas {

    /** Prefijo del actor de las operaciones online del paciente en la auditoría (C-10, {@code ActorOnline}). */
    static final String ACTOR_ONLINE = "paciente-online:";

    private static final Logger LOG = LoggerFactory.getLogger(RevisionDeSenas.class);

    private final Senas senas;
    private final TurnosDeSenas turnos;
    private final Reintegros reintegros;
    private final CancelacionDelSistema cancelacion;
    private final ServicioCobros cobros;
    private final TransactionTemplate transaccion;
    private final Clock reloj;

    public RevisionDeSenas(Senas senas, TurnosDeSenas turnos, Reintegros reintegros, CancelacionDelSistema cancelacion,
                           ServicioCobros cobros, TransactionTemplate transaccion, Clock reloj) {
        this.senas = senas;
        this.turnos = turnos;
        this.reintegros = reintegros;
        this.cancelacion = cancelacion;
        this.cobros = cobros;
        this.transaccion = transaccion;
        this.reloj = reloj;
    }

    public void ejecutar() {
        // Los cargos primero: así una seña de un turno recién atendido se imputa en esta misma pasada.
        cobros.generarCargos();
        for (Long id : senas.idsEnEstados(List.of(EstadoSena.PENDIENTE, EstadoSena.PAGADA))) {
            try {
                transaccion.executeWithoutResult(estado -> revisar(id, reloj.instant()));
            } catch (RuntimeException e) {
                // Solo el id y el tipo del error, nunca importes ni datos del paciente (RN-SE-11).
                LOG.error("Falló la revisión de la seña {}: {}", id, e.getClass().getSimpleName());
            }
        }
        for (Long id : senas.idsConReintegroPendiente()) {
            try {
                reintegros.procesar(id);
            } catch (RuntimeException e) {
                LOG.error("Falló el reintegro de la seña {}: {}", id, e.getClass().getSimpleName());
            }
        }
    }

    private void revisar(long id, Instant ahora) {
        Optional<Sena> bloqueada = senas.porIdSinEspera(id);
        if (bloqueada.isEmpty()) {
            return;
        }
        Sena sena = bloqueada.get();
        Optional<Long> nuevo = turnos.turnoReprogramado(sena.turnoId());
        if (nuevo.isPresent()) {
            senas.moverATurno(id, nuevo.get());
            senas.registrarMovimiento(id, Movimiento.REPROGRAMACION, null, null, null, ahora);
            LOG.info("Seña {} trasladada al turno reprogramado", id);
            return;
        }
        if (sena.estado() == EstadoSena.PENDIENTE) {
            revisarPendiente(sena, ahora);
        } else {
            revisarPagada(sena);
        }
    }

    private void revisarPendiente(Sena sena, Instant ahora) {
        if (sena.turnoActivo() && sena.venceEn().isAfter(ahora)) {
            return;
        }
        if (sena.turnoActivo()) {
            cancelacion.cancelar(sena.turnoId());
        }
        senas.cambiarEstado(sena.id(), EstadoSena.VENCIDA);
        senas.registrarMovimiento(sena.id(), Movimiento.VENCIMIENTO, null, null, null, ahora);
        LOG.info("Seña {} vencida, referencia {}", sena.id(), sena.referencia());
    }

    private void revisarPagada(Sena sena) {
        switch (sena.estadoTurno()) {
            case "CANCELADO" -> terminar(sena, finPorCancelacion(sena));
            case "AUSENTE" -> terminar(sena, FinDelTurno.porAusencia());
            case "ATENDIDO" -> turnos.cargo(sena.turnoId()).ifPresent(cargo -> imputar(sena, cargo));
            default -> { }
        }
    }

    private FinDelTurno finPorCancelacion(Sena sena) {
        Cancelacion cancelada = turnos.cancelacion(sena.turnoId())
                .orElse(new Cancelacion(CancelacionDelSistema.ACTOR, reloj.instant(), null));
        boolean online = cancelada.actor() != null && cancelada.actor().startsWith(ACTOR_ONLINE);
        Causa causa = cancelada.causa() == null ? null : Causa.valueOf(cancelada.causa());
        return FinDelTurno.cancelado(online, cancelada.fecha(), turnos.inicio(sena.turnoId()), causa);
    }

    private void terminar(Sena sena, FinDelTurno fin) {
        if (ReglaFinal.decidir(fin) == Destino.RETENCION) {
            senas.cambiarEstado(sena.id(), EstadoSena.RETENIDA);
            LOG.info("Seña {} retenida por el consultorio", sena.id());
        } else {
            // Queda pagada hasta que la pasarela haga el reintegro; ahí pasa a devuelta (RN-SE-09).
            senas.fijarReintegro(sena.id(), sena.importe());
        }
    }

    private void imputar(Sena sena, CargoDelTurno cargo) {
        Imputacion imputacion = CalculoSena.imputar(sena.importe(), cargo.copago());
        if (imputacion.cobro().signum() > 0) {
            long cobro = cobros.imputarSena(cargo.id(), imputacion.cobro());
            senas.imputar(sena.id(), cobro);
            LOG.info("Seña {} imputada al cargo {}", sena.id(), cargo.id());
        }
        if (imputacion.reintegro().signum() > 0) {
            senas.fijarReintegro(sena.id(), imputacion.reintegro());
        }
    }
}
