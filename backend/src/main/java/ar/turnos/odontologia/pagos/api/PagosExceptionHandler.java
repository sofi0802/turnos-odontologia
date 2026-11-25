package ar.turnos.odontologia.pagos.api;

import ar.turnos.odontologia.pagos.application.FirmaInvalidaException;
import ar.turnos.odontologia.pagos.application.PasarelaNoDisponibleException;
import ar.turnos.odontologia.pagos.domain.DatoInvalidoException;
import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Errores del módulo de pagos: entrada como {@code 422 DATOS_INVALIDOS} con el campo (design D9). */
@RestControllerAdvice(basePackages = "ar.turnos.odontologia.pagos")
class PagosExceptionHandler {

    static final String DATOS_INVALIDOS = "DATOS_INVALIDOS";

    @ExceptionHandler(DatoInvalidoException.class)
    ProblemDetail datoInvalido(DatoInvalidoException e) {
        return datosInvalidos(List.of(error(e.campo(), e.getMessage())));
    }

    @ExceptionHandler(FirmaInvalidaException.class)
    ResponseEntity<ProblemDetail> firma(FirmaInvalidaException e) {
        return problema(HttpStatus.UNAUTHORIZED, "No autorizado", "FIRMA_INVALIDA", e.getMessage());
    }

    @ExceptionHandler(PasarelaNoDisponibleException.class)
    ResponseEntity<ProblemDetail> pasarela(PasarelaNoDisponibleException e) {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Pasarela no disponible", "PASARELA_NO_DISPONIBLE",
                "Mercado Pago no respondió. Probá de nuevo en unos minutos.");
    }

    static ResponseEntity<ProblemDetail> problema(HttpStatus estado, String titulo, String codigo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        problema.setProperty("codigo", codigo);
        return ResponseEntity.status(estado).body(problema);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail cuerpo(HttpMessageNotReadableException e) {
        String campo = "cuerpo";
        if (e.getCause() instanceof JsonMappingException jme && !jme.getPath().isEmpty()) {
            String nombre = jme.getPath().get(jme.getPath().size() - 1).getFieldName();
            campo = nombre != null ? nombre : campo;
        }
        return datosInvalidos(List.of(error(campo, "Formato inválido.")));
    }

    static ProblemDetail datosInvalidos(List<Map<String, String>> errores) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                "Hay datos inválidos en el pedido");
        problema.setTitle("Datos inválidos");
        problema.setProperty("codigo", DATOS_INVALIDOS);
        problema.setProperty("errores", errores);
        return problema;
    }

    static Map<String, String> error(String campo, String mensaje) {
        return Map.of("campo", campo, "mensaje", mensaje);
    }
}
