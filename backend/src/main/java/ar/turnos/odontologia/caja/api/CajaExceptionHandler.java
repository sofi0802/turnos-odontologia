package ar.turnos.odontologia.caja.api;

import ar.turnos.odontologia.caja.application.CajaException;
import ar.turnos.odontologia.caja.application.CodigoCaja;
import ar.turnos.odontologia.caja.application.PeriodoInvalidoException;
import ar.turnos.odontologia.caja.domain.ImporteInvalidoException;
import ar.turnos.odontologia.caja.domain.MotivoObligatorioException;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Errores de la caja: reglas como {@code 409} con código estable y entrada como {@code 422} (design D7). */
@RestControllerAdvice(basePackages = "ar.turnos.odontologia.caja")
class CajaExceptionHandler {

    static final String DATOS_INVALIDOS = "DATOS_INVALIDOS";

    @ExceptionHandler(CajaException.class)
    ResponseEntity<ProblemDetail> regla(CajaException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(e.codigo().estado(), e.getMessage());
        problema.setTitle(e.codigo() == CodigoCaja.RECURSO_NO_ENCONTRADO ? "No encontrado" : "Rechazado");
        problema.setProperty("codigo", e.codigo().name());
        if (e.cajaId() != null) {
            problema.setProperty("cajaId", e.cajaId());
        }
        return ResponseEntity.status(e.codigo().estado()).body(problema);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacion(MethodArgumentNotValidException e) {
        return datosInvalidos(e.getBindingResult().getFieldErrors().stream()
                .map(f -> error(f.getField(), f.getDefaultMessage() == null ? "Es inválido." : f.getDefaultMessage()))
                .toList());
    }

    @ExceptionHandler(MotivoObligatorioException.class)
    ProblemDetail motivo(MotivoObligatorioException e) {
        String campo = e.getMessage().contains("anulación") ? "motivo" : "motivoDiferencia";
        return datosInvalidos(List.of(error(campo, e.getMessage())));
    }

    @ExceptionHandler(ImporteInvalidoException.class)
    ProblemDetail importe(ImporteInvalidoException e) {
        return datosInvalidos(List.of(error("importe", e.getMessage())));
    }

    @ExceptionHandler(PeriodoInvalidoException.class)
    ProblemDetail periodo(PeriodoInvalidoException e) {
        return datosInvalidos(List.of(error("hasta", e.getMessage())));
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

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail parametroFaltante(MissingServletRequestParameterException e) {
        return datosInvalidos(List.of(error(e.getParameterName(), "Es obligatorio.")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametroInvalido(MethodArgumentTypeMismatchException e) {
        return datosInvalidos(List.of(error(e.getName(), "Formato inválido.")));
    }

    private static ProblemDetail datosInvalidos(List<Map<String, String>> errores) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                "Hay datos inválidos en el pedido");
        problema.setTitle("Datos inválidos");
        problema.setProperty("codigo", DATOS_INVALIDOS);
        problema.setProperty("errores", errores);
        return problema;
    }

    private static Map<String, String> error(String campo, String mensaje) {
        return Map.of("campo", campo, "mensaje", mensaje);
    }
}
