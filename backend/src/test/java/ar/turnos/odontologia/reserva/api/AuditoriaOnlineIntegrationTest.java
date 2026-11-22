package ar.turnos.odontologia.reserva.api;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.reserva.IntegracionReservaBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Escenarios de {@code specs/auditoria-turnos}: el actor de las operaciones online del paciente. */
@Tag("integration")
class AuditoriaOnlineIntegrationTest extends IntegracionReservaBase {

    @BeforeEach
    void franjas() {
        for (int dia = 1; dia <= 5; dia++) {
            franja(draA, dia, "08:00", "13:00");
            franja(drB, dia, "10:00", "18:00");
        }
    }

    private List<Map<String, Object>> auditoria(long turnoId) {
        return jdbc.queryForList("SELECT accion, actor, turno_relacionado_id FROM turno_auditoria WHERE turno_id = ? "
                + "ORDER BY id", turnoId);
    }

    private String actorEsperado() {
        return "paciente-online:" + cuentaUno;
    }

    @Test
    @DisplayName("Se registra una reserva online")
    void seRegistraUnaReservaOnline() throws Exception {
        MvcResult resultado = comoPacienteUno().post("/api/v1/paciente/reservas",
                cuerpoJson(Map.of("prestacionId", consulta, "inicio", hora(10, 0), "profesionalId", draA))).andReturn();

        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(201);
        long turno = id("SELECT id FROM turno WHERE paciente_id = ?", pacienteUno);
        List<Map<String, Object>> registros = auditoria(turno);
        assertThat(registros).hasSize(1);
        assertThat(registros.get(0)).containsEntry("accion", "CREAR").containsEntry("actor", actorEsperado());
        assertThat(registros.toString()).doesNotContain(EMAIL_UNO).doesNotContain(DNI_UNO)
                .doesNotContain(numeroDe(turno));
    }

    @Test
    @DisplayName("Se registra una reprogramación online")
    void seRegistraUnaReprogramacionOnline() throws Exception {
        long original = turnoActivo(pacienteUno, draA, sillon1, 2, 10, 0);

        MvcResult resultado = comoPacienteUno().post("/api/v1/paciente/reservas/" + numeroDe(original) + "/reprogramar",
                cuerpoJson(Map.of("inicio", en(2, 11, 0), "profesionalId", draA))).andReturn();

        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        long nuevo = id("SELECT id FROM turno WHERE paciente_id = ? AND estado = 'RESERVADO'", pacienteUno);
        List<Map<String, Object>> deOriginal = auditoria(original);
        assertThat(deOriginal).hasSize(1);
        assertThat(deOriginal.get(0)).containsEntry("accion", "REPROGRAMAR").containsEntry("actor", actorEsperado())
                .containsEntry("turno_relacionado_id", nuevo);
        List<Map<String, Object>> deNuevo = auditoria(nuevo);
        assertThat(deNuevo).hasSize(1);
        assertThat(deNuevo.get(0)).containsEntry("accion", "CREAR").containsEntry("actor", actorEsperado());
        assertThat(deOriginal.toString() + deNuevo).doesNotContain(EMAIL_UNO).doesNotContain(DNI_UNO)
                .doesNotContain(numeroDe(original)).doesNotContain(numeroDe(nuevo));
    }

    @Test
    @DisplayName("Cancelación online rechazada sin registro")
    void cancelacionRechazadaSinRegistro() throws Exception {
        reloj.fijarEn(1, 11, 0);
        long turno = turnoActivo(pacienteUno, draA, sillon1, 2, 10, 0);

        MvcResult resultado = comoPacienteUno().post("/api/v1/paciente/reservas/" + numeroDe(turno) + "/cancelar", null)
                .andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(409);
        assertThat(auditoria(turno)).isEmpty();
    }

    @Test
    @DisplayName("Se registra una cancelación online con el actor de la cuenta")
    void seRegistraUnaCancelacionOnline() throws Exception {
        long turno = turnoActivo(pacienteUno, draA, sillon1, 2, 10, 0);

        MvcResult resultado = comoPacienteUno().post("/api/v1/paciente/reservas/" + numeroDe(turno) + "/cancelar", null)
                .andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(auditoria(turno)).hasSize(1);
        assertThat(auditoria(turno).get(0)).containsEntry("accion", "CANCELAR").containsEntry("actor", actorEsperado());
    }
}
