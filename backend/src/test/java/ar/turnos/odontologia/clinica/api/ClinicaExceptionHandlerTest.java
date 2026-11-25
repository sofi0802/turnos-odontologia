package ar.turnos.odontologia.clinica.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ar.turnos.odontologia.clinica.domain.ClinicaException;
import ar.turnos.odontologia.clinica.domain.CodigoClinica;
import ar.turnos.odontologia.clinica.domain.ConsentimientoFirmado.Firmante;
import ar.turnos.odontologia.clinica.domain.VersionPlantilla;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.ProblemDetail;

/** Estados y cuerpos de los códigos nuevos de C-27 (design D9, tarea 1.1). Datos ficticios. */
class ClinicaExceptionHandlerTest {

    private final ClinicaExceptionHandler manejador = new ClinicaExceptionHandler();
    private final ObjectMapper json = new ObjectMapper();

    @ParameterizedTest(name = "{0} responde {1}")
    @DisplayName("Cada código nuevo de consentimientos responde su estado")
    @CsvSource({
            "PLANTILLA_DUPLICADA, 409",
            "VERSION_DESACTUALIZADA, 409",
            "PLANTILLA_INACTIVA, 409",
            "CONSENTIMIENTO_YA_REVOCADO, 409",
            "FIRMANTE_NO_COINCIDE, 422",
            // Los existentes no cambian.
            "RECURSO_NO_ENCONTRADO, 404",
            "IMAGEN_YA_ANULADA, 409",
            "DATOS_INVALIDOS, 422"
    })
    void cadaCodigoRespondeSuEstado(CodigoClinica codigo, int estado) {
        ProblemDetail problema = manejador.clinica(new ClinicaException(codigo, "Mensaje fijo"));

        assertThat(problema.getStatus()).isEqualTo(estado);
        assertThat(problema.getProperties()).containsEntry("codigo", codigo.name());
        assertThat(problema.getTitle()).isEqualTo(codigo.name());
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("El cuerpo lleva solo el código y el mensaje fijo, sin el valor rechazado")
    @CsvSource({"FIRMANTE_NO_COINCIDE", "PLANTILLA_DUPLICADA", "VERSION_DESACTUALIZADA"})
    void elCuerpoNoRepiteElValorRechazado(CodigoClinica codigo) throws Exception {
        ProblemDetail problema = manejador.clinica(new ClinicaException(codigo, "Mensaje fijo"));

        String cuerpo = json.writeValueAsString(problema);

        assertThat(problema.getProperties()).containsOnlyKeys("codigo");
        assertThat(problema.getDetail()).isEqualTo("Mensaje fijo");
        assertThat(cuerpo).doesNotContain("99000002").doesNotContain("Extracción");
    }

    @Test
    @DisplayName("Un error no repite el DNI escrito")
    void unErrorNoRepiteElDniEscrito() throws Exception {
        ClinicaException error = catchThrowableOfType(ClinicaException.class,
                () -> Firmante.de("Paciente Uno", "99000002", true, "99000001"));

        ProblemDetail problema = manejador.clinica(error);
        String cuerpo = json.writeValueAsString(problema);

        assertThat(problema.getStatus()).isEqualTo(422);
        assertThat(problema.getProperties()).containsEntry("codigo", "FIRMANTE_NO_COINCIDE");
        assertThat(cuerpo).doesNotContain("99000002").doesNotContain("99000001").doesNotContain("Paciente Uno");
    }

    @Test
    @DisplayName("Un título demasiado largo no se repite en el error")
    void tituloLargoNoSeRepite() throws Exception {
        ClinicaException error = catchThrowableOfType(ClinicaException.class,
                () -> VersionPlantilla.nueva("Extracción" + "x".repeat(120), "Texto ficticio"));

        String cuerpo = json.writeValueAsString(manejador.clinica(error));

        assertThat(cuerpo).contains("DATOS_INVALIDOS").doesNotContain("Extracción").doesNotContain("Texto ficticio");
    }
}
