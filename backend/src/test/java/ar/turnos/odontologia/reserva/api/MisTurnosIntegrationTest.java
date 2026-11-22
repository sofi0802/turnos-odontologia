package ar.turnos.odontologia.reserva.api;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.reserva.IntegracionReservaBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Escenarios de "Consultar mis turnos", "Indicador de modificación online" y la ruta solo para el paciente. */
@Tag("integration")
class MisTurnosIntegrationTest extends IntegracionReservaBase {

    private static final String MIS_TURNOS = "/api/v1/paciente/turnos";

    @Test
    @DisplayName("Solo los turnos propios")
    void soloLosTurnosPropios() throws Exception {
        long segundo = turnoActivo(pacienteUno, draA, sillon1, 3, 10, 0);
        long primero = turnoActivo(pacienteUno, draA, sillon1, 2, 10, 0);
        long ajeno = turnoActivo(pacienteDos, draA, sillon1, 2, 11, 0);

        MvcResult resultado = comoPacienteUno().get(MIS_TURNOS).andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        JsonNode turnos = cuerpo(resultado);
        assertThat(turnos).hasSize(2);
        assertThat(turnos.get(0).get("numeroReserva").asText()).isEqualTo(numeroDe(primero));
        assertThat(turnos.get(1).get("numeroReserva").asText()).isEqualTo(numeroDe(segundo));
        assertThat(turnos.get(0).get("inicio").asText()).isEqualTo("2026-12-02T10:00:00-03:00");
        assertThat(turnos.get(0).get("fin").asText()).isEqualTo("2026-12-02T10:30:00-03:00");
        assertThat(turnos.get(0).get("estado").asText()).isEqualTo("RESERVADO");
        assertThat(turnos.get(0).get("prestacion").get("nombre").asText()).isEqualTo("Consulta");
        assertThat(turnos.get(0).get("profesional").get("nombre").asText()).isEqualTo("Ana Ejemplo");
        assertThat(texto(resultado)).doesNotContain(numeroDe(ajeno)).doesNotContain("sillon")
                .doesNotContain("Paciente Dos");
    }

    @Test
    @DisplayName("Intento de ver los turnos de otro paciente")
    void intentoDeVerLosTurnosDeOtroPaciente() throws Exception {
        long ajeno = turnoActivo(pacienteDos, draA, sillon1, 2, 11, 0);

        MvcResult resultado = comoPacienteUno().get(MIS_TURNOS + "?pacienteId=" + pacienteDos).andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(cuerpo(resultado)).isEmpty();
        assertThat(texto(resultado)).doesNotContain(numeroDe(ajeno));
    }

    @Test
    @DisplayName("Turnos dentro y fuera del plazo")
    void turnosDentroYFueraDelPlazo() throws Exception {
        long dentro = turnoActivo(pacienteUno, draA, sillon1, 2, 9, 0);
        long fuera = turnoActivo(pacienteUno, draA, sillon1, 1, 10, 0);

        JsonNode turnos = cuerpo(comoPacienteUno().get(MIS_TURNOS).andReturn());

        assertThat(turnos).hasSize(2);
        assertThat(turnos.get(0).get("numeroReserva").asText()).isEqualTo(numeroDe(fuera));
        assertThat(turnos.get(0).get("modificableOnline").asBoolean()).isFalse();
        assertThat(turnos.get(1).get("numeroReserva").asText()).isEqualTo(numeroDe(dentro));
        assertThat(turnos.get(1).get("modificableOnline").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Un turno cancelado o atendido no es modificable online")
    void turnoCanceladoNoEsModificable() throws Exception {
        turnoCancelado(pacienteUno, draA, sillon1, 3, 10, 0);

        JsonNode turnos = cuerpo(comoPacienteUno().get(MIS_TURNOS).andReturn());

        assertThat(turnos).hasSize(1);
        assertThat(turnos.get(0).get("estado").asText()).isEqualTo("CANCELADO");
        assertThat(turnos.get(0).get("modificableOnline").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Mis turnos sin sesión")
    void misTurnosSinSesion() throws Exception {
        MvcResult resultado = sinSesion().get(MIS_TURNOS).andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(401);
        assertThat(cuerpo(resultado).get("codigo").asText()).isEqualTo("NO_AUTENTICADO");
    }

    @Test
    @DisplayName("Mis turnos con una sesión del personal")
    void misTurnosConSesionDelPersonal() throws Exception {
        MvcResult resultado = comoRecepcion().get(MIS_TURNOS).andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(403);
        assertThat(cuerpo(resultado).get("codigo").asText()).isEqualTo("ACCESO_DENEGADO");
    }
}
