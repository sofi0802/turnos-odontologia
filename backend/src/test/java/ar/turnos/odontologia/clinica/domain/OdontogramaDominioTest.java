package ar.turnos.odontologia.clinica.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Reglas puras del odontograma (C-13, tarea 2.1): notación FDI, caras por cuadrante y catálogo de estados. */
class OdontogramaDominioTest {

    @Nested
    class Piezas {

        @ParameterizedTest(name = "pieza {0}")
        @ValueSource(ints = {11, 18, 21, 28, 31, 38, 41, 48, 51, 55, 61, 65, 71, 75, 81, 85})
        @DisplayName("Acepta los bordes FDI de cada cuadrante, permanentes y temporales")
        void bordesValidos(int numero) {
            assertThat(PiezaDental.de(numero).numero()).isEqualTo(numero);
        }

        @ParameterizedTest(name = "pieza {0}")
        @ValueSource(ints = {0, 10, 19, 20, 29, 30, 39, 40, 49, 50, 56, 60, 66, 70, 76, 80, 86, 91})
        @DisplayName("Rechaza las piezas fuera de la notación FDI con PIEZA_INVALIDA")
        void fueraDeLaNotacion(int numero) {
            assertCodigo(() -> PiezaDental.de(numero), CodigoClinica.PIEZA_INVALIDA);
        }

        @Test
        @DisplayName("Conoce el cuadrante y si la pieza es superior")
        void cuadrante() {
            assertThat(PiezaDental.de(16).cuadrante()).isEqualTo(1);
            assertThat(PiezaDental.de(16).esSuperior()).isTrue();
            assertThat(PiezaDental.de(65).esSuperior()).isTrue();
            assertThat(PiezaDental.de(36).esSuperior()).isFalse();
            assertThat(PiezaDental.de(84).esSuperior()).isFalse();
        }
    }

    @Nested
    class Caras {

        @ParameterizedTest(name = "pieza {0}, cara {1}")
        @CsvSource({"21,P", "16,P", "55,P", "63,P", "31,L", "46,L", "75,L", "84,L", "16,O", "36,M", "11,D", "48,V"})
        @DisplayName("Acepta P en piezas superiores, L en inferiores y O, M, D y V en todas")
        void carasValidas(int pieza, String cara) {
            assertThat(CaraDental.para(cara, PiezaDental.de(pieza))).isEqualTo(CaraDental.valueOf(cara));
        }

        @ParameterizedTest(name = "pieza {0}, cara {1}")
        @CsvSource({"31,P", "46,P", "75,P", "21,L", "16,L", "55,L", "16,X", "16,o", "16,OM"})
        @DisplayName("Rechaza P en piezas inferiores, L en superiores y letras desconocidas con CARA_INVALIDA")
        void carasInvalidas(int pieza, String cara) {
            assertCodigo(() -> CaraDental.para(cara, PiezaDental.de(pieza)), CodigoClinica.CARA_INVALIDA);
        }

        @Test
        @DisplayName("Sin cara es la pieza completa")
        void sinCara() {
            assertThat(CaraDental.para(null, PiezaDental.de(36))).isNull();
        }
    }

    @Nested
    class Estados {

        @Test
        @DisplayName("Acepta los siete estados del catálogo")
        void catalogo() {
            assertThat(EstadoPieza.values()).extracting(Enum::name).containsExactly(
                    "SANO", "CARIES", "OBTURADO", "A_TRATAR", "AUSENTE", "CORONA", "ENDODONCIA");
            assertThat(EstadoPieza.de("A_TRATAR")).isEqualTo(EstadoPieza.A_TRATAR);
        }

        @ParameterizedTest(name = "estado {0}")
        @ValueSource(strings = {"ROTO", "caries", ""})
        @DisplayName("Rechaza un estado fuera del catálogo con DATOS_INVALIDOS")
        void estadoDesconocido(String estado) {
            assertCodigo(() -> EstadoPieza.de(estado), CodigoClinica.DATOS_INVALIDOS);
            assertCodigo(() -> EstadoPieza.de(null), CodigoClinica.DATOS_INVALIDOS);
        }
    }

    private static void assertCodigo(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion, CodigoClinica codigo) {
        assertThatThrownBy(accion).isInstanceOf(ClinicaException.class).extracting("codigo").isEqualTo(codigo);
    }
}
