package ar.turnos.odontologia.coberturas.domain;

import java.math.BigDecimal;

/**
 * Monto cobrado de una línea (RN-LQ-06): pesos con hasta dos decimales, entre 0 y el monto solicitado. Siempre
 * {@link BigDecimal} con escala 2, nunca {@code double}. El mensaje de error es fijo y no repite el importe.
 */
public record MontoCobrado(BigDecimal valor) {

    public static MontoCobrado de(BigDecimal valor, BigDecimal solicitado) {
        if (valor == null || valor.signum() < 0 || valor.stripTrailingZeros().scale() > 2
                || valor.compareTo(solicitado) > 0) {
            throw new CoberturaException(CodigoCobertura.MONTO_COBRADO_INVALIDO, "El monto cobrado no es válido");
        }
        return new MontoCobrado(valor.setScale(2));
    }
}
