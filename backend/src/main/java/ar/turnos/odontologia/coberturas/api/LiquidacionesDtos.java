package ar.turnos.odontologia.coberturas.api;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Cuerpos de entrada de la API de liquidaciones (C-20, design D9). El período, el monto cobrado y el motivo se validan
 * en el dominio para responder el código y el campo de la spec sin repetir el valor recibido (RN-LQ-08); el monto llega
 * como {@code BigDecimal}, nunca como {@code double}.
 */
final class LiquidacionesDtos {

    private LiquidacionesDtos() {
    }

    record GeneracionSolicitud(@NotNull Long obraSocialId, String periodo) {
    }

    record CodigoSolicitud(@NotNull Long nomencladorId) {
    }

    record ConciliacionSolicitud(BigDecimal montoCobrado, String motivo) {
    }
}
