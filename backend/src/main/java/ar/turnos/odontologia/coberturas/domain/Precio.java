package ar.turnos.odontologia.coberturas.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Precio en pesos argentinos (RN-PR-01): mayor que cero, con como máximo dos decimales y sin superar lo que entra en
 * {@code NUMERIC(12,2)}. Siempre {@link BigDecimal} con escala 2, nunca {@code double} (design D3).
 */
public final class Precio {

    public static final BigDecimal MAXIMO = new BigDecimal("9999999999.99");

    private final BigDecimal valor;

    private Precio(BigDecimal valor) {
        this.valor = valor;
    }

    /** Valida el precio recibido; el mensaje es fijo y no repite el valor. */
    public static Precio de(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0 || valor.stripTrailingZeros().scale() > 2
                || valor.compareTo(MAXIMO) > 0) {
            throw CoberturaException.datosInvalidos("precio", "El precio no es válido");
        }
        return new Precio(valor.setScale(2));
    }

    /** Un precio ya guardado en la base: se conserva tal cual, sin volver a validarlo. */
    public static Precio guardado(BigDecimal valor) {
        return new Precio(valor.setScale(2));
    }

    public BigDecimal valor() {
        return valor;
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof Precio p && p.valor.equals(valor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(valor);
    }

    @Override
    public String toString() {
        return "Precio[***]";
    }
}
