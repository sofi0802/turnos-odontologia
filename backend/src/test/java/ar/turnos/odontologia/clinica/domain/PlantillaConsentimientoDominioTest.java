package ar.turnos.odontologia.clinica.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Plantillas de consentimiento y su normalización (C-27, tarea 1.2, design D3). Textos ficticios. */
class PlantillaConsentimientoDominioTest {

    private static final String TITULO = "Extracción dental";
    private static final String TEXTO = "Texto ficticio de la plantilla.\nSegunda línea.";
    /** Un emoji: dos unidades UTF-16, un solo carácter para PostgreSQL. */
    private static final String EMOJI = new String(Character.toChars(0x1F9B7));
    /** El acento agudo combinado (NFD). */
    private static final String ACENTO = String.valueOf((char) 0x0301);

    @Nested
    class CrearUnaPlantillaDeConsentimiento {

        @Test
        @DisplayName("Texto demasiado largo")
        void textoDemasiadoLargo() {
            assertCodigo(() -> VersionPlantilla.nueva(TITULO, "x".repeat(20_001)), CodigoClinica.DATOS_INVALIDOS);
        }

        @Test
        @DisplayName("Los límites de largo se aceptan justos")
        void limitesJustos() {
            VersionPlantilla version = VersionPlantilla.nueva("t".repeat(120), "x".repeat(20_000));

            assertThat(version.titulo()).hasSize(120);
            assertThat(version.texto()).hasSize(20_000);
            assertCodigo(() -> VersionPlantilla.nueva("t".repeat(121), TEXTO), CodigoClinica.DATOS_INVALIDOS);
        }

        @Test
        @DisplayName("El largo se cuenta en caracteres, no en unidades UTF-16")
        void largoEnCaracteres() {
            String titulo = EMOJI.repeat(120);

            assertThat(VersionPlantilla.nueva(titulo, TEXTO).titulo()).isEqualTo(titulo);
            assertCodigo(() -> VersionPlantilla.nueva(EMOJI.repeat(121), TEXTO), CodigoClinica.DATOS_INVALIDOS);
        }

        @Test
        @DisplayName("Título vacío")
        void tituloVacio() {
            assertCodigo(() -> VersionPlantilla.nueva("   ", TEXTO), CodigoClinica.DATOS_INVALIDOS);
            assertCodigo(() -> VersionPlantilla.nueva(null, TEXTO), CodigoClinica.DATOS_INVALIDOS);
        }

        @Test
        @DisplayName("Texto vacío o ausente")
        void textoVacio() {
            assertCodigo(() -> VersionPlantilla.nueva(TITULO, " \r\n "), CodigoClinica.DATOS_INVALIDOS);
            assertCodigo(() -> VersionPlantilla.nueva(TITULO, null), CodigoClinica.DATOS_INVALIDOS);
        }

        @Test
        @DisplayName("El mensaje de error no repite el texto ni el título")
        void mensajeSinDatos() {
            assertThatThrownBy(() -> VersionPlantilla.nueva("Título ficticio", "Contenido" + "x".repeat(20_000)))
                    .isInstanceOf(ClinicaException.class)
                    .message().doesNotContain("Título ficticio").doesNotContain("Contenido");
        }
    }

    @Nested
    class Normalizacion {

        @Test
        @DisplayName("Los saltos de línea CR LF y CR pasan a LF")
        void saltosDeLinea() {
            VersionPlantilla version = VersionPlantilla.nueva(TITULO, "Uno\r\nDos\rTres\nCuatro");

            assertThat(version.texto()).isEqualTo("Uno\nDos\nTres\nCuatro");
        }

        @Test
        @DisplayName("El título y el texto se normalizan a NFC")
        void nfc() {
            String nfd = "Extracci" + "o" + ACENTO + "n dental";

            VersionPlantilla version = VersionPlantilla.nueva(nfd, "Informaci" + "o" + ACENTO + "n");

            assertThat(nfd).isNotEqualTo(TITULO);
            assertThat(version.titulo()).isEqualTo(TITULO);
            assertThat(version.texto()).isEqualTo("Información");
        }

