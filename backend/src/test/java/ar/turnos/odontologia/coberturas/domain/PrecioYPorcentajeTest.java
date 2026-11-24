package ar.turnos.odontologia.coberturas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Valores de dinero y porcentaje (C-19, tarea 2.2): escenarios de "Precio válido" y "Porcentaje válido". */
class PrecioYPorcentajeTest {

    private static void assertInvalido(Runnable accion, String campo) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(CoberturaException.class, e -> {
            assertThat(e.codigo()).isEqualTo(CodigoCobertura.DATOS_INVALIDOS);
            assertThat(e.campo()).isEqualTo(campo);
        });
    }

    @ParameterizedTest
    @DisplayName("Precio inválido: cero, negativo, tres decimales o más que el máximo")
    @ValueSource(strings = {"0", "0.00", "-100.00", "15000.005", "10000000000.00", "9999999999.991"})
    void precioInvalido(String valor) {
        assertInvalido(() -> Precio.de(new BigDecimal(valor)), "precio");
    }

    @Test
    @DisplayName("Un precio ausente es inválido")
    void precioAusente() {
        assertInvalido(() -> Precio.de(null), "precio");
    }

    @Test
    @DisplayName("Precio válido: el mínimo, el máximo y los enteros salen con dos decimales")
    void precioValido() {
        assertThat(Precio.de(new BigDecimal("0.01")).valor()).isEqualTo("0.01");
        assertThat(Precio.de(new BigDecimal("9999999999.99")).valor()).isEqualTo("9999999999.99");
        assertThat(Precio.de(new BigDecimal("15000")).valor()).isEqualTo("15000.00");
        assertThat(Precio.de(new BigDecimal("15000.5")).valor()).isEqualTo("15000.50");
        assertThat(Precio.de(new BigDecimal("15000.000")).valor()).isEqualTo("15000.00");
    }

    @ParameterizedTest
    @DisplayName("Porcentaje inválido: negativo, 101 o decimal")
    @ValueSource(strings = {"-1", "101", "50.5", "100.01", "0.5"})
    void porcentajeInvalido(String valor) {
        assertInvalido(() -> Porcentaje.de(new BigDecimal(valor)), "porcentaje");
    }

    @Test
    @DisplayName("Un porcentaje ausente es inválido")
    void porcentajeAusente() {
        assertInvalido(() -> Porcentaje.de(null), "porcentaje");
    }

    @ParameterizedTest
    @DisplayName("Porcentaje válido: de 0 a 100 enteros")
    @ValueSource(strings = {"0", "1", "50", "100", "50.0"})
    void porcentajeValido(String valor) {
        assertThat(Porcentaje.de(new BigDecimal(valor)).valor()).isEqualTo(new BigDecimal(valor).intValue());
    }
}
