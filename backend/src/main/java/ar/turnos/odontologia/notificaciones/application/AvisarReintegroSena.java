package ar.turnos.odontologia.notificaciones.application;

import ar.turnos.odontologia.notificaciones.domain.Consultorio;
import ar.turnos.odontologia.notificaciones.domain.Enlaces;
import ar.turnos.odontologia.notificaciones.domain.Notificacion;
import ar.turnos.odontologia.notificaciones.domain.TipoNotificacion;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Caso de uso de C-23: avisa al paciente que se reintegró la seña de su turno (RN-SE-11), con las mismas reglas de
 * consentimiento y canal que el resto de los avisos. El mensaje dice solo la fecha, la hora y el consultorio: nunca el
 * importe (RN-CA-08) ni la prestación (RN-NO-01).
 */
public class AvisarReintegroSena {

    private final AvisoDeTurno aviso;

    public AvisarReintegroSena(TurnosARecordar turnos, NotificacionRepository notificaciones,
                               List<NotificacionPort> canales, Consultorio consultorio, Clock reloj) {
        this.aviso = new AvisoDeTurno(turnos, notificaciones,
                new Despachador(canales, consultorio, Enlaces.de("")), reloj);
    }

    /** @return el aviso, o vacío si el turno no existe o el paciente no puede recibir comunicaciones */
    public Optional<Notificacion> ejecutar(Long turnoId) {
        return aviso.avisar(turnoId, TipoNotificacion.REINTEGRO_SENA);
    }
}
