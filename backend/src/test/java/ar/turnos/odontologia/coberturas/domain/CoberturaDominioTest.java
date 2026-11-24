package ar.turnos.odontologia.coberturas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Reglas puras de las coberturas (C-18, tareas 2.1 a 2.3): error con código, número de afiliado y vigencia. */
class CoberturaDominioTest {

    @Nested
    class Errores {

        @Test
        @DisplayName("El error lleva un código estable, un mensaje y el campo")
        void errorConCodigoYCampo() {
            CoberturaException e = CoberturaException.datosInvalidos("numeroAfiliado", "El número no es válido");
            assertThat(e.codigo()).isEqualTo(CodigoCobertura.DATOS_INVALIDOS);
            assertThat(e.campo()).isEqualTo("numeroAfiliado");
            assertThat(e).hasMessage("El número no es válido");
        }

        @Test
        @DisplayName("El error sin campo no lo inventa")
        void errorSinCampo() {
            assertThat(CoberturaException.noEncontrado("No existe el plan").campo()).isNull();
            assertThat(CoberturaException.noEncontrado("No existe el plan").codigo())
                    .isEqualTo(CodigoCobertura.RECURSO_NO_ENCONTRADO);
        }

        @Test
        @DisplayName("Los tipos de cobertura son obra social y prepaga")
        void tipos() {
            assertThat(TipoCobertura.de("OBRA_SOCIAL")).isEqualTo(TipoCobertura.OBRA_SOCIAL);
            assertThat(TipoCobertura.de("PREPAGA")).isEqualTo(TipoCobertura.PREPAGA);
            assertThatThrownBy(() -> TipoCobertura.de("MUTUAL")).isInstanceOfSatisfying(CoberturaException.class,
                    e -> {
                        assertThat(e.codigo()).isEqualTo(CodigoCobertura.DATOS_INVALIDOS);
                        assertThat(e.campo()).isEqualTo("tipo");
                    });
        }
    }

    @Nested
    class Numero {

        @Test
        @DisplayName("Quita los espacios de los extremos")
        void espaciosEnLosExtremos() {
            assertThat(NumeroAfiliado.de("  OSE-000001  ").valor()).isEqualTo("OSE-000001");
        }

