package ar.turnos.odontologia.notificaciones.api;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.notificaciones.IntegracionNotificacionesBase;
import ar.turnos.odontologia.shared.security.Navegador;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * C-17: escenarios del spec {@code consentimiento-comunicaciones} de punta a punta, con PostgreSQL real, sesión y
 * CSRF como el frontend y el reloj fijo del 2026-12-01 a las 08:00. "Paciente Uno" tiene cuenta y "Paciente Dos" no.
 */
class ConsentimientoApiIntegrationTest extends IntegracionNotificacionesBase {

    private static final String DEL_PACIENTE = "/api/v1/paciente/consentimiento-comunicaciones";
    private static final String PACIENTE_UNO = "paciente.uno@example.com";

    private static String delPersonal(long paciente) {
        return "/api/v1/pacientes/" + paciente + "/consentimiento-comunicaciones";
    }

    private MvcResult put(Navegador navegador, String url, String cuerpo) throws Exception {
        return navegador.enviar(MockMvcRequestBuilders.put(url).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                .header(Navegador.ENCABEZADO_CSRF, navegador.tokenCsrf() == null ? "" : navegador.tokenCsrf()))
                .andReturn();
    }

    private long eventos(long paciente) {
        return jdbc.queryForObject("SELECT count(*) FROM consentimiento_comunicacion_evento WHERE paciente_id = ?",
                Long.class, paciente);
    }

    @Test
    @DisplayName("Paciente nuevo sin consentimiento")
    void pacienteNuevoSinConsentimiento() throws Exception {
        MvcResult resultado = como("recepcion").get(delPersonal(pacienteDos)).andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        JsonNode cuerpo = cuerpo(resultado);
        assertThat(cuerpo.get("otorgado").asBoolean()).isFalse();
        assertThat(cuerpo.get("actualizadoEn").isNull()).isTrue();
        assertThat(cuerpo.get("historial")).isEmpty();
    }

    @Test
    @DisplayName("El paciente otorga su consentimiento")
    void elPacienteOtorgaSuConsentimiento() throws Exception {
        Navegador paciente = como(PACIENTE_UNO);

        MvcResult resultado = put(paciente, DEL_PACIENTE, "{\"otorgado\":true}");

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        JsonNode cuerpo = cuerpo(resultado);
        assertThat(cuerpo.get("otorgado").asBoolean()).isTrue();
        assertThat(cuerpo.get("actualizadoEn").asText()).startsWith("2026-12-01T11:00");
        assertThat(jdbc.queryForObject("SELECT origen FROM consentimiento_comunicacion_evento WHERE paciente_id = ?",
                String.class, pacienteUno)).isEqualTo("PACIENTE");
        assertThat(jdbc.queryForObject("SELECT consentimiento_comunicaciones FROM paciente WHERE id = ?",
                Boolean.class, pacienteUno)).isTrue();
        // Lo que ve el paciente después.
        JsonNode consulta = cuerpo(paciente.get(DEL_PACIENTE).andReturn());
        assertThat(consulta.get("otorgado").asBoolean()).isTrue();
        assertThat(consulta.has("historial")).isFalse();
    }

