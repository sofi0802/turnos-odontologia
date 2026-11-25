package ar.turnos.odontologia.pagos.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.caja.domain.Tarifa;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** RN-SE-02, RN-SE-03 y RN-SE-08: importe de la seña e imputación al cargo. */
class CalculoSenaTest {

    private static BigDecimal pesos(String valor) {
        return new BigDecimal(valor);
    }

    @Nested
    @DisplayName("Importe de la seña")
    class Importe {

        @Test
        @DisplayName("un particular paga el porcentaje del precio completo")
        void particular() {
            var sena = CalculoSena.calcular(new Tarifa(pesos("15000.00"), 0, null), 30);
            assertThat(sena).hasValueSatisfying(s -> {
                assertThat(s.copagoBase()).isEqualByComparingTo("15000.00");
                assertThat(s.importe()).isEqualByComparingTo("4500.00");
                assertThat(s.porcentaje()).isEqualTo(30);
            });
        }

        @Test
        @DisplayName("con cobertura del 40 % la seña sale del copago")
        void conCobertura() {
            var sena = CalculoSena.calcular(new Tarifa(pesos("15000.00"), 40, 7L), 30);
            assertThat(sena).hasValueSatisfying(s -> {
                assertThat(s.copagoBase()).isEqualByComparingTo("9000.00");
                assertThat(s.importe()).isEqualByComparingTo("2700.00");
            });
        }

        @Test
        @DisplayName("redondea a dos decimales hacia arriba en la mitad")
        void redondeo() {
            var sena = CalculoSena.calcular(new Tarifa(pesos("100.05"), 0, null), 50);
            assertThat(sena).hasValueSatisfying(s -> assertThat(s.importe()).isEqualByComparingTo("50.03"));
        }

        @Test
        @DisplayName("sin seña si la cobertura es del 100 %")
        void copagoCero() {
            assertThat(CalculoSena.calcular(new Tarifa(pesos("15000.00"), 100, 7L), 30)).isEmpty();
        }

        @Test
        @DisplayName("sin seña si la prestación no tiene arancel")
        void sinArancel() {
            assertThat(CalculoSena.calcular(null, 30)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Imputación al cargo")
    class Imputacion {

        @Test
        @DisplayName("la seña menor que el copago se imputa completa")
        void senaMenor() {
            var imputacion = CalculoSena.imputar(pesos("4500.00"), pesos("15000.00"));
            assertThat(imputacion.cobro()).isEqualByComparingTo("4500.00");
            assertThat(imputacion.reintegro()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("la seña mayor que el copago se imputa hasta el copago y el resto se reintegra")
        void senaMayor() {
            var imputacion = CalculoSena.imputar(pesos("4500.00"), pesos("3000.00"));
            assertThat(imputacion.cobro()).isEqualByComparingTo("3000.00");
            assertThat(imputacion.reintegro()).isEqualByComparingTo("1500.00");
        }

        @Test
        @DisplayName("con copago cero no hay cobro y se reintegra todo")
        void copagoCeroAlAtender() {
            var imputacion = CalculoSena.imputar(pesos("2700.00"), pesos("0.00"));
            assertThat(imputacion.cobro()).isEqualByComparingTo("0.00");
            assertThat(imputacion.reintegro()).isEqualByComparingTo("2700.00");
        }
    }
}