        @ParameterizedTest(name = "número \"{0}\"")
        @ValueSource(strings = {"OSE-000001", "a", "12.345/6-7", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
        @DisplayName("Acepta letras, números, guion, barra y punto, hasta 30 caracteres")
        void validos(String texto) {
            assertThat(NumeroAfiliado.de(texto).valor()).isEqualTo(texto);
        }

        @ParameterizedTest(name = "número \"{0}\"")
        @ValueSource(strings = {"OSE 0001#", "OSE 0001", "ñandú", "a_b", "", "   ", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
        @DisplayName("Rechaza vacío, caracteres no permitidos y más de 30 caracteres con DATOS_INVALIDOS")
        void invalidos(String texto) {
            assertThatThrownBy(() -> NumeroAfiliado.de(texto)).isInstanceOfSatisfying(CoberturaException.class, e -> {
                assertThat(e.codigo()).isEqualTo(CodigoCobertura.DATOS_INVALIDOS);
                assertThat(e.campo()).isEqualTo("numeroAfiliado");
                // RN-OS-07: el mensaje no repite el valor recibido.
                if (!texto.isBlank()) {
                    assertThat(e.getMessage()).doesNotContain(texto.strip());
                }
            });
        }

        @Test
        @DisplayName("Un número nulo es inválido")
        void nulo() {
            assertThatThrownBy(() -> NumeroAfiliado.de(null)).isInstanceOf(CoberturaException.class);
        }

        @Test
        @DisplayName("No muestra el número en toString")
        void sinToString() {
            assertThat(NumeroAfiliado.de("OSE-000001").toString()).doesNotContain("OSE-000001");
        }
    }

    @Nested
    class VigenciaDeUnaAfiliacion {

        private final LocalDate enero1 = LocalDate.of(2026, 1, 1);
        private final LocalDate junio30 = LocalDate.of(2026, 6, 30);

        @Test
        @DisplayName("Una vigencia sin fin está abierta y contiene toda fecha desde el inicio")
        void abierta() {
            Vigencia vigencia = new Vigencia(enero1, null);
            assertThat(vigencia.estaAbierta()).isTrue();
            assertThat(vigencia.contiene(enero1)).isTrue();
            assertThat(vigencia.contiene(LocalDate.of(2099, 1, 1))).isTrue();
            assertThat(vigencia.contiene(enero1.minusDays(1))).isFalse();
        }

        @Test
        @DisplayName("Los días de inicio y de fin son inclusivos")
        void inclusiva() {
            Vigencia vigencia = new Vigencia(enero1, junio30);
            assertThat(vigencia.contiene(enero1)).isTrue();
            assertThat(vigencia.contiene(junio30)).isTrue();
            assertThat(vigencia.contiene(junio30.plusDays(1))).isFalse();
        }

        @Test
        @DisplayName("Vigencia de un solo día")
        void unSoloDia() {
            Vigencia vigencia = new Vigencia(enero1, enero1);
            assertThat(vigencia.contiene(enero1)).isTrue();
            assertThat(vigencia.contiene(enero1.plusDays(1))).isFalse();
        }

        @Test
        @DisplayName("La fecha de fin anterior a la de inicio es VIGENCIA_INVALIDA")
        void finAnterior() {
            assertThatThrownBy(() -> new Vigencia(enero1, enero1.minusDays(1)))
                    .isInstanceOfSatisfying(CoberturaException.class,
                            e -> assertThat(e.codigo()).isEqualTo(CodigoCobertura.VIGENCIA_INVALIDA));
        }

        @Test
        @DisplayName("Sin fecha de inicio es DATOS_INVALIDOS con el campo vigenteDesde")
        void sinInicio() {
            assertThatThrownBy(() -> new Vigencia(null, null)).isInstanceOfSatisfying(CoberturaException.class, e -> {
                assertThat(e.codigo()).isEqualTo(CodigoCobertura.DATOS_INVALIDOS);
                assertThat(e.campo()).isEqualTo("vigenteDesde");
            });
        }

        @Test
        @DisplayName("Una vigencia abierta se superpone con cualquier posterior")
        void abiertaSeSuperpone() {
            Vigencia abierta = new Vigencia(enero1, null);
            assertThat(abierta.seSuperponeCon(new Vigencia(LocalDate.of(2026, 11, 1), null))).isTrue();
            assertThat(new Vigencia(LocalDate.of(2026, 11, 1), null).seSuperponeCon(abierta)).isTrue();
            assertThat(abierta.seSuperponeCon(new Vigencia(enero1.minusYears(1), enero1.minusDays(1)))).isFalse();
        }

        @Test
        @DisplayName("Compartir un solo día en el borde es superposición")
        void bordeDeUnDia() {
            Vigencia hastaJunio = new Vigencia(enero1, junio30);
            assertThat(hastaJunio.seSuperponeCon(new Vigencia(junio30, null))).isTrue();
            assertThat(new Vigencia(junio30, null).seSuperponeCon(hastaJunio)).isTrue();
        }

        @Test
        @DisplayName("Las vigencias contiguas no se superponen")
        void contiguas() {
            Vigencia hastaJunio = new Vigencia(enero1, junio30);
            Vigencia desdeJulio = new Vigencia(junio30.plusDays(1), null);
            assertThat(hastaJunio.seSuperponeCon(desdeJulio)).isFalse();
            assertThat(desdeJulio.seSuperponeCon(hastaJunio)).isFalse();
        }

        @Test
        @DisplayName("Cerrar fija la fecha de fin y respeta el inicio")
        void cerrar() {
            Vigencia abierta = new Vigencia(enero1, null);
            assertThat(abierta.cerrarEn(junio30)).isEqualTo(new Vigencia(enero1, junio30));
            assertThatThrownBy(() -> abierta.cerrarEn(enero1.minusDays(1)))
                    .isInstanceOfSatisfying(CoberturaException.class,
                            e -> assertThat(e.codigo()).isEqualTo(CodigoCobertura.VIGENCIA_INVALIDA));
        }

        @Test
        @DisplayName("Cerrar una vigencia ya cerrada es AFILIACION_CERRADA")
        void cerrarCerrada() {
            assertThatThrownBy(() -> new Vigencia(enero1, junio30).cerrarEn(junio30.plusDays(30)))
                    .isInstanceOfSatisfying(CoberturaException.class,
                            e -> assertThat(e.codigo()).isEqualTo(CodigoCobertura.AFILIACION_CERRADA));
        }
    }

    @Nested
    class UnaAfiliacion {

        private Afiliacion afiliacion(Long id, long paciente, Vigencia vigencia) {
            return new Afiliacion(id, paciente, 10L, 1L, NumeroAfiliado.de("OSE-000001"), vigencia);
        }

        @Test
        @DisplayName("Se superpone con otra del mismo paciente que comparte un día")
        void seSuperponeConOtraDelMismoPaciente() {
            Afiliacion abierta = afiliacion(1L, 7, new Vigencia(LocalDate.of(2026, 1, 1), null));
            Afiliacion nueva = afiliacion(null, 7, new Vigencia(LocalDate.of(2026, 11, 1), null));
            assertThat(nueva.seSuperponeCon(abierta)).isTrue();
        }

        @Test
        @DisplayName("No se superpone con las de otro paciente")
        void noSeSuperponeConLasDeOtroPaciente() {
            Afiliacion abierta = afiliacion(1L, 7, new Vigencia(LocalDate.of(2026, 1, 1), null));
            Afiliacion deOtro = afiliacion(null, 8, new Vigencia(LocalDate.of(2026, 1, 1), null));
            assertThat(deOtro.seSuperponeCon(abierta)).isFalse();
        }

        @Test
        @DisplayName("No se superpone consigo misma al corregirla")
        void noSeSuperponeConsigoMisma() {
            Afiliacion actual = afiliacion(1L, 7, new Vigencia(LocalDate.of(2026, 1, 1), null));
            Afiliacion corregida = actual.corregida(11L, 1L, NumeroAfiliado.de("OSE-000002"),
                    new Vigencia(LocalDate.of(2026, 2, 1), null));
            assertThat(corregida.seSuperponeCon(actual)).isFalse();
        }

        @Test
        @DisplayName("Cerrarla conserva el resto de sus datos")
        void cerrar() {
            Afiliacion actual = afiliacion(1L, 7, new Vigencia(LocalDate.of(2026, 1, 1), null));
            Afiliacion cerrada = actual.cerradaEn(LocalDate.of(2026, 11, 30));
            assertThat(cerrada.vigencia()).isEqualTo(new Vigencia(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 30)));
            assertThat(cerrada.id()).isEqualTo(1L);
            assertThat(cerrada.numero()).isEqualTo(actual.numero());
        }
    }
}
