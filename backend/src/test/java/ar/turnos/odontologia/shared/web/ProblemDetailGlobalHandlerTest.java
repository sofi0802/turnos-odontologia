package ar.turnos.odontologia.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * C-26 (design D5): el límite de multipart de Spring se rechaza antes de elegir el controller, así que lo responde el
 * manejador global. MockMvc no aplica ese límite, por eso se prueba el manejador directamente; el límite del dominio
 * lo prueba {@code ImagenClinicaIntegrationTest}.
 */
class ProblemDetailGlobalHandlerTest {

    @Test
    @DisplayName("Un archivo que supera el límite de multipart responde 413 ARCHIVO_DEMASIADO_GRANDE")
    void archivoDemasiadoGrande() {
        ProblemDetail problema = new ProblemDetailGlobalHandler()
                .archivoDemasiadoGrande(new MaxUploadSizeExceededException(10_485_760L));

        assertThat(problema.getStatus()).isEqualTo(413);
        assertThat(problema.getProperties()).containsEntry("codigo", "ARCHIVO_DEMASIADO_GRANDE");
    }
}
