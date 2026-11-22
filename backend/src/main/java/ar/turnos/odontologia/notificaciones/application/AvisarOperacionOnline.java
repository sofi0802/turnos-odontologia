package ar.turnos.odontologia.notificaciones.application;

import ar.turnos.odontologia.notificaciones.domain.Consultorio;
import ar.turnos.odontologia.notificaciones.domain.Enlaces;
import ar.turnos.odontologia.notificaciones.domain.Notificacion;
import ar.turnos.odontologia.notificaciones.domain.TipoNotificacion;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Caso de uso (acordado con C-10): avisa al paciente de una reserva, una cancelación o una reprogramación hecha online,
 * con las mismas reglas de consentimiento, canal y reintentos que el recordatorio. La reserva y la reprogramación
 * (del turno nuevo) crean una notificación {@code CONFIRMACION} con los links de cancelar y reprogramar; la cancelación
 * crea una {@code CANCELACION} sin links. Ninguna lleva token: el link de confirmar es solo del recordatorio.
 *
 * <p>Se llama después de que la operación quedó confirmada: si el envío falla la notificación queda {@code FALLIDA}
 * para su reintento y la operación no se deshace.
 */
public class AvisarOperacionOnline {

    private final AvisoDeTurno aviso;

    public AvisarOperacionOnline(TurnosARecordar turnos, NotificacionRepository notificaciones,
                                 List<NotificacionPort> canales, Consultorio consultorio, Enlaces enlaces,
                                 Clock reloj) {
        this.aviso = new AvisoDeTurno(turnos, notificaciones, new Despachador(canales, consultorio, enlaces), reloj);
    }

    public Optional<Notificacion> reservado(Long turnoId) {
        return aviso.avisar(turnoId, TipoNotificacion.CONFIRMACION);
    }

    /** El turno ya está cancelado cuando se avisa: se lee igual, en cualquier estado. */
    public Optional<Notificacion> cancelado(Long turnoId) {
        return aviso.avisar(turnoId, TipoNotificacion.CANCELACION);
    }

    /** @param turnoIdNuevo el turno que reemplaza al original: solo se avisa del nuevo */
    public Optional<Notificacion> reprogramado(Long turnoIdNuevo) {
        return aviso.avisar(turnoIdNuevo, TipoNotificacion.CONFIRMACION);
    }
}
