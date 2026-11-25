package ar.turnos.odontologia.pagos.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ar.turnos.odontologia.pagos.application.PasarelaNoDisponibleException;
import java.math.BigDecimal;
import java.time.Instant;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Adaptador de Mercado Pago contra un servidor HTTP falso: nunca contra Mercado Pago (design D6, D12). */
class PasarelaMercadoPagoTest {

    private static final String API = "https://api.mercadopago.test";
    private MockRestServiceServer servidor;
    private PasarelaMercadoPago pasarela;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        pasarela = new PasarelaMercadoPago(builder, API, "token-de-prueba", "https://backend.test",
                "https://frontend.test");
    }

    @Test
    @DisplayName("el link se pide con la referencia, el importe y una descripción genérica, sin datos del pagador")
    void crearLink() {
        servidor.expect(requestTo(API + "/checkout/preferences"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-de-prueba"))
                .andExpect(jsonPath("$.items[0].title").value("Seña de turno"))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.items[0].unit_price").value(4500.00))
                .andExpect(jsonPath("$.items[0].currency_id").value("ARS"))
                .andExpect(jsonPath("$.external_reference").value("S-ABC123"))
                .andExpect(jsonPath("$.notification_url")
                        .value("https://backend.test/api/v1/public/pagos/mercadopago/notificaciones"))
                .andExpect(jsonPath("$.back_urls.success").value("https://frontend.test/#/mis-turnos?pago=aprobado"))
                .andExpect(jsonPath("$.expires").value(true))
                .andExpect(jsonPath("$.payer").doesNotExist())
                .andExpect(content().string(Matchers.not(Matchers.containsString("Paciente"))))
                .andRespond(withSuccess("{\"id\":\"pref-1\",\"init_point\":\"https://mp.test/pagar/pref-1\"}",
                        MediaType.APPLICATION_JSON));

        String link = pasarela.crearLink("S-ABC123", new BigDecimal("4500.00"), "Seña de turno",
                Instant.parse("2026-12-07T13:30:00Z"));

        assertThat(link).isEqualTo("https://mp.test/pagar/pref-1");
        servidor.verify();
    }

    @Test
    @DisplayName("un error de Mercado Pago al pedir el link es pasarela no disponible")
    void crearLinkConError() {
        servidor.expect(requestTo(API + "/checkout/preferences")).andRespond(withServerError());
        assertThatThrownBy(() -> pasarela.crearLink("S-1", BigDecimal.TEN, "Seña de turno", Instant.EPOCH))
                .isInstanceOf(PasarelaNoDisponibleException.class);
    }

    @Test
    @DisplayName("la consulta del pago trae el estado, la referencia y el importe")
    void consultarPago() {
        servidor.expect(requestTo(API + "/v1/payments/987"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-de-prueba"))
                .andRespond(withSuccess("{\"id\":987,\"status\":\"approved\",\"external_reference\":\"S-ABC123\","
                        + "\"transaction_amount\":4500.0}", MediaType.APPLICATION_JSON));

        var pago = pasarela.consultarPago("987");

        assertThat(pago).hasValueSatisfying(p -> {
            assertThat(p.id()).isEqualTo("987");
            assertThat(p.aprobado()).isTrue();
            assertThat(p.referencia()).isEqualTo("S-ABC123");
            assertThat(p.importe()).isEqualByComparingTo("4500.00");
        });
    }

    @Test
    @DisplayName("un pago que Mercado Pago no conoce es vacío")
    void pagoInexistente() {
        servidor.expect(requestTo(API + "/v1/payments/1")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThat(pasarela.consultarPago("1")).isEmpty();
    }

    @Test
    @DisplayName("el reintegro manda el importe y la clave de idempotencia")
    void reintegrar() {
        servidor.expect(requestTo(API + "/v1/payments/987/refunds"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Idempotency-Key", "sena-12-CANCELACION"))
                .andExpect(jsonPath("$.amount").value(1500.00))
                .andRespond(withSuccess("{\"id\":55,\"status\":\"approved\"}", MediaType.APPLICATION_JSON));

        pasarela.reintegrar("987", new BigDecimal("1500.00"), "sena-12-CANCELACION");

        servidor.verify();
    }

    @Test
    @DisplayName("un reintegro rechazado es pasarela no disponible")
    void reintegroRechazado() {
        servidor.expect(requestTo(API + "/v1/payments/987/refunds")).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> pasarela.reintegrar("987", BigDecimal.ONE, "k"))
                .isInstanceOf(PasarelaNoDisponibleException.class);
    }

    @Test
    @DisplayName("sin token de Mercado Pago el arranque falla")
    void sinCredenciales() {
        assertThatThrownBy(() -> PasarelaMercadoPago.verificarCredenciales("", "secreto"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PasarelaMercadoPago.verificarCredenciales("token", " "))
                .isInstanceOf(IllegalStateException.class);
    }
}
