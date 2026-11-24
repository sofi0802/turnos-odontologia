package ar.turnos.odontologia.coberturas.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cálculo de un ítem de presupuesto (RN-PR-03): importe = precio por cantidad; cobertura = importe por porcentaje
 * dividido 100, a centavos {@code HALF_UP}; copago = importe menos cobertura. Todo {@link BigDecimal} con escala 2.
 * La {@code CHECK presupuesto_item_cobertura} de V13 repite la misma cuenta con {@code round()} de PostgreSQL.
 */
public record CalculoItem(BigDecimal importe, BigDecimal cobertura, BigDecimal copago) {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    public static CalculoItem de(Precio precioUnitario, int cantidad, Porcentaje porcentaje) {
        BigDecimal importe = precioUnitario.valor().multiply(BigDecimal.valueOf(cantidad));
        BigDecimal cobertura = importe.multiply(BigDecimal.valueOf(porcentaje.valor()))
                .divide(CIEN, 2, RoundingMode.HALF_UP);
        return new CalculoItem(importe, cobertura, importe.subtract(cobertura));
    }
}
