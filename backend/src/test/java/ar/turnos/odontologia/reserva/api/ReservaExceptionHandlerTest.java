package ar.turnos.odontologia.reserva.api;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.agenda.domain.CodigoRechazo;
import ar.turnos.odontologia.agenda.domain.Intervalo;
import ar.turnos.odontologia.agenda.domain.ReferenciaInexistenteException;
import ar.turnos.odontologia.reserva.domain.CodigoReserva;
import ar.turnos.odontologia.reserva.domain.ReservaRechazadaException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

class ReservaExceptionHandlerTest {

    private final ReservaExceptionHandler handler =
            new ReservaExceptionHandler(ZoneId.of("America/Argentina/Buenos_Aires"));

    @Test
    @DisplayName("SIN_DISPONIBILIDAD es un 409 con el código y las alternativas con zona horaria")
    void sinDisponibilidadConAlternativas() {
        Intervalo alternativa = new Intervalo(Instant.parse("2026-12-01T13:45:00Z"),
                Instant.parse("2026-12-01T14:15:00Z"));

        ProblemDetail problema = handler.rechazada(ReservaRechazadaException.sinDisponibilidad(List.of(alternativa)));

        assertThat(problema.getStatus()).isEqualTo(409);
        assertThat(problema.getProperties()).containsEntry("codigo", "SIN_DISPONIBILIDAD");
        assertThat(problema.getProperties().get("alternativas")).asList().hasSize(1);
        HorarioResponse horario = (HorarioResponse) ((List<?>) problema.getProperties().get("alternativas")).get(0);
        assertThat(horario.inicio().toString()).isEqualTo("2026-12-01T10:45-03:00");
    }

    @Test
    @DisplayName("RESERVA_INEXISTENTE es un 404 sin alternativas")
    void reservaInexistente() {
        ProblemDetail problema = handler.rechazada(
                ReservaRechazadaException.de(CodigoReserva.RESERVA_INEXISTENTE, "No existe la reserva"));

        assertThat(problema.getStatus()).isEqualTo(404);
        assertThat(problema.getProperties()).containsEntry("codigo", "RESERVA_INEXISTENTE")
                .doesNotContainKey("alternativas");
    }

    @Test
    @DisplayName("Los demás rechazos de negocio son 409 con su código estable")
    void rechazosDeNegocio() {
        for (CodigoRechazo codigo : List.of(CodigoRechazo.INICIO_EN_EL_PASADO, CodigoRechazo.PROFESIONAL_NO_HABILITADO,
                CodigoRechazo.RECURSO_INACTIVO, CodigoRechazo.TURNO_NO_ACTIVO)) {
            ProblemDetail problema = handler.rechazada(ReservaRechazadaException.de(codigo, "motivo"));
            assertThat(problema.getStatus()).isEqualTo(409);
            assertThat(problema.getProperties()).containsEntry("codigo", codigo.name())
                    .doesNotContainKey("alternativas");
        }
        ProblemDetail plazo = handler.rechazada(ReservaRechazadaException.de(CodigoReserva.CANCELACION_FUERA_DE_PLAZO,
                "Comunicate con el consultorio"));
        assertThat(plazo.getStatus()).isEqualTo(409);
        assertThat(plazo.getProperties()).containsEntry("codigo", "CANCELACION_FUERA_DE_PLAZO");
    }

    @Test
    @DisplayName("Una referencia inexistente es un 422 DATOS_INVALIDOS con el campo")
    void referenciaInexistente() {
        ProblemDetail problema = handler.referenciaInexistente(new ReferenciaInexistenteException("profesionalId"));

        assertThat(problema.getStatus()).isEqualTo(422);
        assertThat(problema.getProperties()).containsEntry("codigo", "DATOS_INVALIDOS");
        assertThat(problema.getProperties().get("errores")).asList()
                .containsExactly(Map.of("campo", "profesionalId", "mensaje", "no existe"));
    }
}
