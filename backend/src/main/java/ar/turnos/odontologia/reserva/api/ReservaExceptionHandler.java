package ar.turnos.odontologia.reserva.api;

import ar.turnos.odontologia.agenda.domain.ReferenciaInexistenteException;
import ar.turnos.odontologia.reserva.application.SenasDeReserva;
import ar.turnos.odontologia.reserva.domain.CodigoReserva;
import ar.turnos.odontologia.reserva.domain.ReservaRechazadaException;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.time.ZoneId;
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
 * Errores de la reserva online (design D7), con el formato de C-05 y C-06: {@code ProblemDetail} con la propiedad
 * {@code codigo}. Rechazos de negocio → 409, reserva inexistente → 404, entrada inválida → 422. Los códigos de
 * disponibilidad de la agenda nunca llegan acá: el caso de uso los traduce a {@code SIN_DISPONIBILIDAD}.
 */
@RestControllerAdvice(basePackages = "ar.turnos.odontologia.reserva.api")
class ReservaExceptionHandler {

    static final String DATOS_INVALIDOS = "DATOS_INVALIDOS";

    private final ZoneId zona;

    ReservaExceptionHandler(ZoneId zonaHoraria) {
        this.zona = zonaHoraria;
    }

    @ExceptionHandler(ReservaRechazadaException.class)
    ProblemDetail rechazada(ReservaRechazadaException e) {
        boolean inexistente = CodigoReserva.RESERVA_INEXISTENTE.name().equals(e.codigo());
        ProblemDetail problema = problema(inexistente ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT,
                inexistente ? "Reserva inexistente" : "Reserva rechazada", e.codigo(), e.getMessage());
        if (CodigoReserva.SIN_DISPONIBILIDAD.name().equals(e.codigo())) {
            problema.setProperty("alternativas", e.alternativas().stream()
                    .map(horario -> HorarioResponse.de(horario, zona)).toList());
        }
        return problema;
    }

    /** C-23: el turno no tiene una seña pendiente para pedir un link nuevo. */
    @ExceptionHandler(SenasDeReserva.SenaNoPendienteException.class)
    ProblemDetail senaNoPendiente(SenasDeReserva.SenaNoPendienteException e) {
        return problema(HttpStatus.CONFLICT, "Seña no pendiente", "SENA_NO_PENDIENTE", e.getMessage());
    }

    /** C-23: la pasarela no respondió o no se pudo guardar la seña. */
    @ExceptionHandler(SenasDeReserva.SenaNoDisponibleException.class)
    ProblemDetail senaNoDisponible(SenasDeReserva.SenaNoDisponibleException e) {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Seña no disponible", e.codigo(), e.getMessage());
    }

    @ExceptionHandler(ReferenciaInexistenteException.class)
    ProblemDetail referenciaInexistente(ReferenciaInexistenteException e) {
        return datosInvalidos(List.of(error(e.campo(), "no existe")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacion(MethodArgumentNotValidException e) {
        return datosInvalidos(e.getBindingResult().getFieldErrors().stream()
                .map(ReservaExceptionHandler::aError)
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
