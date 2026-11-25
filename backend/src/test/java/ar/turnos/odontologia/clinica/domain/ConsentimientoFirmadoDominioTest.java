package ar.turnos.odontologia.clinica.domain;

import static ar.turnos.odontologia.clinica.domain.PlantillaConsentimientoDominioTest.assertCodigo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnos.odontologia.clinica.domain.ConsentimientoFirmado.Firmante;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Firma electrónica en el consultorio, su evidencia y la revocación (C-27, tarea 2.2, design D3, D4 y D7). Datos
 * ficticios: "Paciente Uno", DNI 99000001, IP 10.0.0.15 y "Navegador de prueba 1.0".
 */
class ConsentimientoFirmadoDominioTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String DNI = "99000001";
    private static final VersionPlantilla EXTRACCION =
            VersionPlantilla.nueva("Extracción dental", "Texto ficticio de prueba.\nSegunda línea.");
    private static final EvidenciaFirma EVIDENCIA = EvidenciaFirma.de("10.0.0.15", "Navegador de prueba 1.0");
    private static final Instant DIEZ_Y_MEDIA = LocalDateTime.of(2026, 11, 24, 10, 30).atZone(BUENOS_AIRES).toInstant();

    @Nested
    class FirmarUnConsentimientoEnElConsultorio {

        @Test
        @DisplayName("Firmar un consentimiento")
        void firmarUnConsentimiento() {
            ConsentimientoFirmado firmado = firmar(Firmante.de("Paciente Uno", DNI, true, DNI), relojFijo(DIEZ_Y_MEDIA));

            assertThat(firmado.titulo()).isEqualTo("Extracción dental");
            assertThat(firmado.texto()).isEqualTo(EXTRACCION.texto());
            assertThat(firmado.firmanteNombre()).isEqualTo("Paciente Uno");
            assertThat(firmado.firmanteDni()).isEqualTo(DNI);
            assertThat(firmado.profesionalId()).isEqualTo(3L);
            assertThat(firmado.ipOrigen()).isEqualTo("10.0.0.15");
            assertThat(firmado.dispositivo()).isEqualTo("Navegador de prueba 1.0");
            assertThat(firmado.hash()).isEqualTo(firmado.contenidoCanonico().sha256()).matches("[0-9a-f]{64}");
            assertThat(firmado.contenidoCanonico().tipoFirma()).isEqualTo("ELECTRONICA");
        }

        @Test
        @DisplayName("Sin aceptación")
        void sinAceptacion() {
            assertCodigo(() -> Firmante.de("Paciente Uno", DNI, false, DNI), CodigoClinica.DATOS_INVALIDOS);
            assertCodigo(() -> Firmante.de("Paciente Uno", DNI, null, DNI), CodigoClinica.DATOS_INVALIDOS);
        }

        @Test
        @DisplayName("Nombre del firmante vacío")
        void nombreDelFirmanteVacio() {
            assertCodigo(() -> Firmante.de("   ", DNI, true, DNI), CodigoClinica.DATOS_INVALIDOS);
            assertCodigo(() -> Firmante.de(null, DNI, true, DNI), CodigoClinica.DATOS_INVALIDOS);
            assertCodigo(() -> Firmante.de("Al", DNI, true, DNI), CodigoClinica.DATOS_INVALIDOS);
            assertCodigo(() -> Firmante.de("x".repeat(201), DNI, true, DNI), CodigoClinica.DATOS_INVALIDOS);
        }

        @Test
        @DisplayName("El nombre se recorta, colapsa los espacios y queda en NFC")
        void nombreNormalizado() {
            String nfd = "Paciente  Uno Peri" + "n" + (char) 0x0303 + "a";

            Firmante firmante = Firmante.de("  " + nfd + " \t", DNI, true, DNI);

            assertThat(firmante.nombre()).isEqualTo("Paciente Uno Peri" + (char) 0x00F1 + "a");
            assertThat(Firmante.de("Ana", DNI, true, DNI).nombre()).isEqualTo("Ana");
            assertThat(Firmante.de("x".repeat(200), DNI, true, DNI).nombre()).hasSize(200);
        }

        @Test
        @DisplayName("DNI distinto del paciente")
        void dniDistintoDelPaciente() {
            assertCodigo(() -> Firmante.de("Paciente Uno", "99000002", true, DNI), CodigoClinica.FIRMANTE_NO_COINCIDE);
            assertCodigo(() -> Firmante.de("Paciente Uno", "9900000", true, DNI), CodigoClinica.FIRMANTE_NO_COINCIDE);
        }

        @Test
        @DisplayName("Un error no repite el DNI escrito")
        void unErrorNoRepiteElDniEscrito() {
            assertThatThrownBy(() -> Firmante.de("Paciente Uno", "99000002", true, DNI))
                    .isInstanceOf(ClinicaException.class)
                    .message().doesNotContain("99000002").doesNotContain(DNI).doesNotContain("Paciente Uno");
        }

        @Test
        @DisplayName("DNI con puntos")
        void dniConPuntos() {
            assertThat(Firmante.de("Paciente Uno", "99.000.001", true, DNI).dni()).isEqualTo(DNI);
            assertThat(Firmante.de("Paciente Uno", " 99 000 001 ", true, DNI).dni()).isEqualTo(DNI);
            // El DNI del paciente se normaliza igual.
            assertThat(Firmante.de("Paciente Uno", DNI, true, "99.000.001").dni()).isEqualTo(DNI);
            assertThat(Firmante.de("Paciente Uno", "1.234.567", true, "1234567").dni()).isEqualTo("1234567");
        }

        @Test
        @DisplayName("DNI con letras o de largo inválido")
        void dniInvalido() {
            assertCodigo(() -> Firmante.de("Paciente Uno", "99A00001", true, DNI), CodigoClinica.DATOS_INVALIDOS);
            assertCodigo(() -> Firmante.de("Paciente Uno", "123456", true, DNI), CodigoClinica.DATOS_INVALIDOS);
            assertCodigo(() -> Firmante.de("Paciente Uno", "990000011", true, DNI), CodigoClinica.DATOS_INVALIDOS);
            assertCodigo(() -> Firmante.de("Paciente Uno", null, true, DNI), CodigoClinica.DATOS_INVALIDOS);
        }
    }

    @Nested
    class EvidenciaDeLaFirma {

        @Test
        @DisplayName("Se guardan la IP y el dispositivo")
        void seGuardanLaIpYElDispositivo() {
            assertThat(EVIDENCIA.ip()).isEqualTo("10.0.0.15");
            assertThat(EVIDENCIA.dispositivo()).isEqualTo("Navegador de prueba 1.0");
            // IPv6 literal, tal como lo entrega el contenedor.
            assertThat(EvidenciaFirma.de("0:0:0:0:0:0:0:1", "x").ip()).isEqualTo("0:0:0:0:0:0:0:1");
            assertThat(EvidenciaFirma.de("::1", "x").ip()).isEqualTo("::1");
        }

        @Test
        @DisplayName("La IP tiene que ser una dirección literal")
        void ipLiteral() {
            assertThatThrownBy(() -> EvidenciaFirma.de("consultorio.example.com", "x"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .message().doesNotContain("consultorio");
            assertThatThrownBy(() -> EvidenciaFirma.de("10.0.0.256", "x")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> EvidenciaFirma.de("1:2:3", "x")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> EvidenciaFirma.de(null, "x")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("User-Agent demasiado largo")
        void userAgentDemasiadoLargo() {
            String largo = "N".repeat(512) + "Z".repeat(88);

            EvidenciaFirma evidencia = EvidenciaFirma.de("10.0.0.15", largo);

            assertThat(evidencia.dispositivo()).isEqualTo("N".repeat(512));
        }

        @Test
        @DisplayName("El recorte no parte un carácter de dos unidades UTF-16")
        void recorteSinPartirCaracteres() {
            String emoji = new String(Character.toChars(0x1F9B7));

            String dispositivo = EvidenciaFirma.de("10.0.0.15", emoji.repeat(600)).dispositivo();

            assertThat(dispositivo.codePointCount(0, dispositivo.length())).isEqualTo(512);
            assertThat(dispositivo).isEqualTo(emoji.repeat(512));
        }

        @Test
        @DisplayName("Sin User-Agent")
        void sinUserAgent() {
            assertThat(EvidenciaFirma.de("10.0.0.15", null).dispositivo()).isEmpty();
            assertThat(EvidenciaFirma.de("10.0.0.15", "").dispositivo()).isEmpty();
        }

        @Test
        @DisplayName("El dispositivo no guarda caracteres de control")
        void sinCaracteresDeControl() {
            assertThat(EvidenciaFirma.de("10.0.0.15", "Navegador\r\nde\tprueba\u0000 1.0").dispositivo())
                    .isEqualTo("Navegadordeprueba 1.0");
        }

        @Test
        @DisplayName("La fecha es la del servidor")
        void laFechaEsLaDelServidor() {
            ConsentimientoFirmado firmado = firmar(Firmante.de("Paciente Uno", DNI, true, DNI), relojFijo(DIEZ_Y_MEDIA));

            assertThat(firmado.firmadoEn()).isEqualTo(DIEZ_Y_MEDIA);
            assertThat(firmado.firmadoEn().atZone(BUENOS_AIRES).toLocalDateTime())
                    .isEqualTo(LocalDateTime.of(2026, 11, 24, 10, 30));
        }

        @Test
        @DisplayName("El instante se trunca a microsegundos")
        void truncadoAMicrosegundos() {
            Instant conNanos = DIEZ_Y_MEDIA.plusNanos(123_456_789);

            ConsentimientoFirmado firmado = firmar(Firmante.de("Paciente Uno", DNI, true, DNI), relojFijo(conNanos));

            assertThat(firmado.firmadoEn()).isEqualTo(DIEZ_Y_MEDIA.plusNanos(123_456_000));
        }
    }

    @Nested
    class RevocarUnConsentimientoConRegistro {

        @Test
        @DisplayName("Revocar sin motivo")
        void revocarSinMotivo() {
            assertCodigo(() -> Revocacion.motivo(""), CodigoClinica.DATOS_INVALIDOS);
            assertCodigo(() -> Revocacion.motivo("   "), CodigoClinica.DATOS_INVALIDOS);
            assertCodigo(() -> Revocacion.motivo(null), CodigoClinica.DATOS_INVALIDOS);
            assertCodigo(() -> Revocacion.motivo("x".repeat(201)), CodigoClinica.DATOS_INVALIDOS);
        }

        @Test
        @DisplayName("Revocar un consentimiento")
        void revocarUnConsentimiento() {
            assertThat(Revocacion.motivo("  El paciente decidió no hacer el tratamiento "))
                    .isEqualTo("El paciente decidió no hacer el tratamiento");
            assertThat(Revocacion.motivo("x".repeat(200))).hasSize(200);
        }

        @Test
        @DisplayName("El mensaje de error no repite el motivo")
        void mensajeSinMotivo() {
            assertThatThrownBy(() -> Revocacion.motivo("Motivo ficticio" + "x".repeat(200)))
                    .message().doesNotContain("Motivo ficticio");
        }
    }

    private static ConsentimientoFirmado firmar(Firmante firmante, Clock reloj) {
        return ConsentimientoFirmado.firmar(1L, firmante, 7L, EXTRACCION, 3L, EVIDENCIA, reloj);
    }

    private static Clock relojFijo(Instant instante) {
        return Clock.fixed(instante, BUENOS_AIRES);
    }
}