        @Test
        @DisplayName("Se recortan los espacios de los extremos, no los internos")
        void recorte() {
            VersionPlantilla version = VersionPlantilla.nueva("  Extracción dental \t", "\n  Línea uno\n\nLínea dos  \n");

            assertThat(version.titulo()).isEqualTo(TITULO);
            assertThat(version.texto()).isEqualTo("Línea uno\n\nLínea dos");
        }
    }

    @Nested
    class EditarUnaPlantillaCreaUnaVersionNueva {

        private final PlantillaConsentimiento plantilla =
                new PlantillaConsentimiento(1L, true, 1, VersionPlantilla.nueva(TITULO, TEXTO));

        @Test
        @DisplayName("Edición sin cambios")
        void edicionSinCambios() {
            assertThat(plantilla.editar(1, TITULO, TEXTO)).isEmpty();
            // Con espacios o saltos CR LF que se normalizan al mismo contenido tampoco hay versión nueva.
            assertThat(plantilla.editar(1, " " + TITULO, TEXTO.replace("\n", "\r\n"))).isEmpty();
        }

        @Test
        @DisplayName("Editar una plantilla")
        void editarUnaPlantilla() {
            assertThat(plantilla.editar(1, TITULO, "Otro texto ficticio")).hasValueSatisfying(version -> {
                assertThat(version.titulo()).isEqualTo(TITULO);
                assertThat(version.texto()).isEqualTo("Otro texto ficticio");
            });
            assertThat(plantilla.editar(1, "Extracción de muela", TEXTO)).isPresent();
        }

        @Test
        @DisplayName("Edición sobre una versión vieja")
        void edicionSobreUnaVersionVieja() {
            PlantillaConsentimiento enVersion2 = new PlantillaConsentimiento(1L, true, 2,
                    VersionPlantilla.nueva(TITULO, TEXTO));

            assertCodigo(() -> enVersion2.editar(1, TITULO, "Otro"), CodigoClinica.VERSION_DESACTUALIZADA);
            assertCodigo(() -> enVersion2.editar(3, TITULO, "Otro"), CodigoClinica.VERSION_DESACTUALIZADA);
        }

        @Test
        @DisplayName("Sin la versión base")
        void sinVersionBase() {
            assertCodigo(() -> plantilla.editar(null, TITULO, "Otro"), CodigoClinica.DATOS_INVALIDOS);
        }

        @Test
        @DisplayName("Una edición con datos inválidos responde DATOS_INVALIDOS")
        void edicionInvalida() {
            assertCodigo(() -> plantilla.editar(1, "", TEXTO), CodigoClinica.DATOS_INVALIDOS);
            assertCodigo(() -> plantilla.editar(1, TITULO, "x".repeat(20_001)), CodigoClinica.DATOS_INVALIDOS);
        }
    }

    @Nested
    class FirmarSobreUnaVersion {

        @Test
        @DisplayName("Plantilla inactiva")
        void plantillaInactiva() {
            PlantillaConsentimiento inactiva = new PlantillaConsentimiento(1L, false, 1,
                    VersionPlantilla.nueva("Blanqueamiento", TEXTO));

            assertCodigo(() -> inactiva.exigirFirmable(1), CodigoClinica.PLANTILLA_INACTIVA);
        }

        @Test
        @DisplayName("Versión desactualizada")
        void versionDesactualizada() {
            PlantillaConsentimiento enVersion2 = new PlantillaConsentimiento(1L, true, 2,
                    VersionPlantilla.nueva(TITULO, TEXTO));

            assertCodigo(() -> enVersion2.exigirFirmable(1), CodigoClinica.VERSION_DESACTUALIZADA);
            assertCodigo(() -> enVersion2.exigirFirmable(null), CodigoClinica.DATOS_INVALIDOS);
            enVersion2.exigirFirmable(2);
        }
    }

    static void assertCodigo(ThrowingCallable accion, CodigoClinica codigo) {
        assertThatThrownBy(accion).isInstanceOf(ClinicaException.class)
                .extracting(e -> ((ClinicaException) e).codigo()).isEqualTo(codigo);
    }
}
