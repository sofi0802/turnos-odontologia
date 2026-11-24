package ar.turnos.odontologia.reportes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnos.odontologia.reserva.IntegracionReservaBase;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Riesgo de DD-16: los reportes dependen del actor que registra la reserva online. Este test reserva por el endpoint
 * real del paciente y verifica que el turno cuenta como online; si {@code ActorOnline} cambia de formato, falla.
 */
@Tag("integration")
class OrigenOnlineRealIntegrationTest extends IntegracionReservaBase {

    @BeforeEach
    void franjas() {
        for (int dia = 1; dia <= 5; dia++) {
            franja(draA, dia, "08:00", "13:00");
        }
    }

    @Test
    @DisplayName("Un turno reservado por el paciente desde su cuenta cuenta como online")
    void reservaOnlineCuentaComoOnline() throws Exception {
        MvcResult reserva = comoPacienteUno().post("/api/v1/paciente/reservas",
                cuerpoJson(Map.of("prestacionId", consulta, "inicio", hora(10, 0), "profesionalId", draA))).andReturn();
        assertThat(reserva.getResponse().getStatus()).as(texto(reserva)).isEqualTo(201);

        comoRecepcion().get("/api/v1/reportes?desde=" + fecha(1) + "&hasta=" + fecha(1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen.online").value(1))
                .andExpect(jsonPath("$.origen.consultorio").value(0))
                .andExpect(jsonPath("$.origen.sinDato").value(0));
    }
}
