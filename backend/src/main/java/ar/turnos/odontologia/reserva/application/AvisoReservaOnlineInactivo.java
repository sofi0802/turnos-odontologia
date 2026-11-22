package ar.turnos.odontologia.reserva.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aviso que no envía nada: se usa mientras ningún módulo publique un {@link AvisoReservaOnline} (design D9). Deja una
 * línea de log con el tipo y el id del turno, sin nombre, DNI, email ni número de reserva (regla dura 6).
 */
public final class AvisoReservaOnlineInactivo implements AvisoReservaOnline {

    private static final Logger LOG = LoggerFactory.getLogger(AvisoReservaOnlineInactivo.class);

    @Override
    public void reservado(Long turnoId) {
        LOG.info("Aviso de reserva online del turno {} sin enviar: no hay un módulo de notificaciones", turnoId);
    }

    @Override
    public void cancelado(Long turnoId) {
        LOG.info("Aviso de cancelación online del turno {} sin enviar: no hay un módulo de notificaciones", turnoId);
    }

    @Override
    public void reprogramado(Long turnoIdNuevo) {
        LOG.info("Aviso de reprogramación online del turno {} sin enviar: no hay un módulo de notificaciones",
                turnoIdNuevo);
    }
}
