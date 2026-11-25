package ar.turnos.odontologia.agenda.api;

import ar.turnos.odontologia.agenda.domain.CodigoRechazo;
import ar.turnos.odontologia.agenda.domain.ReferenciaInexistenteException;
import ar.turnos.odontologia.agenda.domain.TurnoInexistenteException;
import ar.turnos.odontologia.agenda.domain.TurnoRechazadoException;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Errores de la API de turnos (design D10), con el mismo formato que los de C-05: {@code ProblemDetail} con la
 * propiedad {@code codigo}. Rechazos de la agenda → 409, turno inexistente → 404, entrada inválida → 422.
 */
@RestControllerAdvice(basePackages = "ar.turnos.odontologia.agenda.api")
class AgendaExceptionHandler {

    static final String DATOS_INVALIDOS = "DATOS_INVALIDOS";

    @ExceptionHandler(TurnoRechazadoException.class)
    ProblemDetail turnoRechazado(TurnoRechazadoException e) {
        if (e.codigo() == CodigoRechazo.DATOS_INVALIDOS) {
            return datosInvalidos(List.of(error(campoDeDatosInvalidos(e.getMessage()), e.getMessage())));
        }
        return problema(HttpStatus.CONFLICT, "Turno rechazado", e.codigo().name(), e.getMessage());
    }

    @ExceptionHandler(TurnoInexistenteException.class)
    ProblemDetail turnoInexistente(TurnoInexistenteException e) {
        return problema(HttpStatus.NOT_FOUND, "Turno inexistente", "TURNO_INEXISTENTE",
                "No existe un turno con ese id");
    }

    @ExceptionHandler(ReferenciaInexistenteException.class)
    ProblemDetail referenciaInexistente(ReferenciaInexistenteException e) {
        return datosInvalidos(List.of(error(e.campo(), "no existe")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacion(MethodArgumentNotValidException e) {
        return datosInvalidos(e.getBindingResult().getFieldErrors().stream()
                .map(AgendaExceptionHandler::aError)
                .toList());
    }

    /** Cuerpo ilegible, por ejemplo una fecha sin zona horaria. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail cuerpoIlegible(HttpMessageNotReadableException e) {
        String campo = "cuerpo";
        if (e.getCause() instanceof JsonMappingException jme && !jme.getPath().isEmpty()) {
            String nombre = jme.getPath().get(jme.getPath().size() - 1).getFieldName();
            campo = nombre != null ? nombre : campo;
        }
        return datosInvalidos(List.of(error(campo, "formato inválido: las fechas van en ISO-8601 con zona horaria")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail parametroFaltante(MissingServletRequestParameterException e) {
        return datosInvalidos(List.of(error(e.getParameterName(), "es obligatorio")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametroInvalido(MethodArgumentTypeMismatchException e) {
        return datosInvalidos(List.of(error(e.getName(), "formato inválido")));
    }

    @ExceptionHandler(TurnosController.PeriodoInvalidoException.class)
    ProblemDetail periodoInvalido(TurnosController.PeriodoInvalidoException e) {
        return datosInvalidos(List.of(error("hasta", e.getMessage())));
    }

    /** Los rechazos de negocio por datos inválidos informan el campo que los originó (motivo o fin). */
    private static String campoDeDatosInvalidos(String mensaje) {
        if (mensaje == null) {
            return "cuerpo";
        }
        if (mensaje.contains("motivo")) {
            return "motivo";
        }
        if (mensaje.contains("causa")) {
            return "causa";
        }
        return mensaje.contains("fin") ? "fin" : "cuerpo";
    }

    private static ProblemDetail datosInvalidos(List<Map<String, String>> errores) {
        ProblemDetail problema = problema(HttpStatus.UNPROCESSABLE_ENTITY, "Datos inválidos", DATOS_INVALIDOS,
                "Hay datos inválidos en el pedido");
        problema.setProperty("errores", errores);
        return problema;
    }

    private static ProblemDetail problema(HttpStatus estado, String titulo, String codigo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        problema.setProperty("codigo", codigo);
        return problema;
    }

    private static Map<String, String> aError(FieldError error) {
        return error(error.getField(), error.getDefaultMessage() == null ? "inválido" : error.getDefaultMessage());
    }

    private static Map<String, String> error(String campo, String mensaje) {
        return Map.of("campo", campo, "mensaje", mensaje);
    }
}
