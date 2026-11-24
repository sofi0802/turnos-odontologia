package ar.turnos.odontologia.coberturas.domain;

import java.math.BigDecimal;

/**
 * Porcentaje de cobertura (RN-PR-02): un entero de 0 a 100. Se recibe como {@link BigDecimal} para poder rechazar
 * {@code 50.5} en lugar de que Jackson lo recorte en silencio (design D3).
 */
public final class Porcentaje {

    private final int valor;

    private Porcentaje(int valor) {
        this.valor = valor;
    }

    public static Porcentaje de(BigDecimal valor) {
        if (valor == null || valor.signum() < 0 || valor.compareTo(BigDecimal.valueOf(100)) > 0
                || valor.stripTrailingZeros().scale() > 0) {
            throw CoberturaException.datosInvalidos("porcentaje", "El porcentaje no es válido");
        }
        return new Porcentaje(valor.intValueExact());
    }

    /** Un porcentaje ya guardado en la base. */
    public static Porcentaje guardado(int valor) {
        return new Porcentaje(valor);
    }

    public int valor() {
        return valor;
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof Porcentaje p && p.valor == valor;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(valor);
    }

    @Override
    public String toString() {
        return "Porcentaje[" + valor + "]";
    }
}
