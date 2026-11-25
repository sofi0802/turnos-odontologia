package ar.turnos.odontologia.notificaciones.api;

import ar.turnos.odontologia.notificaciones.application.PacienteNoEncontradoException;
import ar.turnos.odontologia.notificaciones.domain.CodigoConfirmacion;
import ar.turnos.odontologia.notificaciones.domain.ConfirmacionRechazadaException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Errores de los controllers de notificaciones, con el mismo formato que el resto de la API: {@code ProblemDetail}
 * con la propiedad {@code codigo}. Token inexistente → 404; token usado o turno no confirmable → 409. El detalle
 * nunca incluye el token.
 */
@RestControllerAdvice(basePackages = "ar.turnos.odontologia.notificaciones.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
class NotificacionesExceptionHandler {

    @ExceptionHandler(ConfirmacionRechazadaException.class)
    ProblemDetail confirmacionRechazada(ConfirmacionRechazadaException e) {
        CodigoConfirmacion codigo = e.codigo();
        return switch (codigo) {
            case TOKEN_INEXISTENTE -> problema(HttpStatus.NOT_FOUND, "Token inexistente", codigo,
                    "El link de confirmación no es válido");
            case TOKEN_USADO -> problema(HttpStatus.CONFLICT, "Token ya usado", codigo,
                    "El link de confirmación ya se usó");
            case TURNO_NO_CONFIRMABLE -> problema(HttpStatus.CONFLICT, "Turno no confirmable", codigo,
                    "El turno ya no se puede confirmar");
        };
    }

    /** C-17: el paciente del consentimiento no existe. */
    @ExceptionHandler(PacienteNoEncontradoException.class)
    ProblemDetail pacienteNoEncontrado(PacienteNoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", "RECURSO_NO_ENCONTRADO", e.getMessage());
    }

    /** C-17: falta {@code otorgado}, falta el medio o el medio no existe. Nunca repite datos del paciente. */
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    ProblemDetail datosInvalidos(Exception e) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Datos inválidos", "DATOS_INVALIDOS",
                "Hay datos inválidos en el pedido");
    }

    private static ProblemDetail problema(HttpStatus estado, String titulo, CodigoConfirmacion codigo, String detalle) {
        return problema(estado, titulo, codigo.name(), detalle);
    }

    private static ProblemDetail problema(HttpStatus estado, String titulo, String codigo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        problema.setProperty("codigo", codigo);
        return problema;
    }
}
