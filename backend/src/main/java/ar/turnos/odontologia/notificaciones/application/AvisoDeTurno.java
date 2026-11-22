package ar.turnos.odontologia.notificaciones.application;

import ar.turnos.odontologia.notificaciones.domain.Canal;
import ar.turnos.odontologia.notificaciones.domain.Notificacion;
import ar.turnos.odontologia.notificaciones.domain.TipoNotificacion;
import ar.turnos.odontologia.notificaciones.domain.TurnoARecordar;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Lo que tienen en común los avisos de un turno que no son el recordatorio (reasignación, reserva, reprogramación y
 * cancelación): leer el turno, respetar el consentimiento y el canal, registrar la notificación sin token y enviarla.
 * Si el envío falla, la notificación queda {@code FALLIDA} y {@link EnviarRecordatorios} la reintenta con la misma
 * política. Cada llamada genera su aviso: no hay límite de uno por turno.
 */
final class AvisoDeTurno {

    private final TurnosARecordar turnos;
    private final NotificacionRepository notificaciones;
    private final Despachador despachador;
    private final Clock reloj;

    AvisoDeTurno(TurnosARecordar turnos, NotificacionRepository notificaciones, Despachador despachador, Clock reloj) {
        this.turnos = Objects.requireNonNull(turnos, "turnos");
        this.notificaciones = Objects.requireNonNull(notificaciones, "notificaciones");
        this.despachador = Objects.requireNonNull(despachador, "despachador");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    /** @return el aviso, o vacío si el turno no existe o el paciente no puede recibir comunicaciones */
    Optional<Notificacion> avisar(Long turnoId, TipoNotificacion tipo) {
        Optional<TurnoARecordar> encontrado = turnos.deTurno(turnoId);
        if (encontrado.isEmpty() || !encontrado.get().contacto().puedeRecibirComunicaciones()) {
            return Optional.empty();
        }
        TurnoARecordar turno = encontrado.get();
        Canal canal = turno.contacto().canalDeEnvio().orElseThrow();
        Instant ahora = reloj.instant();
        // sin token: ningún aviso que no sea el recordatorio guarda una huella
        Notificacion aviso = notificaciones.insertar(Notificacion.pendiente(turno.turnoId(), canal, tipo, ahora, null));
        Notificacion resultado = despachador.despachar(aviso, turno, null, ahora);
        notificaciones.actualizar(resultado);
        return Optional.of(resultado);
    }
}
