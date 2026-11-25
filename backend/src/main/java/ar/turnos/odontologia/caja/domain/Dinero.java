package ar.turnos.odontologia.caja.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Un monto en pesos, mayor o igual a cero, con dos decimales (RN-CA-04). */
public record Dinero(BigDecimal valor) implements Comparable<Dinero> {

    public static final Dinero CERO = new Dinero(BigDecimal.ZERO.setScale(2));
    private static final BigDecimal MAXIMO = new BigDecimal("9999999999.99");

    public Dinero {
        Objects.requireNonNull(valor, "valor");
    }

    /** Monto mayor o igual a cero, con hasta dos decimales. */
    public static Dinero de(BigDecimal valor) {
        if (valor == null) {
            throw new ImporteInvalidoException("El importe es obligatorio.");
        }
        if (valor.signum() < 0) {
            throw new ImporteInvalidoException("El importe no puede ser negativo.");
        }
        if (valor.stripTrailingZeros().scale() > 2) {
            throw new ImporteInvalidoException("El importe tiene hasta dos decimales.");
        }
        if (valor.compareTo(MAXIMO) > 0) {
            throw new ImporteInvalidoException("El importe es demasiado grande.");
        }
        return new Dinero(valor.setScale(2, RoundingMode.UNNECESSARY));
    }

    /** Monto mayor que cero, como el de un cobro. */
    public static Dinero positivo(BigDecimal valor) {
        Dinero dinero = de(valor);
        if (dinero.valor.signum() == 0) {
            throw new ImporteInvalidoException("El importe tiene que ser mayor que cero.");
        }
        return dinero;
    }

    public Dinero mas(Dinero otro) {
        return new Dinero(valor.add(otro.valor));
    }

    /** Resta que puede dar negativo: se usa para diferencias, no para guardar montos. */
    public BigDecimal menos(Dinero otro) {
        return valor.subtract(otro.valor);
    }

    @Override
    public int compareTo(Dinero otro) {
        return valor.compareTo(otro.valor);
    }
}
