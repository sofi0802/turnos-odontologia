package ar.turnos.odontologia.pagos.domain;

import ar.turnos.odontologia.caja.domain.CalculoCargo;
import ar.turnos.odontologia.caja.domain.CargoCalculado;
import ar.turnos.odontologia.caja.domain.Tarifa;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Importe de la seña (RN-SE-03) y su imputación al cargo (RN-SE-08). El copago sale del mismo cálculo que el cargo de
 * C-22 ({@link CalculoCargo}), así la seña es siempre un porcentaje de lo que el paciente va a deber.
 */
public final class CalculoSena {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private CalculoSena() {
    }

    /** La seña calculada al reservar, con el porcentaje y el copago que se guardan como foto. */
    public record SenaCalculada(int porcentaje, BigDecimal copagoBase, BigDecimal importe) {
    }

    /** Cuánto de la seña se cobra en el cargo y cuánto se reintegra. */
    public record Imputacion(BigDecimal cobro, BigDecimal reintegro) {
    }

    /**
     * @param tarifa la tarifa del turno, o {@code null} si la prestación no tiene arancel
     * @return la seña, o vacío si no hay arancel o el copago es cero (RN-SE-02)
     */
    public static Optional<SenaCalculada> calcular(Tarifa tarifa, int porcentaje) {
        if (tarifa == null) {
            return Optional.empty();
        }
        CargoCalculado cargo = CalculoCargo.calcular(tarifa);
        BigDecimal copago = cargo.copago();
        if (copago.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal importe = copago.multiply(BigDecimal.valueOf(porcentaje)).divide(CIEN, 2, RoundingMode.HALF_UP);
        if (importe.signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(new SenaCalculada(porcentaje, copago.setScale(2, RoundingMode.HALF_UP), importe));
    }

    /** Se imputa como máximo el copago; lo que sobra de la seña se reintegra (RN-SE-08, PA-63). */
    public static Imputacion imputar(BigDecimal sena, BigDecimal copago) {
        BigDecimal cobro = sena.min(copago.max(BigDecimal.ZERO)).setScale(2, RoundingMode.HALF_UP);
        return new Imputacion(cobro, sena.subtract(cobro).setScale(2, RoundingMode.HALF_UP));
    }
}
