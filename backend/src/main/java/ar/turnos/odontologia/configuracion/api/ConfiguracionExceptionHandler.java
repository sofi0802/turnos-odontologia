package ar.turnos.odontologia.configuracion.api;

import ar.turnos.odontologia.configuracion.domain.CodigoError;
import ar.turnos.odontologia.configuracion.domain.ConfiguracionException;
import java.util.List;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce los errores del módulo de configuración a {@link ProblemDetail} con un código estable
 * (regla dura 9). Solo atiende a los controladores de este módulo, para no chocar con el
 * manejador global que agregue C-06.
 */
@RestControllerAdvice(basePackages = "ar.turnos.odontologia.configuracion")
public class ConfiguracionExceptionHandler {

    /** Restricciones de la base que también se validan antes en los servicios (design D5). */
    private static final Map<String, CodigoError> RESTRICCIONES = Map.of(
            "profesional_matricula_key", CodigoError.MATRICULA_DUPLICADA,
            "sillon_nombre_key", CodigoError.NOMBRE_DUPLICADO,
            "prestacion_nombre_key", CodigoError.NOMBRE_DUPLICADO,
            "prestacion_duracion_multiplo_5", CodigoError.DURACION_INVALIDA,
            "prestacion_duracion_minutos_check", CodigoError.DURACION_INVALIDA);

    @ExceptionHandler(ConfiguracionException.class)
    public ProblemDetail manejar(ConfiguracionException ex) {
        return problema(ex.codigo(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail manejar(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "campo", error.getField(),
                        "mensaje", String.valueOf(error.getDefaultMessage())))
                .toList();
        ProblemDetail problema = problema(CodigoError.DATOS_INVALIDOS, "Hay datos de entrada inválidos");
        problema.setProperty("errores", errores);
        return problema;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail manejar(HttpMessageNotReadableException ex) {
        return problema(CodigoError.DATOS_INVALIDOS, "El cuerpo de la solicitud no es válido");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail manejar(DataIntegrityViolationException ex) {
        String restriccion = ex.getCause() instanceof ConstraintViolationException cve
                ? cve.getConstraintName()
                : null;
        CodigoError codigo = restriccion == null ? null : RESTRICCIONES.get(restriccion);
        if (codigo == null) {
            throw ex;
        }
        return problema(codigo, "Los datos violan la restricción " + restriccion);
    }

    static HttpStatus statusDe(CodigoError codigo) {
        return switch (codigo) {
            case MATRICULA_DUPLICADA, NOMBRE_DUPLICADO, SILLON_HABITUAL_OCUPADO -> HttpStatus.CONFLICT;
            case RECURSO_NO_ENCONTRADO -> HttpStatus.NOT_FOUND;
            case DURACION_INVALIDA, PRESTACION_INACTIVA, SILLON_INACTIVO, MARGEN_INVALIDO,
                 DATOS_INVALIDOS -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }

    private static ProblemDetail problema(CodigoError codigo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(statusDe(codigo), detalle);
        problema.setTitle(codigo.name());
        problema.setProperty("codigo", codigo.name());
        return problema;
    }
}
