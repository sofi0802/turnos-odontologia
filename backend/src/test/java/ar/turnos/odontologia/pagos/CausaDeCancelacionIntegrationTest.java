package ar.turnos.odontologia.pagos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Escenarios del delta de {@code agenda-api}: la causa al cancelar un turno con seña pagada (RN-SE-06). */
class CausaDeCancelacionIntegrationTest extends IntegracionPagosBase {

    private String cancelar(long turno) {
        return "/api/v1/turnos/" + turno + "/cancelar";
    }

    private String pedido(long turno, String causa) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("dni", "99000002");
        cuerpo.put("numeroReserva", jdbc.queryForObject("SELECT codigo_reserva FROM turno WHERE id = ?", String.class,
                turno));
        if (causa != null) {
            cuerpo.put("causa", causa);
        }
        return cuerpoJson(cuerpo);
    }

    private String estadoDelTurno(long turno) {
        return jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, turno);
    }

    @Test
    @DisplayName("Cancelar un turno con seña indicando la causa")
    void cancelarConCausa() throws Exception {
        long turno = turnoEn(pacienteDos, consulta, "RESERVADO", "2026-12-09");
        senaDirecta(turno, pacienteDos, "4500.00", "PAGADA", Instant.now());

        como("recepcion").post(cancelar(turno), pedido(turno, "CONSULTORIO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));

        assertThat(jdbc.queryForObject("SELECT causa FROM turno_auditoria WHERE turno_id = ? AND accion = 'CANCELAR'",
                String.class, turno)).isEqualTo("CONSULTORIO");
    }

    @Test
    @DisplayName("Falta la causa de un turno con seña")
    void faltaLaCausa() throws Exception {
        long turno = turnoEn(pacienteDos, consulta, "RESERVADO", "2026-12-09");
        senaDirecta(turno, pacienteDos, "4500.00", "PAGADA", Instant.now());

        como("recepcion").post(cancelar(turno), pedido(turno, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.errores[0].campo").value("causa"));

        assertThat(estadoDelTurno(turno)).isEqualTo("RESERVADO");
    }

    @Test
    @DisplayName("Turno sin seña sin causa")
    void turnoSinSenaSinCausa() throws Exception {
        long turno = turnoEn(pacienteDos, consulta, "RESERVADO", "2026-12-09");

        como("recepcion").post(cancelar(turno), pedido(turno, null)).andExpect(status().isOk());

        assertThat(estadoDelTurno(turno)).isEqualTo("CANCELADO");
    }

    @Test
    @DisplayName("Causa inválida")
    void causaInvalida() throws Exception {
        long turno = turnoEn(pacienteDos, consulta, "RESERVADO", "2026-12-09");

        como("recepcion").post(cancelar(turno), pedido(turno, "OTRA"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.errores[0].campo").value("causa"));
    }
}
