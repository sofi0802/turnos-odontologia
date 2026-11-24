package ar.turnos.odontologia.clinica.api;

import ar.turnos.odontologia.agenda.domain.AusenciaNoPermitidaException;
import ar.turnos.odontologia.agenda.domain.TurnoInexistenteException;
import ar.turnos.odontologia.agenda.domain.TurnoRechazadoException;
import ar.turnos.odontologia.clinica.domain.ClinicaException;
import ar.turnos.odontologia.clinica.domain.CodigoClinica;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Errores de la API clínica en {@code ProblemDetail} con {@code codigo} (design D9). Nunca repite un valor
 * rechazado ni el texto de la ficha: el {@code 422} lleva solo el campo y un mensaje fijo. Los 403 de los permisos
 * los escribe la seguridad; este manejador no atrapa {@code AccessDeniedException}.
 */
@RestControllerAdvice(basePackages = "ar.turnos.odontologia.clinica")
class ClinicaExceptionHandler {

    private static final String DATOS_INVALIDOS = "DATOS_INVALIDOS";

    @ExceptionHandler(ClinicaException.class)
    ProblemDetail clinica(ClinicaException e) {
        HttpStatus status = switch (e.codigo()) {
            case RECURSO_NO_ENCONTRADO -> HttpStatus.NOT_FOUND;
            // C-26: el archivo supera los 10 MB (RN-CL-07) y la imagen ya estaba anulada (RN-CL-08, regla dura 9).
            case ARCHIVO_DEMASIADO_GRANDE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case IMAGEN_YA_ANULADA -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return problema(status, e.codigo().name(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacion(MethodArgumentNotValidException e) {
        ProblemDetail problema = problema(HttpStatus.UNPROCESSABLE_ENTITY, DATOS_INVALIDOS,
                "Hay datos de entrada inválidos");
        // Solo el campo: getRejectedValue() podría ser un dato de salud.
        List<Map<String, String>> errores = e.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("campo", error.getField(), "mensaje", "valor inválido"))
                .toList();
        problema.setProperty("errores", errores);
        return problema;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail ilegible(Exception e) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, DATOS_INVALIDOS, "El pedido no es válido");
    }

    // Rechazos de la agenda al marcar la ausencia: AgendaExceptionHandler se limita a agenda.api (design D6).

    @ExceptionHandler(TurnoInexistenteException.class)
    ProblemDetail turnoInexistente(TurnoInexistenteException e) {
        return problema(HttpStatus.NOT_FOUND, "TURNO_INEXISTENTE", "No existe el turno para ese paciente");
    }

    @ExceptionHandler(AusenciaNoPermitidaException.class)
    ProblemDetail ausenciaNoPermitida(AusenciaNoPermitidaException e) {
        return problema(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", "No tenés permiso para esta operación");
    }

    @ExceptionHandler(TurnoRechazadoException.class)
    ProblemDetail turnoRechazado(TurnoRechazadoException e) {
        return problema(HttpStatus.CONFLICT, e.codigo().name(), e.getMessage());
    }

    private static ProblemDetail problema(HttpStatus status, String codigo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalle);
        problema.setTitle(codigo);
        problema.setProperty("codigo", codigo);
        return problema;
    }
}
