package ar.turnos.odontologia.shared.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Manejador global de la ruta inexistente: {@code 404 RECURSO_INEXISTENTE} con el mismo formato ProblemDetail + código
 * del resto de la API. Prioridad la más baja: los manejadores de cada módulo siguen ganando. El {@code 500} de los
 * errores no controlados vive en {@link ErrorInternoResolver}, que corre después de los manejadores de Spring MVC.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class ProblemDetailGlobalHandler {

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail recursoInexistente(NoResourceFoundException excepcion) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "El recurso pedido no existe");
        problema.setTitle(HttpStatus.NOT_FOUND.getReasonPhrase());
        problema.setProperty("codigo", "RECURSO_INEXISTENTE");
        return problema;
    }
}
