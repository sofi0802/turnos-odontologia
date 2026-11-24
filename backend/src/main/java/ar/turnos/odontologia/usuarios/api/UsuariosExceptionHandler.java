package ar.turnos.odontologia.usuarios.api;

import ar.turnos.odontologia.usuarios.domain.BusquedaInvalidaException;
import ar.turnos.odontologia.usuarios.domain.CodigoRegistro;
import ar.turnos.odontologia.usuarios.domain.CodigoCuenta;
import ar.turnos.odontologia.usuarios.domain.CuentaRechazadaException;
import ar.turnos.odontologia.usuarios.domain.DatosCuentaInvalidosException;
import ar.turnos.odontologia.usuarios.domain.RegistroRechazadoException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

/**
 * Errores de los controllers de usuarios. Se limita a este paquete y tiene prioridad alta para no pisar
 * el manejador global de la API (C-06).
 */
@RestControllerAdvice(basePackages = "ar.turnos.odontologia.usuarios.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
class UsuariosExceptionHandler {

    /** Mismo mensaje para contraseña incorrecta, usuario inexistente e inactivo. */
    static final String DETALLE_CREDENCIALES = "Usuario o contraseña incorrectos";

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail credencialesInvalidas(AuthenticationException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, DETALLE_CREDENCIALES);
        problema.setTitle("Credenciales inválidas");
        problema.setProperty("codigo", "CREDENCIALES_INVALIDAS");
        return problema;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail datosInvalidos(MethodArgumentNotValidException e) {
        List<Map<String, String>> errores = e.getBindingResult().getFieldErrors().stream()
                .map(UsuariosExceptionHandler::aError)
                .toList();
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                "Hay datos inválidos en el pedido");
        problema.setTitle("Datos inválidos");
        problema.setProperty("codigo", "DATOS_INVALIDOS");
        problema.setProperty("errores", errores);
        return problema;
    }

    /** El mensaje es fijo: nunca repite el término buscado, que es un dato personal. */
    @ExceptionHandler(BusquedaInvalidaException.class)
    ProblemDetail busquedaInvalida(BusquedaInvalidaException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                "Hay datos inválidos en el pedido");
        problema.setTitle("Datos inválidos");
        problema.setProperty("codigo", "DATOS_INVALIDOS");
        problema.setProperty("errores", List.of(Map.of("campo", "q", "mensaje", e.getMessage())));
        return problema;
    }

    @ExceptionHandler(CuentaRechazadaException.class)
    ProblemDetail cuentaRechazada(CuentaRechazadaException e) {
        return cuentaRechazada(e.codigo());
    }

    /** El mensaje es fijo y nunca repite el valor recibido (C-41). */
    @ExceptionHandler(DatosCuentaInvalidosException.class)
    ProblemDetail datosDeCuentaInvalidos(DatosCuentaInvalidosException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                "Hay datos inválidos en el pedido");
        problema.setTitle("Datos inválidos");
        problema.setProperty("codigo", "DATOS_INVALIDOS");
        problema.setProperty("errores", List.of(Map.of("campo", e.campo(), "mensaje", e.getMessage())));
        return problema;
    }

    static ProblemDetail cuentaRechazada(CodigoCuenta codigo) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(codigo.estado()), codigo.detalle());
        problema.setTitle("Operación sobre la cuenta rechazada");
        problema.setProperty("codigo", codigo.name());
        return problema;
    }

    @ExceptionHandler(RegistroRechazadoException.class)
    ProblemDetail registroRechazado(RegistroRechazadoException e) {
        return conflicto(e.codigo());
    }

    /**
     * Una carrera entre dos registros llega como violación de unicidad: se traduce según el nombre de la
     * restricción, así el resultado no depende de quién llega primero. Otras violaciones se vuelven a lanzar.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail violacionDeIntegridad(DataIntegrityViolationException e) {
        String restriccion = nombreDeRestriccion(e);
        if (restriccion.contains("paciente_dni_key")) {
            return conflicto(CodigoRegistro.DNI_YA_REGISTRADO);
        }
        if (restriccion.contains("usuario_username_unico")) {
            return conflicto(CodigoRegistro.EMAIL_YA_REGISTRADO);
        }
        throw e;
    }

    static String nombreDeRestriccion(Throwable e) {
        for (Throwable causa = e; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException cv && cv.getConstraintName() != null) {
                return cv.getConstraintName();
            }
        }
        // sin nombre estructurado, se busca en el mensaje más específico
        return String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage());
    }

    private static ProblemDetail conflicto(CodigoRegistro codigo) {
        String detalle = codigo == CodigoRegistro.DNI_YA_REGISTRADO
                ? "Ya existe un paciente con ese DNI"
                : "Ya existe una cuenta con ese email";
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detalle);
        problema.setTitle("Registro rechazado");
        problema.setProperty("codigo", codigo.name());
        return problema;
    }

    private static Map<String, String> aError(FieldError error) {
        return Map.of("campo", error.getField(),
                "mensaje", error.getDefaultMessage() == null ? "inválido" : error.getDefaultMessage());
    }
}
