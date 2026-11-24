package ar.turnos.odontologia.reportes.api;

import ar.turnos.odontologia.reportes.application.ProfesionalInexistenteException;
import ar.turnos.odontologia.reportes.domain.PeriodoInvalidoException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Errores de entrada de los reportes como {@code 422 DATOS_INVALIDOS} con el campo en error (design D8). */
@RestControllerAdvice(basePackages = "ar.turnos.odontologia.reportes")
class ReportesExceptionHandler {

    static final String DATOS_INVALIDOS = "DATOS_INVALIDOS";

    @ExceptionHandler(PeriodoInvalidoException.class)
    ProblemDetail periodoInvalido(PeriodoInvalidoException e) {
        return datosInvalidos(e.campo(), e.getMessage());
    }

    @ExceptionHandler(ProfesionalInexistenteException.class)
    ProblemDetail profesionalInexistente(ProfesionalInexistenteException e) {
        return datosInvalidos("profesionalId", e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail parametroFaltante(MissingServletRequestParameterException e) {
        return datosInvalidos(e.getParameterName(), "Es obligatorio.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametroInvalido(MethodArgumentTypeMismatchException e) {
        return datosInvalidos(e.getName(), "Formato inválido: las fechas van como AAAA-MM-DD.");
    }

    private static ProblemDetail datosInvalidos(String campo, String mensaje) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                "Hay datos inválidos en el pedido");
        problema.setTitle("Datos inválidos");
        problema.setProperty("codigo", DATOS_INVALIDOS);
        problema.setProperty("errores", List.of(Map.of("campo", campo, "mensaje", mensaje)));
        return problema;
    }
}
