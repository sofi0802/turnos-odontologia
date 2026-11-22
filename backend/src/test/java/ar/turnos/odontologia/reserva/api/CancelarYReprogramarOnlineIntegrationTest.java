package ar.turnos.odontologia.reserva.api;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.reserva.IntegracionReservaBase;
import ar.turnos.odontologia.shared.security.Navegador;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Un test por escenario de cancelar y reprogramar de {@code specs/turnos-paciente}. */
@Tag("integration")
class CancelarYReprogramarOnlineIntegrationTest extends IntegracionReservaBase {

    private static final String RESERVAS = "/api/v1/paciente/reservas/";

    @BeforeEach
    void franjasDelSpec() {
        // spec turnos-paciente: la Dra. A de lunes a viernes de 08:00 a 13:00 y el Dr. B de 10:00 a 18:00
        for (int dia = 1; dia <= 5; dia++) {
            franja(draA, dia, "08:00", "13:00");
            franja(drB, dia, "10:00", "18:00");
        }
    }

    private MvcResult cancelar(Navegador navegador, String numero) throws Exception {
        return navegador.post(RESERVAS + numero + "/cancelar", null).andReturn();
    }

    private MvcResult reprogramar(Navegador navegador, String numero, String inicio, Long profesional)
            throws Exception {
        Map<String, Object> pedido = new LinkedHashMap<>();
        if (inicio != null) {
            pedido.put("inicio", inicio);
        }
        if (profesional != null) {
            pedido.put("profesionalId", profesional);
        }
        return navegador.post(RESERVAS + numero + "/reprogramar", cuerpoJson(pedido)).andReturn();
    }

    private void esperarRechazo(MvcResult resultado, int estado, String codigo) throws Exception {
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(estado);
        assertThat(cuerpo(resultado).get("codigo").asText()).isEqualTo(codigo);
    }

    private long auditorias(long turnoId, String accion) {
        return id("SELECT count(*) FROM turno_auditoria WHERE turno_id = ? AND accion = ?", turnoId, accion);
    }

    @Nested
    @DisplayName("Cancelar online con el número de reserva")
    class Cancelar {

        @Test
        @DisplayName("Cancelación con anticipación")
        void cancelacionConAnticipacion() throws Exception {
            long turno = turnoActivo(pacienteUno, draA, sillon1, 2, 10, 0);

            MvcResult resultado = cancelar(comoPacienteUno(), numeroDe(turno));

            assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
            assertThat(cuerpo(resultado).get("estado").asText()).isEqualTo("CANCELADO");
            assertThat(estadoDe(turno)).isEqualTo("CANCELADO");
            MvcResult disponibilidad = sinSesion().get("/api/v1/public/disponibilidad?prestacionId=" + consulta
                    + "&fecha=2026-12-02&profesionalId=" + draA).andReturn();
            assertThat(inicios(cuerpo(disponibilidad))).contains("10:00");
        }

        @Test
        @DisplayName("Cancelación exactamente 24 h antes")
        void cancelacionExactamente24Horas() throws Exception {
            reloj.fijarEn(1, 10, 0);
            long turno = turnoActivo(pacienteUno, draA, sillon1, 2, 10, 0);

            MvcResult resultado = cancelar(comoPacienteUno(), numeroDe(turno));

            assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
            assertThat(cuerpo(resultado).get("estado").asText()).isEqualTo("CANCELADO");
        }

        @Test
        @DisplayName("Cancelación a 23 h rechazada")
        void cancelacionA23Horas() throws Exception {
            reloj.fijarEn(1, 11, 0);
            long turno = turnoActivo(pacienteUno, draA, sillon1, 2, 10, 0);

            MvcResult resultado = cancelar(comoPacienteUno(), numeroDe(turno));

            esperarRechazo(resultado, 409, "CANCELACION_FUERA_DE_PLAZO");
            assertThat(cuerpo(resultado).get("detail").asText()).contains("consultorio");
            assertThat(estadoDe(turno)).isEqualTo("RESERVADO");
            assertThat(auditorias(turno, "CANCELAR")).isZero();
        }

