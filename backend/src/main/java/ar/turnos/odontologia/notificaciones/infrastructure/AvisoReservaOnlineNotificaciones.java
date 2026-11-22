package ar.turnos.odontologia.notificaciones.infrastructure;

import ar.turnos.odontologia.reserva.application.AvisoReservaOnline;
import org.springframework.stereotype.Component;

/**
 * Adaptador del puerto {@link AvisoReservaOnline} de la reserva online (C-10, design D12): pide el aviso de C-11 para
 * el turno reservado, cancelado o reprogramado. Las reglas de consentimiento, canal, contenido y reintentos son de
 * {@link ServicioNotificaciones}. {@code reserva} no conoce este módulo: depende solo del puerto, y mientras este
 * adaptador exista Spring lo usa en lugar del aviso inactivo.
 *
 * <p>{@code AvisoSeguro} (C-10) ya captura cualquier excepción de acá y la descarta, así una falla del aviso nunca
 * deshace la reserva; una falla del proveedor deja la notificación {@code FALLIDA} para su reintento.
 */
@Component
class AvisoReservaOnlineNotificaciones implements AvisoReservaOnline {

    private final ServicioNotificaciones notificaciones;

    AvisoReservaOnlineNotificaciones(ServicioNotificaciones notificaciones) {
        this.notificaciones = notificaciones;
    }

    @Override
    public void reservado(Long turnoId) {
        notificaciones.avisarReservado(turnoId);
    }

    @Override
    public void cancelado(Long turnoId) {
        notificaciones.avisarCancelado(turnoId);
    }

    @Override
    public void reprogramado(Long turnoIdNuevo) {
        notificaciones.avisarReprogramado(turnoIdNuevo);
    }
}
