package ar.turnos.odontologia.coberturas.domain;

import java.math.BigDecimal;

/**
 * Resultado de conciliar una línea (RN-LQ-06), derivado del monto cobrado: {@code COBRADA} si es igual al solicitado,
 * {@code PARCIAL} si es mayor que 0 y menor, {@code RECHAZADA} si es 0.
 */
public enum ResultadoConciliacion {
    COBRADA, PARCIAL, RECHAZADA;

    /** Los dos montos ya son válidos ({@link MontoCobrado}); se comparan por valor, sin importar la escala. */
    public static ResultadoConciliacion de(BigDecimal cobrado, BigDecimal solicitado) {
        if (cobrado.signum() == 0) {
            return RECHAZADA;
        }
        return cobrado.compareTo(solicitado) == 0 ? COBRADA : PARCIAL;
    }
}
