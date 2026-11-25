package ar.turnos.odontologia.clinica.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Contenido canónico versión 1 y su SHA-256 (C-27, tarea 2.1, design D4). El vector de referencia está en
 * {@code src/test/resources/clinica/consentimiento-canonico-v1.txt} y su hash se calculó fuera de Java, con
 * {@code sha256sum}. Si este test falla, el formato cambió: un formato nuevo es el 2, nunca un cambio del 1.
 */
class ContenidoCanonicoTest {

    /** {@code sha256sum src/test/resources/clinica/consentimiento-canonico-v1.txt}. */
    private static final String SHA256_DE_REFERENCIA =
            "edcd3e6620f75dc849928d67d3c589dfd253c2605eb19731c58c573f41e348a3";

    /** El consentimiento ficticio fijo del vector de referencia. */
    static ContenidoCanonico deReferencia() {
        return new ContenidoCanonico(1L, 7L, "Extracción dental", "Texto ficticio de prueba.\nSegunda línea.",
                "Paciente Uno", "99000001", 3L, Instant.parse("2026-11-24T13:30:00.123456Z"), "10.0.0.15",
                "Navegador de prueba 1.0", "ELECTRONICA");
    }

    @Nested
    class HashDelDocumentoFirmado {

        @Test
        @DisplayName("El hash coincide con el contenido canónico")
        void elHashCoincideConElContenidoCanonico() throws Exception {
            byte[] referencia = vectorDeReferencia();

            assertThat(new String(deReferencia().bytes(), StandardCharsets.UTF_8))
                    .isEqualTo(new String(referencia, StandardCharsets.UTF_8));
            assertThat(deReferencia().bytes()).isEqualTo(referencia);
            assertThat(deReferencia().sha256()).isEqualTo(SHA256_DE_REFERENCIA);
            // El archivo y el hash de referencia corresponden entre sí.
            assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(referencia)))
                    .isEqualTo(SHA256_DE_REFERENCIA);
        }

        @Test
        @DisplayName("El mismo contenido da siempre el mismo hash")
        void mismoContenidoMismoHash() {
            assertThat(deReferencia().sha256()).isEqualTo(deReferencia().sha256()).matches("[0-9a-f]{64}");
        }

        @Test
        @DisplayName("Un cambio en el texto cambia el hash")
        void unCambioEnElTextoCambiaElHash() {
            ContenidoCanonico original = deReferencia();
            ContenidoCanonico otraLetra = new ContenidoCanonico(1L, 7L, "Extracción dental",
                    "Texto ficticio de prueba.\nSegunda linea.", "Paciente Uno", "99000001", 3L,
                    original.firmadoEn(), "10.0.0.15", "Navegador de prueba 1.0", "ELECTRONICA");

            assertThat(otraLetra.sha256()).isNotEqualTo(original.sha256());
        }

        @Test
        @DisplayName("Cualquier dato del documento cambia el hash")
        void cualquierDatoCambiaElHash() {
            ContenidoCanonico r = deReferencia();
            String hash = r.sha256();

            assertThat(new ContenidoCanonico(2L, 7L, r.titulo(), r.texto(), r.firmanteNombre(), r.firmanteDni(), 3L,
                    r.firmadoEn(), r.ipOrigen(), r.dispositivo(), r.tipoFirma()).sha256()).isNotEqualTo(hash);
            assertThat(new ContenidoCanonico(1L, 8L, r.titulo(), r.texto(), r.firmanteNombre(), r.firmanteDni(), 3L,
                    r.firmadoEn(), r.ipOrigen(), r.dispositivo(), r.tipoFirma()).sha256()).isNotEqualTo(hash);
            assertThat(new ContenidoCanonico(1L, 7L, r.titulo(), r.texto(), r.firmanteNombre(), r.firmanteDni(), 4L,
                    r.firmadoEn(), r.ipOrigen(), r.dispositivo(), r.tipoFirma()).sha256()).isNotEqualTo(hash);
            assertThat(new ContenidoCanonico(1L, 7L, r.titulo(), r.texto(), r.firmanteNombre(), r.firmanteDni(), 3L,
                    r.firmadoEn().plusNanos(1_000), r.ipOrigen(), r.dispositivo(), r.tipoFirma()).sha256())
                    .isNotEqualTo(hash);
            assertThat(new ContenidoCanonico(1L, 7L, r.titulo(), r.texto(), r.firmanteNombre(), r.firmanteDni(), 3L,
                    r.firmadoEn(), "10.0.0.16", r.dispositivo(), r.tipoFirma()).sha256()).isNotEqualTo(hash);
            assertThat(new ContenidoCanonico(1L, 7L, r.titulo(), r.texto(), r.firmanteNombre(), r.firmanteDni(), 3L,
                    r.firmadoEn(), r.ipOrigen(), "", r.tipoFirma()).sha256()).isNotEqualTo(hash);
        }

        @Test
        @DisplayName("Los campos no se pueden correr de lugar")
        void losCamposNoSePuedenCorrerDeLugar() {
            ContenidoCanonico r = deReferencia();
            // "Paciente Uno" + "99000001" y "Paciente Uno9" + "9000001" se concatenan igual.
            ContenidoCanonico corrido = new ContenidoCanonico(1L, 7L, r.titulo(), r.texto(), "Paciente Uno9",
                    "9000001", 3L, r.firmadoEn(), r.ipOrigen(), r.dispositivo(), r.tipoFirma());
            // Un salto de línea en el texto no puede imitar el comienzo del campo siguiente.
            ContenidoCanonico textoConCampo = new ContenidoCanonico(1L, 7L, r.titulo(),
                    r.texto() + "\nfirmante_nombre=12:Paciente Uno", "", r.firmanteDni(), 3L, r.firmadoEn(),
                    r.ipOrigen(), r.dispositivo(), r.tipoFirma());

            assertThat(corrido.sha256()).isNotEqualTo(r.sha256());
            assertThat(textoConCampo.sha256()).isNotEqualTo(r.sha256());
        }

        @Test
        @DisplayName("La longitud se cuenta en bytes UTF-8")
        void longitudEnBytes() {
            String contenido = new String(deReferencia().bytes(), StandardCharsets.UTF_8);

            assertThat(contenido).contains("\ntitulo=18:Extracción dental\n")
                    .contains("\ntexto=41:Texto ficticio de prueba.\nSegunda línea.\n")
                    .startsWith("consentimiento-informado/v1\npaciente_id=1:1\n")
                    .endsWith("\ntipo_firma=11:ELECTRONICA\n");
        }
    }

    /**
     * El archivo de referencia sin los {@code \r} que puede agregar Git en Windows ({@code core.autocrlf}): el formato
     * canónico solo tiene {@code \n}, porque el texto se normaliza al crear la versión (design D3).
     */
    private static byte[] vectorDeReferencia() throws IOException {
        try (InputStream entrada = ContenidoCanonicoTest.class.getResourceAsStream(
                "/clinica/consentimiento-canonico-v1.txt")) {
            assertThat(entrada).as("vector de referencia").isNotNull();
            String texto = new String(entrada.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            return texto.getBytes(StandardCharsets.UTF_8);
        }
    }
}
