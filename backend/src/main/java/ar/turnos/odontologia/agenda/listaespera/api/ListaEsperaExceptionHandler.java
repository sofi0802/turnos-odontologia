package ar.turnos.odontologia.agenda.listaespera.api;

import ar.turnos.odontologia.agenda.listaespera.domain.CodigoListaEspera;
import ar.turnos.odontologia.agenda.listaespera.domain.ListaEsperaException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Errores de la lista de espera con el formato de siempre: {@code ProblemDetail} con {@code codigo}. El detalle es el
 * mensaje del dominio, que nunca lleva el token ni la prestación.
 */
@RestControllerAdvice(basePackages = "ar.turnos.odontologia.agenda.listaespera.api")
class ListaEsperaExceptionHandler {

    /** La ruta de la oferta lleva el token: el error nunca la repite ({@code instance} sería la URL completa). */
    static final URI RUTA_OFERTAS = URI.create("/api/v1/public/ofertas");

    @ExceptionHandler(ListaEsperaException.class)
    ProblemDetail rechazo(ListaEsperaException e, HttpServletRequest pedido) {
        CodigoListaEspera codigo = e.codigo();
        ProblemDetail problema = problema(HttpStatus.valueOf(codigo.estadoHttp()), codigo.name(), e.getMessage());
        if (pedido.getRequestURI().startsWith(RUTA_OFERTAS.getPath())) {
            problema.setInstance(RUTA_OFERTAS);
        }
        return problema;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacion(MethodArgumentNotValidException e) {
        return datosInvalidos(e.getBindingResult().getFieldErrors().stream()
                .map(ListaEsperaExceptionHandler::aError).toList());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail cuerpoIlegible(HttpMessageNotReadableException e) {
        return datosInvalidos(List.of(Map.of("campo", "cuerpo", "mensaje", "formato inválido")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametroInvalido(MethodArgumentTypeMismatchException e) {
        return datosInvalidos(List.of(Map.of("campo", e.getName(), "mensaje", "formato inválido")));
    }

    private static ProblemDetail datosInvalidos(List<Map<String, String>> errores) {
        ProblemDetail problema = problema(HttpStatus.UNPROCESSABLE_ENTITY, CodigoListaEspera.DATOS_INVALIDOS.name(),
                "Hay datos inválidos en el pedido");
        problema.setProperty("errores", errores);
        return problema;
    }

    private static ProblemDetail problema(HttpStatus estado, String codigo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(estado.getReasonPhrase());
        problema.setProperty("codigo", codigo);
        return problema;
    }

    private static Map<String, String> aError(FieldError error) {
        return Map.of("campo", error.getField(),
                "mensaje", error.getDefaultMessage() == null ? "inválido" : error.getDefaultMessage());
    }
}
