package ar.turnos.odontologia.coberturas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Cálculo de cobertura y copago de cada ítem (C-19, tarea 2.4, RN-PR-03): BigDecimal, escala 2 y HALF_UP. */
class CalculoItemTest {

    private static CalculoItem calcular(String precio, int cantidad, int porcentaje) {
        return CalculoItem.de(Precio.de(new BigDecimal(precio)), cantidad, Porcentaje.de(BigDecimal.valueOf(porcentaje)));
    }

    @ParameterizedTest(name = "{0} x {1} al {2} % = cobertura {4} y copago {5}")
    @DisplayName("Cálculo de cobertura y copago de cada ítem")
    @CsvSource({
            "15000.00,1,100,15000.00,15000.00,0.00",
            "25000.00,1,50,25000.00,12500.00,12500.00",
            "15000.00,1,80,15000.00,12000.00,3000.00",
            "30000.00,2,80,60000.00,48000.00,12000.00",
            "30000.00,1,0,30000.00,0.00,30000.00",
            "25000.15,1,50,25000.15,12500.08,12500.07",
            "0.01,1,50,0.01,0.01,0.00",
            "0.01,1,49,0.01,0.00,0.01",
            "0.05,1,10,0.05,0.01,0.04",
            "0.04,1,10,0.04,0.00,0.04"})
    void calculo(String precio, int cantidad, int porcentaje, String importe, String cobertura, String copago) {
        CalculoItem calculo = calcular(precio, cantidad, porcentaje);
        assertThat(calculo.importe()).isEqualTo(importe);
        assertThat(calculo.cobertura()).isEqualTo(cobertura);
        assertThat(calculo.copago()).isEqualTo(copago);
    }

    @Test
    @DisplayName("Cobertura más copago es siempre el importe, con escala 2")
    void coberturaMasCopagoEsElImporte() {
        Random azar = new Random(19);
        for (int i = 0; i < 2000; i++) {
            BigDecimal precio = BigDecimal.valueOf(1 + azar.nextInt(500_000_000), 2);
            int cantidad = 1 + azar.nextInt(99);
            int porcentaje = azar.nextInt(101);
            CalculoItem calculo = calcular(precio.toPlainString(), cantidad, porcentaje);
            assertThat(calculo.cobertura().add(calculo.copago())).isEqualTo(calculo.importe());
            assertThat(calculo.importe().scale()).isEqualTo(2);
            assertThat(calculo.cobertura().scale()).isEqualTo(2);
            assertThat(calculo.copago().scale()).isEqualTo(2);
            assertThat(calculo.copago().signum()).isGreaterThanOrEqualTo(0);
            BigDecimal esperada = precio.multiply(BigDecimal.valueOf(cantidad)).multiply(BigDecimal.valueOf(porcentaje))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            assertThat(calculo.cobertura()).isEqualTo(esperada);
        }
    }
}
