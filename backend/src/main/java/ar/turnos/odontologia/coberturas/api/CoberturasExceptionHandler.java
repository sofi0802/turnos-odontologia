package ar.turnos.odontologia.coberturas.api;

import ar.turnos.odontologia.coberturas.domain.CoberturaException;
import ar.turnos.odontologia.coberturas.domain.CodigoCobertura;
import java.util.List;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Errores de la API de coberturas en {@code ProblemDetail} con {@code codigo} estable (design D6). Los mensajes son
 * fijos por código: nunca repiten un número de afiliado ni nombran a otro paciente (RN-OS-07), y el {@code 422} lleva
 * solo el campo. Los {@code 403} de los permisos los escribe la seguridad: este manejador no atrapa
 * {@code AccessDeniedException}.
 */
@RestControllerAdvice(basePackages = "ar.turnos.odontologia.coberturas")
class CoberturasExceptionHandler {

    /** Restricciones de la base que también se validan antes en los servicios (design D4 y D6). */
    private static final Map<String, CoberturaException> RESTRICCIONES = Map.of(
            "obra_social_nombre_unico", new CoberturaException(CodigoCobertura.OBRA_SOCIAL_EXISTENTE,
                    "Ya existe una obra social con ese nombre"),
            "plan_nombre_unico", new CoberturaException(CodigoCobertura.PLAN_EXISTENTE,
                    "Ya existe un plan con ese nombre en la obra social"),
            "nomenclador_codigo_unico", new CoberturaException(CodigoCobertura.CODIGO_NOMENCLADOR_EXISTENTE,
                    "Ya existe un código con ese valor en el nomenclador"),
            // Las dos EXCLUDE de las afiliaciones frenan a los pedidos que pasaron a la vez la verificación previa.
            "afiliacion_sin_superposicion", new CoberturaException(CodigoCobertura.VIGENCIA_SUPERPUESTA,
                    "La vigencia se superpone con otra afiliación del paciente"),
            "afiliacion_numero_unico_por_obra_social", new CoberturaException(CodigoCobertura.AFILIADO_DUPLICADO,
                    "El número de afiliado ya está registrado en esa obra social"),
            "afiliacion_vigencia_valida", new CoberturaException(CodigoCobertura.VIGENCIA_INVALIDA,
                    "La fecha de fin no puede ser anterior a la de inicio"),
            "afiliacion_numero_valido", CoberturaException.datosInvalidos("numeroAfiliado",
                    "El número de afiliado no es válido"));

    @ExceptionHandler(CoberturaException.class)
    ProblemDetail cobertura(CoberturaException e) {
        ProblemDetail problema = problema(e.codigo(), e.getMessage());
        if (e.codigo() == CodigoCobertura.DATOS_INVALIDOS && e.campo() != null) {
            problema.setProperty("errores", List.of(Map.of("campo", e.campo(), "mensaje", "valor inválido")));
        }
        return problema;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacion(MethodArgumentNotValidException e) {
        ProblemDetail problema = problema(CodigoCobertura.DATOS_INVALIDOS, "Hay datos de entrada inválidos");
        // Solo el campo: getRejectedValue() podría ser un número de afiliado.
        List<Map<String, String>> errores = e.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("campo", error.getField(), "mensaje", "valor inválido"))
                .toList();
        problema.setProperty("errores", errores);
        return problema;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail ilegible(Exception e) {
        return problema(CodigoCobertura.DATOS_INVALIDOS, "El pedido no es válido");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integridad(DataIntegrityViolationException e) {
        String restriccion = nombreDeRestriccion(e);
        for (Map.Entry<String, CoberturaException> conocida : RESTRICCIONES.entrySet()) {
            if (restriccion.contains(conocida.getKey())) {
                return cobertura(conocida.getValue());
            }
        }
        throw e;
    }

    static String nombreDeRestriccion(Throwable e) {
        for (Throwable causa = e; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException cv && cv.getConstraintName() != null) {
                return cv.getConstraintName();
            }
        }
        return String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage());
    }

    static HttpStatus estadoDe(CodigoCobertura codigo) {
        return switch (codigo.estado()) {
            case CONFLICTO -> HttpStatus.CONFLICT;
            case NO_ENCONTRADO -> HttpStatus.NOT_FOUND;
            case INVALIDO -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }

    private static ProblemDetail problema(CodigoCobertura codigo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estadoDe(codigo), detalle);
        problema.setTitle(codigo.name());
        problema.setProperty("codigo", codigo.name());
        return problema;
    }
}