        @Test
        @DisplayName("Número de reserva de otro paciente")
        void numeroDeOtroPaciente() throws Exception {
            long ajeno = turnoActivo(pacienteDos, draA, sillon1, 2, 11, 0);

            MvcResult resultado = cancelar(comoPacienteUno(), numeroDe(ajeno));

            esperarRechazo(resultado, 404, "RESERVA_INEXISTENTE");
            assertThat(estadoDe(ajeno)).isEqualTo("RESERVADO");
            assertThat(texto(resultado)).doesNotContain("Paciente Dos").doesNotContain(DNI_DOS)
                    .doesNotContain("Ana Ejemplo").doesNotContain("inicio");
        }

        @Test
        @DisplayName("Cancelar un turno ya cancelado")
        void cancelarUnTurnoYaCancelado() throws Exception {
            long turno = turnoCancelado(pacienteUno, draA, sillon1, 2, 10, 0);

            esperarRechazo(cancelar(comoPacienteUno(), numeroDe(turno)), 409, "TURNO_NO_ACTIVO");
        }

        @Test
        @DisplayName("El personal intenta cancelar por la ruta del paciente")
        void elPersonalIntentaCancelar() throws Exception {
            long turno = turnoActivo(pacienteUno, draA, sillon1, 2, 10, 0);

            esperarRechazo(cancelar(comoRecepcion(), numeroDe(turno)), 403, "ACCESO_DENEGADO");
            assertThat(estadoDe(turno)).isEqualTo("RESERVADO");
        }

        @Test
        @DisplayName("Cancelar sin sesión o sin token CSRF no modifica el turno")
        void cancelarSinSesionOSinCsrf() throws Exception {
            long turno = turnoActivo(pacienteUno, draA, sillon1, 2, 10, 0);

            esperarRechazo(cancelar(sinSesion(), numeroDe(turno)), 401, "NO_AUTENTICADO");
            MvcResult sinCsrf = comoPacienteUno().postSinEncabezadoCsrf(RESERVAS + numeroDe(turno) + "/cancelar", null)
                    .andReturn();
            esperarRechazo(sinCsrf, 403, "CSRF_INVALIDO");
            assertThat(estadoDe(turno)).isEqualTo("RESERVADO");
        }
    }

    @Nested
    @DisplayName("Reprogramar online")
    class Reprogramar {

        @Test
        @DisplayName("Reprogramación a 23 h rechazada")
        void reprogramacionA23Horas() throws Exception {
            reloj.fijarEn(1, 11, 0);
            long turno = turnoActivo(pacienteUno, draA, sillon1, 2, 10, 0);

            MvcResult resultado = reprogramar(comoPacienteUno(), numeroDe(turno), en(3, 10, 0), draA);

            esperarRechazo(resultado, 409, "CANCELACION_FUERA_DE_PLAZO");
            assertThat(estadoDe(turno)).isEqualTo("RESERVADO");
            assertThat(contarTurnos()).isEqualTo(1);
        }

        @Test
        @DisplayName("Número de reserva inexistente")
        void numeroInexistente() throws Exception {
            esperarRechazo(reprogramar(comoPacienteUno(), "ZZZZ-ZZZZ", en(3, 10, 0), null), 404, "RESERVA_INEXISTENTE");
        }

        @Test
        @DisplayName("Número de reserva de otro paciente al reprogramar")
        void numeroDeOtroPacienteAlReprogramar() throws Exception {
            long ajeno = turnoActivo(pacienteDos, draA, sillon1, 2, 11, 0);

            esperarRechazo(reprogramar(comoPacienteUno(), numeroDe(ajeno), en(3, 10, 0), null), 404,
                    "RESERVA_INEXISTENTE");
            assertThat(estadoDe(ajeno)).isEqualTo("RESERVADO");
            assertThat(contarTurnos()).isEqualTo(1);
        }

