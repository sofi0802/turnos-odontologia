package ar.turnos.odontologia.configuracion.api;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.configuracion.domain.CodigoError;
import ar.turnos.odontologia.configuracion.domain.ConfiguracionException;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;

class ConfiguracionExceptionHandlerTest {

    private final ConfiguracionExceptionHandler handler = new ConfiguracionExceptionHandler();

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "MATRICULA_DUPLICADA, 409",
            "NOMBRE_DUPLICADO, 409",
            "SILLON_HABITUAL_OCUPADO, 409",
            "DURACION_INVALIDA, 422",
            "PRESTACION_INACTIVA, 422",
            "SILLON_INACTIVO, 422",
            "MARGEN_INVALIDO, 422",
            "DATOS_INVALIDOS, 422",
            "RECURSO_NO_ENCONTRADO, 404"
    })
    @DisplayName("Cada código de error sale con su status y en la propiedad codigo")
    void codigoYStatus(CodigoError codigo, int status) {
        ProblemDetail problema = handler.manejar(new ConfiguracionException(codigo, "detalle"));

        assertThat(problema.getStatus()).isEqualTo(status);
        assertThat(problema.getProperties()).containsEntry("codigo", codigo.name());
        assertThat(problema.getDetail()).isEqualTo("detalle");
    }

    @Test
    @DisplayName("Una matrícula repetida detectada por la base sale como MATRICULA_DUPLICADA")
    void matriculaDuplicadaEnLaBase() {
        ProblemDetail problema = handler.manejar(violacion("profesional_matricula_key"));

        assertThat(problema.getStatus()).isEqualTo(409);
        assertThat(problema.getProperties()).containsEntry("codigo", "MATRICULA_DUPLICADA");
    }

    @Test
    @DisplayName("Un nombre de sillón repetido detectado por la base sale como NOMBRE_DUPLICADO")
    void nombreDuplicadoEnLaBase() {
        ProblemDetail problema = handler.manejar(violacion("sillon_nombre_key"));

        assertThat(problema.getStatus()).isEqualTo(409);
        assertThat(problema.getProperties()).containsEntry("codigo", "NOMBRE_DUPLICADO");
    }

    private static DataIntegrityViolationException violacion(String restriccion) {
        var causa = new ConstraintViolationException("duplicado", new SQLException("duplicado"), restriccion);
        return new DataIntegrityViolationException("duplicado", causa);
    }
}
