package ar.turnos.odontologia.shared.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Manejador global de la ruta inexistente ({@code 404 RECURSO_INEXISTENTE}) y del archivo demasiado grande
 * ({@code 413 ARCHIVO_DEMASIADO_GRANDE}, C-26), con el mismo formato ProblemDetail + código del resto de la API. Prioridad la más baja: los manejadores de cada módulo siguen ganando. El {@code 500} de los
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

    /**
     * C-26 (design D5): un archivo que supera {@code spring.servlet.multipart.max-file-size}. Spring lo rechaza al leer
     * el multipart, antes de elegir el controller, así que el manejador de cada módulo no lo ve.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail archivoDemasiadoGrande(MaxUploadSizeExceededException excepcion) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
                "El archivo supera el tamaño máximo permitido");
        problema.setTitle("ARCHIVO_DEMASIADO_GRANDE");
        problema.setProperty("codigo", "ARCHIVO_DEMASIADO_GRANDE");
        return problema;
    }
}
