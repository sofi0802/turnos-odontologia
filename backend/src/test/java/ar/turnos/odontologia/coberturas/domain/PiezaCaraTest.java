package ar.turnos.odontologia.coberturas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pieza FDI y cara de un ítem (C-19, tarea 2.3): las mismas reglas que {@code odontograma_registro} de V8. */
class PiezaCaraTest {

    private static void assertInvalido(Runnable accion, String campo) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(CoberturaException.class, e -> {
            assertThat(e.codigo()).isEqualTo(CodigoCobertura.DATOS_INVALIDOS);
            assertThat(e.campo()).isEqualTo(campo);
        });
    }

    @ParameterizedTest
    @DisplayName("Pieza fuera de la notación FDI")
    @ValueSource(ints = {0, 10, 19, 29, 39, 49, 56, 66, 76, 86, 90, 100, -16})
    void piezaFueraDeFdi(int pieza) {
        assertInvalido(() -> PiezaCara.de(pieza, null), "pieza");
    }

    @ParameterizedTest
    @DisplayName("Piezas permanentes y temporales válidas")
    @ValueSource(ints = {11, 18, 21, 28, 31, 38, 41, 48, 51, 55, 61, 65, 71, 75, 81, 85})
    void piezasValidas(int pieza) {
        assertThat(PiezaCara.de(pieza, null).pieza()).isEqualTo(pieza);
    }

    @Test
    @DisplayName("Cara palatina en una pieza inferior y lingual en una superior")
    void caraDelLadoEquivocado() {
        assertInvalido(() -> PiezaCara.de(36, "P"), "cara");
        assertInvalido(() -> PiezaCara.de(16, "L"), "cara");
        assertInvalido(() -> PiezaCara.de(75, "P"), "cara");
        assertInvalido(() -> PiezaCara.de(55, "L"), "cara");
    }

    @Test
    @DisplayName("Cara sin pieza")
    void caraSinPieza() {
        assertInvalido(() -> PiezaCara.de(null, "O"), "cara");
    }

    @Test
    @DisplayName("Cara desconocida o en minúscula")
    void caraDesconocida() {
        assertInvalido(() -> PiezaCara.de(16, "X"), "cara");
        assertInvalido(() -> PiezaCara.de(16, "o"), "cara");
        assertInvalido(() -> PiezaCara.de(16, ""), "cara");
    }

    @Test
    @DisplayName("Pieza y cara válidas, y un ítem sin pieza ni cara")
    void validas() {
        assertThat(PiezaCara.de(36, "L")).isEqualTo(new PiezaCara(36, "L"));
        assertThat(PiezaCara.de(16, "P")).isEqualTo(new PiezaCara(16, "P"));
        assertThat(PiezaCara.de(16, "O").cara()).isEqualTo("O");
        assertThat(PiezaCara.de(36, null).cara()).isNull();
        assertThat(PiezaCara.de(null, null)).isEqualTo(PiezaCara.NINGUNA);
    }
}
