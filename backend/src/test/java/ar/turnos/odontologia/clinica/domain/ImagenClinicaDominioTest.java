package ar.turnos.odontologia.clinica.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Reglas puras de las imágenes clínicas (C-26, tareas 2.1 y 2.2): RN-CL-07 y RN-CL-08. Los archivos son bytes
 * ficticios con la firma de cada tipo, generados acá: nunca imágenes reales.
 */
class ImagenClinicaDominioTest {

    static final LocalDate HOY = LocalDate.of(2026, 12, 1);

    static byte[] jpeg(int tamano) {
        return conFirma(tamano, 0xFF, 0xD8, 0xFF, 0xE0);
    }

    static byte[] png(int tamano) {
        return conFirma(tamano, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
    }

    static byte[] pdf(int tamano) {
        return conFirma(tamano, 0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37);
    }

    static byte[] conFirma(int tamano, int... firma) {
        byte[] contenido = new byte[tamano];
        Arrays.fill(contenido, (byte) 0x20);
        for (int i = 0; i < firma.length && i < tamano; i++) {
            contenido[i] = (byte) firma[i];
        }
        return contenido;
    }

    static ImagenClinica nueva(String categoria, Integer pieza, LocalDate tomadaEl, String descripcion, byte[] archivo) {
        return ImagenClinica.nueva(categoria, pieza, tomadaEl, descripcion, archivo, HOY);
    }

    static void assertCodigo(ThrowingCallable accion, CodigoClinica codigo) {
        assertThatThrownBy(accion).isInstanceOfSatisfying(ClinicaException.class,
                e -> assertThat(e.codigo()).isEqualTo(codigo));
    }

    @Nested
    class TiposDeArchivoPermitidos {

        @Test
        @DisplayName("Reconoce JPEG, PNG y PDF por su firma")
        void reconoceLosTiposPorSuFirma() {
            assertThat(TipoArchivoClinico.detectar(jpeg(10))).isEqualTo(TipoArchivoClinico.JPEG);
            assertThat(TipoArchivoClinico.detectar(png(10))).isEqualTo(TipoArchivoClinico.PNG);
            assertThat(TipoArchivoClinico.detectar(pdf(10))).isEqualTo(TipoArchivoClinico.PDF);
            assertThat(TipoArchivoClinico.PDF.tipoContenido()).isEqualTo("application/pdf");
        }

        @Test
        @DisplayName("Archivo de un tipo no permitido")
        void archivoDeUnTipoNoPermitido() {
            byte[] gif = "GIF89a ficticio".getBytes(StandardCharsets.US_ASCII);
            assertCodigo(() -> nueva("FOTOGRAFIA", null, HOY, null, gif), CodigoClinica.TIPO_ARCHIVO_NO_PERMITIDO);
        }

        @Test
        @DisplayName("Extensión engañosa")
        void extensionEnganosa() {
            byte[] texto = "texto plano que dice ser una radiografía".getBytes(StandardCharsets.UTF_8);
            assertCodigo(() -> TipoArchivoClinico.detectar(texto), CodigoClinica.TIPO_ARCHIVO_NO_PERMITIDO);
        }

        @Test
        @DisplayName("Un archivo más corto que cualquier firma no se reconoce")
        void archivoMasCortoQueLaFirma() {
            assertCodigo(() -> TipoArchivoClinico.detectar(new byte[] {(byte) 0xFF, (byte) 0xD8}),
                    CodigoClinica.TIPO_ARCHIVO_NO_PERMITIDO);
        }

        @Test
        @DisplayName("El tipo guardado es el del contenido")
        void elTipoGuardadoEsElDelContenido() {
            // El nombre "foto.jpg" y el tipo declarado no llegan al dominio: solo cuentan los bytes.
            assertThat(nueva("FOTOGRAFIA", null, HOY, null, png(64)).tipo()).isEqualTo(TipoArchivoClinico.PNG);
        }

        @Test
        @DisplayName("Archivo vacío")
        void archivoVacio() {
            assertCodigo(() -> nueva("RADIOGRAFIA", null, HOY, null, new byte[0]), CodigoClinica.DATOS_INVALIDOS);
            assertCodigo(() -> nueva("RADIOGRAFIA", null, HOY, null, null), CodigoClinica.DATOS_INVALIDOS);
        }
    }

    @Nested
    class TamanoMaximoDelArchivo {

        @Test
        @DisplayName("Archivo de más de 10 MB")
        void archivoDeMasDe10Mb() {
            assertCodigo(() -> nueva("RADIOGRAFIA", 36, HOY, null, jpeg(10_485_761)),
                    CodigoClinica.ARCHIVO_DEMASIADO_GRANDE);
        }

        @Test
        @DisplayName("Archivo de exactamente 10 MB")
        void archivoDeExactamente10Mb() {
            assertThat(nueva("RADIOGRAFIA", 36, HOY, null, jpeg(10_485_760)).tamanoBytes()).isEqualTo(10_485_760);
        }
    }

    @Nested
    class FechaDeLaTomaYPieza {

        @Test
        @DisplayName("Fecha de la toma futura")
        void fechaDeLaTomaFutura() {
            assertCodigo(() -> nueva("RADIOGRAFIA", null, HOY.plusDays(1), null, jpeg(10)),
                    CodigoClinica.DATOS_INVALIDOS);
        }

        @Test
        @DisplayName("Fecha de la toma faltante")
        void fechaDeLaTomaFaltante() {
            assertCodigo(() -> nueva("RADIOGRAFIA", null, null, null, jpeg(10)), CodigoClinica.DATOS_INVALIDOS);
        }

        @Test
        @DisplayName("Fecha de la toma de hoy")
        void fechaDeLaTomaDeHoy() {
            assertThat(nueva("ESTUDIO", null, HOY, null, pdf(10)).tomadaEl()).isEqualTo(HOY);
        }

        @Test
        @DisplayName("Pieza temporal válida")
        void piezaTemporalValida() {
            assertThat(nueva("FOTOGRAFIA", 55, HOY, null, png(10)).pieza()).isEqualTo(55);
        }

        @Test
        @DisplayName("Pieza inexistente")
        void piezaInexistente() {
            assertThatThrownBy(() -> nueva("RADIOGRAFIA", 19, HOY, null, jpeg(10)))
                    .isInstanceOfSatisfying(ClinicaException.class, e -> {
                        assertThat(e.codigo()).isEqualTo(CodigoClinica.PIEZA_INVALIDA);
                        assertThat(e.getMessage()).doesNotContain("19");
                    });
        }
    }

    @Nested
    class CargarUnaImagenClinica {

        @Test
        @DisplayName("Categoría inexistente")
        void categoriaInexistente() {
            assertCodigo(() -> nueva("TOMOGRAFIA", null, HOY, null, png(10)), CodigoClinica.DATOS_INVALIDOS);
            assertCodigo(() -> nueva(null, null, HOY, null, png(10)), CodigoClinica.DATOS_INVALIDOS);
        }

        @Test
        @DisplayName("Descripción demasiado larga")
        void descripcionDemasiadoLarga() {
            String larga = "Lesión" + "x".repeat(195);
            assertThatThrownBy(() -> nueva("FOTOGRAFIA", null, HOY, larga, png(10)))
                    .isInstanceOfSatisfying(ClinicaException.class, e -> {
                        assertThat(e.codigo()).isEqualTo(CodigoClinica.DATOS_INVALIDOS);
                        assertThat(e.getMessage()).doesNotContain("Lesión");
                    });
        }

        @Test
        @DisplayName("Descripción de 200 caracteres y descripción en blanco")
        void descripcionLimites() {
            assertThat(nueva("FOTOGRAFIA", null, HOY, "x".repeat(200), png(10)).descripcion()).hasSize(200);
            assertThat(nueva("FOTOGRAFIA", null, HOY, "   ", png(10)).descripcion()).isNull();
        }
    }

    @Nested
    class AnularUnaImagenConMotivo {

        @Test
        @DisplayName("Anular sin motivo")
        void anularSinMotivo() {
            assertCodigo(() -> ImagenClinica.motivoDeAnulacion(""), CodigoClinica.DATOS_INVALIDOS);
            assertCodigo(() -> ImagenClinica.motivoDeAnulacion("   "), CodigoClinica.DATOS_INVALIDOS);
            assertCodigo(() -> ImagenClinica.motivoDeAnulacion(null), CodigoClinica.DATOS_INVALIDOS);
        }

        @Test
        @DisplayName("El motivo tiene de 1 a 200 caracteres")
        void motivoLimites() {
            assertThat(ImagenClinica.motivoDeAnulacion(" Se cargó en el paciente equivocado "))
                    .isEqualTo("Se cargó en el paciente equivocado");
            assertThat(ImagenClinica.motivoDeAnulacion("x".repeat(200))).hasSize(200);
            assertCodigo(() -> ImagenClinica.motivoDeAnulacion("x".repeat(201)), CodigoClinica.DATOS_INVALIDOS);
        }
    }
}
