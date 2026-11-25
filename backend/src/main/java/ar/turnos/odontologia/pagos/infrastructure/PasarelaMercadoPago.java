package ar.turnos.odontologia.pagos.infrastructure;

import ar.turnos.odontologia.pagos.application.PasarelaNoDisponibleException;
import ar.turnos.odontologia.pagos.application.PasarelaPago;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adaptador de Mercado Pago Checkout Pro en sandbox ({@code PAGOS_PASARELA=mercadopago}, design D6), con
 * {@link RestClient} y sin SDK. La preferencia lleva un solo ítem genérico y la referencia opaca de la seña: nunca datos
 * del pagador ni la prestación (RN-SE-11). Los cuerpos de Mercado Pago no se loguean (design D10).
 */
class PasarelaMercadoPago implements PasarelaPago {

    static final String RUTA_NOTIFICACION = "/api/v1/public/pagos/mercadopago/notificaciones";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private final RestClient cliente;
    private final String urlPublica;
    private final String urlFrontend;

    PasarelaMercadoPago(RestClient.Builder builder, String urlApi, String accessToken, String urlPublica,
                        String urlFrontend) {
        this.cliente = builder.baseUrl(urlApi).defaultHeader("Authorization", "Bearer " + accessToken).build();
        this.urlPublica = urlPublica;
        this.urlFrontend = urlFrontend;
    }

    /** El arranque falla si se elige Mercado Pago sin credenciales (tarea 2.3). */
    static void verificarCredenciales(String accessToken, String webhookSecret) {
        if (accessToken == null || accessToken.isBlank() || webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("PAGOS_PASARELA=mercadopago necesita MERCADOPAGO_ACCESS_TOKEN y "
                    + "MERCADOPAGO_WEBHOOK_SECRET (de sandbox, en el .env local).");
        }
    }

    @Override
    public String crearLink(String referencia, BigDecimal importe, String descripcion, Instant vence) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", descripcion);
        item.put("quantity", 1);
        item.put("unit_price", importe);
        item.put("currency_id", "ARS");
        String misTurnos = urlFrontend + "/#/mis-turnos?pago=";
        Map<String, Object> preferencia = new LinkedHashMap<>();
        preferencia.put("items", List.of(item));
        preferencia.put("external_reference", referencia);
        preferencia.put("notification_url", urlPublica + RUTA_NOTIFICACION);
        preferencia.put("back_urls", Map.of("success", misTurnos + "aprobado", "pending", misTurnos + "pendiente",
                "failure", misTurnos + "rechazado"));
        preferencia.put("auto_return", "approved");
        preferencia.put("expires", true);
        preferencia.put("expiration_date_to", ISO.format(vence.atOffset(ZoneOffset.UTC)));
        try {
            JsonNode respuesta = cliente.post().uri("/checkout/preferences").contentType(MediaType.APPLICATION_JSON)
                    .body(preferencia).retrieve().body(JsonNode.class);
            String link = respuesta == null ? null : texto(respuesta, "init_point");
            if (link == null) {
                link = respuesta == null ? null : texto(respuesta, "sandbox_init_point");
            }
            if (link == null) {
                throw new PasarelaNoDisponibleException("Mercado Pago no devolvió el link de pago.");
            }
            return link;
        } catch (RestClientException e) {
            throw new PasarelaNoDisponibleException("Mercado Pago no creó el link de pago.", e);
        }
    }

    @Override
    public Optional<PagoExterno> consultarPago(String idPago) {
        try {
            JsonNode pago = cliente.get().uri("/v1/payments/{id}", idPago).retrieve().body(JsonNode.class);
            if (pago == null) {
                return Optional.empty();
            }
            BigDecimal importe = pago.hasNonNull("transaction_amount")
                    ? pago.get("transaction_amount").decimalValue() : BigDecimal.ZERO;
            return Optional.of(new PagoExterno(pago.path("id").asText(idPago), texto(pago, "status"),
                    texto(pago, "external_reference"), importe));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw new PasarelaNoDisponibleException("Mercado Pago no informó el pago.", e);
        } catch (RestClientException e) {
            throw new PasarelaNoDisponibleException("Mercado Pago no informó el pago.", e);
        }
    }

    @Override
    public void reintegrar(String idPago, BigDecimal importe, String claveIdempotencia) {
        try {
            cliente.post().uri("/v1/payments/{id}/refunds", idPago).header("X-Idempotency-Key", claveIdempotencia)
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of("amount", importe)).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw new PasarelaNoDisponibleException("Mercado Pago no hizo el reintegro.", e);
        }
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        return valor == null || valor.isNull() ? null : valor.asText();
    }
}
