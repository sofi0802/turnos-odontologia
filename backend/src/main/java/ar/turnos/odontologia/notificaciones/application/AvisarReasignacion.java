package ar.turnos.odontologia.notificaciones.application;

import ar.turnos.odontologia.notificaciones.domain.Consultorio;
import ar.turnos.odontologia.notificaciones.domain.Enlaces;
import ar.turnos.odontologia.notificaciones.domain.Notificacion;
import ar.turnos.odontologia.notificaciones.domain.TipoNotificacion;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Caso de uso: avisa a un paciente que su turno fue reasignado por la ausencia del profesional (RN-AG-16), con
 * las mismas reglas de consentimiento, canal y contenido que el recordatorio. El aviso no lleva token ni link. C-07
 * lo llama al reasignar, después de guardar el turno con su nueva fecha, hora y profesional.
 */
public class AvisarReasignacion {

    private final AvisoDeTurno aviso;

    public AvisarReasignacion(TurnosARecordar turnos, NotificacionRepository notificaciones,
                              List<NotificacionPort> canales, Consultorio consultorio, Clock reloj) {
        this.aviso = new AvisoDeTurno(turnos, notificaciones,
                new Despachador(canales, consultorio, Enlaces.de("")), reloj);
    }

    /** @return el aviso, o vacío si el turno no existe o el paciente no puede recibir comunicaciones */
    public Optional<Notificacion> ejecutar(Long turnoId) {
        return aviso.avisar(turnoId, TipoNotificacion.REASIGNACION);
    }
}
