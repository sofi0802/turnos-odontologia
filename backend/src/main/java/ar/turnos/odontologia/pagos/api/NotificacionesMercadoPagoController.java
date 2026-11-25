package ar.turnos.odontologia.pagos.api;

import ar.turnos.odontologia.pagos.application.NotificacionDePago;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notificación de Mercado Pago (RN-SE-05, design D5). Es pública y la única ruta fuera de CSRF (PA-64, design D7): su
 * autenticidad la deciden la firma y la consulta del pago. El cuerpo no se loguea.
 */
@RestController
class NotificacionesMercadoPagoController {

    static final String RUTA = "/api/v1/public/pagos/mercadopago/notificaciones";

    private final NotificacionDePago notificacion;

    NotificacionesMercadoPagoController(NotificacionDePago notificacion) {
        this.notificacion = notificacion;
    }

    @PostMapping(RUTA)
    ResponseEntity<Void> recibir(@RequestHeader(name = "x-signature", required = false) String firma,
                                 @RequestHeader(name = "x-request-id", required = false) String idPedido,
                                 @RequestParam(name = "data.id", required = false) String idDato,
                                 @RequestParam(name = "type", required = false) String tipo,
                                 @RequestBody(required = false) JsonNode cuerpo) {
        String id = idDato;
        if ((id == null || id.isBlank()) && cuerpo != null) {
            id = cuerpo.path("data").path("id").asText(null);
        }
        String tipoNotificacion = tipo;
        if ((tipoNotificacion == null || tipoNotificacion.isBlank()) && cuerpo != null) {
            tipoNotificacion = cuerpo.path("type").asText(null);
        }
        notificacion.procesar(firma, idPedido, id, tipoNotificacion);
        return ResponseEntity.ok().build();
    }
}
