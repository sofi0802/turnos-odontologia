package ar.turnos.odontologia.pagos.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Firma de las notificaciones de Mercado Pago (RN-SE-05, design D5): {@code x-signature} con {@code ts} y {@code v1},
 * HMAC-SHA256 en hexadecimal del manifiesto {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;}.
 */
class FirmaMercadoPagoTest {

    private static final String CLAVE = "clave-de-prueba-no-secreta";

    @Test
    @DisplayName("el manifiesto sigue el formato de Mercado Pago")
    void manifiesto() {
        assertThat(FirmaMercadoPago.manifiesto("987", "req-1", "1700000000"))
                .isEqualTo("id:987;request-id:req-1;ts:1700000000;");
    }

    @Test
    @DisplayName("un dato que no vino se omite del manifiesto")
    void manifiestoSinRequestId() {
        assertThat(FirmaMercadoPago.manifiesto("987", null, "1700000000")).isEqualTo("id:987;ts:1700000000;");
    }

    @Test
    @DisplayName("una firma calculada con la clave es válida")
    void firmaValida() {
        String firma = FirmaMercadoPago.firmar("987", "req-1", "1700000000", CLAVE);
        assertThat(FirmaMercadoPago.valida(firma, "req-1", "987", CLAVE)).isTrue();
    }

    @Test
    @DisplayName("el encabezado admite espacios entre las partes")
    void conEspacios() {
        String firma = FirmaMercadoPago.firmar("987", "req-1", "1700000000", CLAVE).replace(",", ", ");
        assertThat(FirmaMercadoPago.valida(firma, "req-1", "987", CLAVE)).isTrue();
    }

    @Test
    @DisplayName("una firma alterada no es válida")
    void firmaAlterada() {
        String firma = FirmaMercadoPago.firmar("987", "req-1", "1700000000", CLAVE);
        String alterada = firma.substring(0, firma.length() - 1) + (firma.endsWith("0") ? "1" : "0");
        assertThat(FirmaMercadoPago.valida(alterada, "req-1", "987", CLAVE)).isFalse();
    }

    @Test
    @DisplayName("la firma de otro pago no vale para este")
    void otroPago() {
        String firma = FirmaMercadoPago.firmar("987", "req-1", "1700000000", CLAVE);
        assertThat(FirmaMercadoPago.valida(firma, "req-1", "988", CLAVE)).isFalse();
    }

    @Test
    @DisplayName("una firma hecha con otra clave no es válida")
    void otraClave() {
        String firma = FirmaMercadoPago.firmar("987", "req-1", "1700000000", "otra-clave");
        assertThat(FirmaMercadoPago.valida(firma, "req-1", "987", CLAVE)).isFalse();
    }

    @Test
    @DisplayName("sin encabezado, sin ts, sin v1 o sin clave no es válida")
    void vacia() {
        assertThat(FirmaMercadoPago.valida(null, "req-1", "987", CLAVE)).isFalse();
        assertThat(FirmaMercadoPago.valida("", "req-1", "987", CLAVE)).isFalse();
        assertThat(FirmaMercadoPago.valida("v1=abc", "req-1", "987", CLAVE)).isFalse();
        assertThat(FirmaMercadoPago.valida("ts=1700000000", "req-1", "987", CLAVE)).isFalse();
        String firma = FirmaMercadoPago.firmar("987", "req-1", "1700000000", CLAVE);
        assertThat(FirmaMercadoPago.valida(firma, "req-1", "987", "")).isFalse();
    }
}