        @Test
        @DisplayName("Reprogramación a otro horario")
        void reprogramacionAOtroHorario() throws Exception {
            long original = turnoActivo(pacienteUno, draA, sillon1, 2, 10, 0);

            MvcResult resultado = reprogramar(comoPacienteUno(), numeroDe(original), en(2, 11, 0), draA);

            assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
            JsonNode nuevo = cuerpo(resultado);
            assertThat(nuevo.get("inicio").asText()).isEqualTo("2026-12-02T11:00:00-03:00");
            assertThat(nuevo.get("fin").asText()).isEqualTo("2026-12-02T11:30:00-03:00");
            assertThat(nuevo.get("estado").asText()).isEqualTo("RESERVADO");
            assertThat(nuevo.get("numeroReserva").asText()).isNotEqualTo(numeroDe(original));
            assertThat(estadoDe(original)).isEqualTo("CANCELADO");
        }

        @Test
        @DisplayName("Reprogramación sobre el propio horario")
        void reprogramacionSobreElPropioHorario() throws Exception {
            long original = turnoActivo(pacienteUno, draA, sillon1, 2, 10, 0);

            MvcResult resultado = reprogramar(comoPacienteUno(), numeroDe(original), en(2, 10, 15), draA);

            assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
            assertThat(cuerpo(resultado).get("inicio").asText()).isEqualTo("2026-12-02T10:15:00-03:00");
            assertThat(cuerpo(resultado).get("fin").asText()).isEqualTo("2026-12-02T10:45:00-03:00");
        }

        @Test
        @DisplayName("Reprogramación sin preferencia")
        void reprogramacionSinPreferencia() throws Exception {
            long original = turnoActivo(pacienteUno, draA, sillon1, 2, 10, 0);
            turnoActivo(pacienteDos, draA, sillon1, 2, 8, 0);

            MvcResult resultado = reprogramar(comoPacienteUno(), numeroDe(original), en(2, 11, 0), null);

            assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
            assertThat(cuerpo(resultado).get("profesional").get("id").asLong()).isEqualTo(drB);
        }

        @Test
        @DisplayName("Reprogramación a un horario no disponible")
        void reprogramacionAUnHorarioNoDisponible() throws Exception {
            long original = turnoActivo(pacienteUno, draA, sillon1, 2, 10, 0);
            turnoActivo(pacienteDos, draA, sillon1, 2, 11, 0);

            MvcResult resultado = reprogramar(comoPacienteUno(), numeroDe(original), en(2, 11, 0), draA);

            esperarRechazo(resultado, 409, "SIN_DISPONIBILIDAD");
            assertThat(estadoDe(original)).isEqualTo("RESERVADO");
            assertThat(contarTurnos()).isEqualTo(2);
        }

        @Test
        @DisplayName("Reprogramación sin inicio")
        void reprogramacionSinInicio() throws Exception {
            long original = turnoActivo(pacienteUno, draA, sillon1, 2, 10, 0);

            MvcResult resultado = reprogramar(comoPacienteUno(), numeroDe(original), null, draA);

            esperarRechazo(resultado, 422, "DATOS_INVALIDOS");
            assertThat(camposConError(cuerpo(resultado))).contains("inicio");
            assertThat(estadoDe(original)).isEqualTo("RESERVADO");
        }

        @Test
        @DisplayName("Reprogramación con un profesional inexistente")
        void reprogramacionConProfesionalInexistente() throws Exception {
            long original = turnoActivo(pacienteUno, draA, sillon1, 2, 10, 0);

            MvcResult resultado = reprogramar(comoPacienteUno(), numeroDe(original), en(2, 11, 0), 999_999L);

            esperarRechazo(resultado, 422, "DATOS_INVALIDOS");
            assertThat(camposConError(cuerpo(resultado))).contains("profesionalId");
            assertThat(estadoDe(original)).isEqualTo("RESERVADO");
        }

        @Test
        @DisplayName("El personal intenta reprogramar por la ruta del paciente")
        void elPersonalIntentaReprogramar() throws Exception {
            long original = turnoActivo(pacienteUno, draA, sillon1, 2, 10, 0);

            esperarRechazo(reprogramar(comoRecepcion(), numeroDe(original), en(2, 11, 0), draA), 403,
                    "ACCESO_DENEGADO");
            assertThat(estadoDe(original)).isEqualTo("RESERVADO");
        }
    }
}
