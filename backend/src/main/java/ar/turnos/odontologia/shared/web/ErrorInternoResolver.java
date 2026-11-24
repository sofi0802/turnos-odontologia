package ar.turnos.odontologia.shared.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.List;

/**
 * {@code 500 ERROR_INTERNO} para un error no controlado, sin traza, mensaje ni datos del pedido en la respuesta.
 *
 * <p>Se registra al <b>final</b> de la cadena de resolvers de Spring MVC, así que solo atiende lo que nadie resolvió:
 * los {@code @ExceptionHandler} de los módulos y los {@code 4xx} propios de Spring MVC (JSON ilegible, método no
 * soportado, parámetro faltante…) siguen con su estado. {@link AccessDeniedException} y {@link AuthenticationException}
 * (por ejemplo las de {@code @PreAuthorize}) se dejan pasar para que la cadena de seguridad responda {@code 403} o {@code 401}.
 */
@Configuration
class ErrorInternoResolver implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(ErrorInternoResolver.class);

    private final ObjectMapper objectMapper;

    ErrorInternoResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
        resolvers.add(this::resolver);
    }

    private ModelAndView resolver(HttpServletRequest request, HttpServletResponse response, Object handler,
                                  Exception excepcion) {
        if (excepcion instanceof AccessDeniedException || excepcion instanceof AuthenticationException
                || response.isCommitted()) {
            return null;
        }
        // Solo el método HTTP y la excepción: nunca la query string (puede traer un DNI, RN-NO-01) ni los parámetros
        log.error("Error no controlado en un pedido {}", request.getMethod(), excepcion);
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado");
        problema.setTitle(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        problema.setProperty("codigo", "ERROR_INTERNO");
        try {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getOutputStream(), problema);
        } catch (IOException e) {
            log.error("No se pudo escribir la respuesta de error interno", e);
        }
        return new ModelAndView();
    }
}
