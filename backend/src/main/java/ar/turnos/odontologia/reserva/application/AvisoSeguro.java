package ar.turnos.odontologia.reserva.application;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Llama al aviso después de que la agenda confirmó la operación y no deja que su falla la deshaga (design D9): una
 * excepción se registra con el tipo de aviso y el id del turno, sin datos del paciente, y se descarta.
 */
final class AvisoSeguro implements AvisoReservaOnline {

    private static final Logger LOG = LoggerFactory.getLogger(AvisoSeguro.class);

    private final AvisoReservaOnline aviso;

    AvisoSeguro(AvisoReservaOnline aviso) {
        this.aviso = Objects.requireNonNull(aviso, "aviso");
    }

    @Override
    public void reservado(Long turnoId) {
        intentar("reserva", turnoId, () -> aviso.reservado(turnoId));
    }

    @Override
    public void cancelado(Long turnoId) {
        intentar("cancelación", turnoId, () -> aviso.cancelado(turnoId));
    }

    @Override
    public void reprogramado(Long turnoIdNuevo) {
        intentar("reprogramación", turnoIdNuevo, () -> aviso.reprogramado(turnoIdNuevo));
    }

    private static void intentar(String tipo, Long turnoId, Runnable envio) {
        try {
            envio.run();
        } catch (RuntimeException e) {
            // solo el tipo, el id y la clase de la falla: el mensaje podría traer datos del paciente
            LOG.warn("No se pudo avisar la {} online del turno {}: {}", tipo, turnoId, e.getClass().getSimpleName());
        }
    }
}
