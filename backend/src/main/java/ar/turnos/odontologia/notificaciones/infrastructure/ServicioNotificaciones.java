package ar.turnos.odontologia.notificaciones.infrastructure;

import ar.turnos.odontologia.notificaciones.application.AvisarOperacionOnline;
import ar.turnos.odontologia.notificaciones.application.AvisarReasignacion;
import ar.turnos.odontologia.notificaciones.application.ConfirmarAsistencia;
import ar.turnos.odontologia.notificaciones.application.EnviarRecordatorios;
import ar.turnos.odontologia.notificaciones.application.EnviarRecordatorios.Resumen;
import ar.turnos.odontologia.notificaciones.application.TurnoConfirmado;
import ar.turnos.odontologia.notificaciones.domain.Notificacion;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Borde transaccional de las notificaciones (design D4, D5).
 *
 * <ul>
 *   <li>Los envíos <b>no</b> corren en una transacción: cada paso (insertar la notificación, enviar, guardar el
 *       resultado) es atómico por sí solo. Así un envío lento o fallido nunca deja una transacción abierta ni
 *       pierde el registro de lo que ya se mandó.</li>
 *   <li>La confirmación sí: consumir el token, pasar el turno a {@code CONFIRMADO} y registrar la auditoría
 *       ocurren juntos o no ocurre ninguno. Si el turno no se puede confirmar, el token no se consume.</li>
 * </ul>
 */
@Service
public class ServicioNotificaciones {

    private final EnviarRecordatorios enviarRecordatorios;
    private final AvisarReasignacion avisarReasignacion;
    private final AvisarOperacionOnline avisarOperacionOnline;
    private final ConfirmarAsistencia confirmarAsistencia;
    private final TransactionTemplate transaccion;

    ServicioNotificaciones(EnviarRecordatorios enviarRecordatorios, AvisarReasignacion avisarReasignacion,
                           AvisarOperacionOnline avisarOperacionOnline, ConfirmarAsistencia confirmarAsistencia,
                           PlatformTransactionManager transacciones) {
        this.enviarRecordatorios = enviarRecordatorios;
        this.avisarReasignacion = avisarReasignacion;
        this.avisarOperacionOnline = avisarOperacionOnline;
        this.confirmarAsistencia = confirmarAsistencia;
        this.transaccion = new TransactionTemplate(transacciones);
    }

    /** Lo que ejecuta la tarea programada; los tests lo llaman a mano. */
    public Resumen enviarRecordatorios() {
        return enviarRecordatorios.ejecutar();
    }

    /** C-07 lo llama después de reasignar un turno por la ausencia del profesional (RN-AG-16). */
    public Optional<Notificacion> avisarReasignacion(Long turnoId) {
        return avisarReasignacion.ejecutar(turnoId);
    }

    /** La reserva online pide el aviso de un turno nuevo (CONFIRMACION, con links de cancelar y reprogramar). */
    public Optional<Notificacion> avisarReservado(Long turnoId) {
        return avisarOperacionOnline.reservado(turnoId);
    }

    /** La cancelación online pide el aviso de un turno que ya está cancelado (CANCELACION, sin links). */
    public Optional<Notificacion> avisarCancelado(Long turnoId) {
        return avisarOperacionOnline.cancelado(turnoId);
    }

    /** La reprogramación online pide el aviso del turno nuevo (CONFIRMACION). */
    public Optional<Notificacion> avisarReprogramado(Long turnoIdNuevo) {
        return avisarOperacionOnline.reprogramado(turnoIdNuevo);
    }

    /**
     * Confirma la asistencia con el token del recordatorio.
     *
     * @throws ar.turnos.odontologia.notificaciones.domain.ConfirmacionRechazadaException si el token no existe, ya
     *     se usó o el turno ya no se puede confirmar; en cualquiera de los tres casos no queda nada cambiado
     */
    public TurnoConfirmado confirmar(String token) {
        return transaccion.execute(estado -> confirmarAsistencia.ejecutar(token));
    }
}