    @Test
    @DisplayName("El personal no usa el recurso del paciente")
    void elPersonalNoUsaElRecursoDelPaciente() throws Exception {
        assertThat(como("recepcion").get(DEL_PACIENTE).andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("La recepción registra un consentimiento telefónico")
    void laRecepcionRegistraUnConsentimientoTelefonico() throws Exception {
        MvcResult resultado = put(como("recepcion"), delPersonal(pacienteDos),
                "{\"otorgado\":true,\"medio\":\"TELEFONO\"}");

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        JsonNode cuerpo = cuerpo(resultado);
        assertThat(cuerpo.get("otorgado").asBoolean()).isTrue();
        JsonNode evento = cuerpo.get("historial").get(0);
        assertThat(evento.get("origen").asText()).isEqualTo("RECEPCION");
        assertThat(evento.get("medio").asText()).isEqualTo("TELEFONO");
        assertThat(evento.get("registradoPor").asText()).isEqualTo("recepcion");
    }

    @Test
    void elAdministradorTambienRegistraYRevoca() throws Exception {
        Navegador admin = como("admin");
        put(admin, delPersonal(pacienteDos), "{\"otorgado\":true,\"medio\":\"PRESENCIAL\"}");

        JsonNode cuerpo = cuerpo(put(admin, delPersonal(pacienteDos), "{\"otorgado\":false,\"medio\":\"TELEFONO\"}"));

        assertThat(cuerpo.get("otorgado").asBoolean()).isFalse();
        assertThat(cuerpo.get("historial")).hasSize(2);
        assertThat(cuerpo.get("historial").get(1).get("registradoPor").asText()).isEqualTo("admin");
    }

    @Test
    @DisplayName("Falta el medio")
    void faltaElMedio() throws Exception {
        MvcResult resultado = put(como("recepcion"), delPersonal(pacienteDos), "{\"otorgado\":true}");

        assertThat(resultado.getResponse().getStatus()).isEqualTo(422);
        assertThat(cuerpo(resultado).get("codigo").asText()).isEqualTo("DATOS_INVALIDOS");
        assertThat(eventos(pacienteDos)).isZero();
        assertThat(jdbc.queryForObject("SELECT consentimiento_comunicaciones FROM paciente WHERE id = ?",
                Boolean.class, pacienteDos)).isFalse();
    }

    @Test
    void faltaOtorgadoOElMedioEsDesconocido() throws Exception {
        Navegador recepcion = como("recepcion");

        assertThat(put(recepcion, delPersonal(pacienteDos), "{\"medio\":\"TELEFONO\"}").getResponse().getStatus())
                .isEqualTo(422);
        assertThat(put(recepcion, delPersonal(pacienteDos), "{\"otorgado\":true,\"medio\":\"EMAIL\"}")
                .getResponse().getStatus()).isEqualTo(422);
        assertThat(put(como(PACIENTE_UNO), DEL_PACIENTE, "{}").getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("Paciente inexistente")
    void pacienteInexistente() throws Exception {
        MvcResult resultado = como("recepcion").get(delPersonal(999_999)).andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(404);
        assertThat(cuerpo(resultado).get("codigo").asText()).isEqualTo("RECURSO_NO_ENCONTRADO");
    }

    @Test
    @DisplayName("El odontólogo no gestiona el consentimiento")
    void elOdontologoNoGestionaElConsentimiento() throws Exception {
        Navegador odontologo = como("ana.ejemplo");

        assertThat(odontologo.get(delPersonal(pacienteUno)).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(put(odontologo, delPersonal(pacienteUno), "{\"otorgado\":true,\"medio\":\"TELEFONO\"}")
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(eventos(pacienteUno)).isZero();
    }

    @Test
    @DisplayName("Un paciente no consulta el de otro")
    void unPacienteNoConsultaElDeOtro() throws Exception {
        assertThat(como(PACIENTE_UNO).get(delPersonal(pacienteDos)).andReturn().getResponse().getStatus())
                .isEqualTo(403);
    }

    @Test
    void sinSesionLaRespuestaEs401() throws Exception {
        assertThat(navegador().get(delPersonal(pacienteUno)).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(navegador().get(DEL_PACIENTE).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void elPutSinTokenCsrfSeRechaza() throws Exception {
        Navegador recepcion = como("recepcion");

        MvcResult resultado = recepcion.enviar(MockMvcRequestBuilders.put(delPersonal(pacienteDos))
                .contentType(MediaType.APPLICATION_JSON).content("{\"otorgado\":true,\"medio\":\"TELEFONO\"}"))
                .andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(403);
        assertThat(eventos(pacienteDos)).isZero();
    }

    @Test
    @DisplayName("La respuesta del personal no trae contacto")
    void laRespuestaDelPersonalNoTraeContacto() throws Exception {
        put(como(PACIENTE_UNO), DEL_PACIENTE, "{\"otorgado\":true}");

        String respuesta = como("recepcion").get(delPersonal(pacienteUno)).andReturn().getResponse()
                .getContentAsString();

        assertThat(respuesta).doesNotContain("paciente.uno@example.com", DNI_PACIENTE_UNO, "example.com");
    }
}
