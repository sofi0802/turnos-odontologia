package ar.turnos.odontologia.notificaciones.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.notificaciones.CapturaDeEnvios;
import ar.turnos.odontologia.notificaciones.application.NotificacionPort.EnvioNotificacion;
import ar.turnos.odontologia.reserva.IntegracionReservaBase;
import ar.turnos.odontologia.reserva.application.AvisoReservaOnline;
import ar.turnos.odontologia.shared.security.Navegador;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * C-11 tarea 10.3 (acordado con C-10): con el adaptador real, la reserva, la cancelación y la reprogramación online
 * pedidas por la API de C-10 dejan sus avisos. PostgreSQL real, reloj fijo del 2026-12-01 a las 08:00 y los
 * canales capturadores de {@link CapturaDeEnvios}; la tarea de recordatorios está apagada.
 */
@Import(CapturaDeEnvios.class)
@TestPropertySource(properties = {
        "app.notificaciones.activo=false",
        "app.notificaciones.base-url=https://turnos.example.test",
        "app.consultorio.nombre=Clínica Ejemplo",
        "app.consultorio.direccion=Calle Ficticia 123"})
class AvisoReservaOnlineNotificacionesIntegrationTest extends IntegracionReservaBase {

    private static final String RESERVAS = "/api/v1/paciente/reservas";
    private static final String BASE = "https://turnos.example.test";
    private static final Pattern LINK = Pattern.compile("https://\\S+");

    @Autowired AvisoReservaOnline aviso;
    @Autowired CapturaDeEnvios.Registro envios;

    @BeforeEach
    void franjasYCapturaDeEnvios() {
        envios.reiniciar();
        for (int dia = 1; dia <= 5; dia++) {
            franja(draA, dia, "08:00", "13:00");
            franja(drB, dia, "10:00", "18:00");
        }
        // "Paciente Uno" con consentimiento y email; "Paciente Dos" queda sin consentimiento
        jdbc.update("UPDATE paciente SET consentimiento_comunicaciones = TRUE, consentimiento_fecha = now() WHERE id = ?",
                pacienteUno);
    }

    private MvcResult reservar(Navegador navegador, String inicio) throws Exception {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("prestacionId", consulta);
        pedido.put("inicio", inicio);
        pedido.put("profesionalId", draA);
        return navegador.post(RESERVAS, cuerpoJson(pedido)).andReturn();
    }

    private MvcResult cancelar(Navegador navegador, String numero) throws Exception {
        return navegador.post(RESERVAS + "/" + numero + "/cancelar", null).andReturn();
    }

    private MvcResult reprogramar(Navegador navegador, String numero, String inicio) throws Exception {
        return navegador.post(RESERVAS + "/" + numero + "/reprogramar", cuerpoJson(Map.of("inicio", inicio)))
                .andReturn();
    }

    private List<Map<String, Object>> notificaciones() {
        return jdbc.queryForList("SELECT turno_id, canal, tipo, estado, token_confirmacion, proximo_intento"
                + " FROM notificacion ORDER BY id");
    }

    private static List<String> links(String cuerpo) {
        Matcher m = LINK.matcher(cuerpo);
        List<String> encontrados = new java.util.ArrayList<>();
        while (m.find()) {
            encontrados.add(m.group().replaceAll("[.,]$", ""));
        }
        return encontrados;
    }

    @Test
    void conElAdaptadorRealSpringNoUsaElAvisoInactivo() {
        assertThat(aviso).isInstanceOf(AvisoReservaOnlineNotificaciones.class);
    }

    @Test
    void laReservaOnlineDejaUnAvisoDeConfirmacionSinToken() throws Exception {
        // GIVEN "Paciente Uno" con consentimiento y email que reserva online el 2026-12-01 a las 10:00
        MvcResult resultado = reservar(comoPacienteUno(), hora(10, 0));

        // THEN la reserva queda confirmada y se envía un aviso por email con fecha, hora, consultorio y los links
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(201);
        String numero = cuerpo(resultado).get("numeroReserva").asText();
        assertThat(envios.email.enviados).hasSize(1);
        EnvioNotificacion envio = envios.email.enviados.get(0);
        assertThat(envio.destino()).isEqualTo(EMAIL_UNO);
        assertThat(envio.cuerpo()).contains("01/12/2026", "10:00", "Clínica Ejemplo", "Calle Ficticia 123",
                BASE + "/mis-turnos/cancelar", BASE + "/mis-turnos/reprogramar");
        // AND sin número de reserva, DNI, nombres, prestación ni link de confirmar
        assertThat(envio.cuerpo()).doesNotContain(numero, DNI_UNO, "Paciente", "Ana", "Consulta ",
                "/confirmar/");
        List<Map<String, Object>> filas = notificaciones();
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0)).containsEntry("tipo", "CONFIRMACION").containsEntry("estado", "ENVIADA")
                .containsEntry("canal", "EMAIL").containsEntry("token_confirmacion", null);
    }

    @Test
    void laReservaOnlineSinConsentimientoNoEnviaNada() throws Exception {
        // "Paciente Dos" no dio consentimiento
        MvcResult resultado = reservar(comoPacienteDos(), hora(10, 0));

        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(201);
        assertThat(envios.totalEnviados()).isZero();
        assertThat(notificaciones()).isEmpty();
    }

    @Test
    void laCancelacionOnlineAvisaConElTurnoYaCanceladoYSinLinks() throws Exception {
        // GIVEN un turno de "Paciente Uno" el 2026-12-03 a las 10:00, con consentimiento y WhatsApp
        jdbc.update("UPDATE paciente SET tiene_whatsapp = TRUE, telefono = '+54 11 5555-0001' WHERE id = ?", pacienteUno);
        long turno = turnoActivo(pacienteUno, draA, sillon1, 3, 10, 0);

        // WHEN lo cancela online
        MvcResult resultado = cancelar(comoPacienteUno(), numeroDe(turno));

        // THEN queda CANCELADO y se avisa por WhatsApp, con el turno ya cancelado, sin links
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        assertThat(estadoDe(turno)).isEqualTo("CANCELADO");
        assertThat(envios.whatsapp.enviados).hasSize(1);
        assertThat(envios.whatsapp.enviados.get(0).cuerpo()).contains("cancelado", "03/12/2026", "10:00",
                "Clínica Ejemplo", "Calle Ficticia 123").doesNotContain("http", numeroDe(turno));
        assertThat(notificaciones()).hasSize(1);
        assertThat(notificaciones().get(0)).containsEntry("tipo", "CANCELACION").containsEntry("estado", "ENVIADA")
                .containsEntry("token_confirmacion", null);
    }

    @Test
    void laReprogramacionOnlineAvisaSoloDelTurnoNuevo() throws Exception {
        // GIVEN un turno de "Paciente Uno" el 2026-12-03 a las 10:00
        long original = turnoActivo(pacienteUno, draA, sillon1, 3, 10, 0);

        // WHEN lo reprograma al 2026-12-04 a las 11:00
        MvcResult resultado = reprogramar(comoPacienteUno(), numeroDe(original), en(4, 11, 0));

        // THEN se avisa del turno nuevo, con la fecha y hora nuevas y sin mencionar la anterior
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        JsonNode nuevo = cuerpo(resultado);
        assertThat(envios.email.enviados).hasSize(1);
        assertThat(envios.email.enviados.get(0).cuerpo()).contains("04/12/2026", "11:00",
                BASE + "/mis-turnos/cancelar", BASE + "/mis-turnos/reprogramar")
                .doesNotContain("03/12/2026", nuevo.get("numeroReserva").asText(), numeroDe(original));
        List<Map<String, Object>> filas = notificaciones();
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0)).containsEntry("tipo", "CONFIRMACION");
        assertThat(((Number) filas.get(0).get("turno_id")).longValue()).isNotEqualTo(original);
    }

    @Test
    void unFalloDelProveedorDejaLaReservaConfirmadaYLaNotificacionFallida() throws Exception {
        // GIVEN un proveedor de email que falla
        envios.email.falla = true;

        // WHEN "Paciente Uno" reserva online
        MvcResult resultado = reservar(comoPacienteUno(), hora(10, 0));

        // THEN la reserva queda confirmada y la notificación queda FALLIDA con un reintento programado
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(201);
        assertThat(contarTurnosDe(pacienteUno)).isEqualTo(1);
        List<Map<String, Object>> filas = notificaciones();
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0)).containsEntry("estado", "FALLIDA").containsEntry("tipo", "CONFIRMACION");
        assertThat(filas.get(0).get("proximo_intento")).isNotNull();
    }

    @Test
    @DisplayName("El link de cancelar no incluye credenciales")
    void elLinkDeCancelarNoIncluyeCredenciales() throws Exception {
        // GIVEN un aviso de reserva online con links de cancelar y reprogramar
        MvcResult resultado = reservar(comoPacienteUno(), hora(10, 0));
        String numero = cuerpo(resultado).get("numeroReserva").asText();
        long turnoId = id("SELECT id FROM turno WHERE paciente_id = ?", pacienteUno);

        // WHEN se arma el mensaje
        String cuerpo = envios.email.enviados.get(0).cuerpo();

        // THEN los links empiezan con la dirección base configurada
        List<String> links = links(cuerpo);
        assertThat(links).hasSize(2).allSatisfy(link -> assertThat(link).startsWith(BASE));
        // AND no contienen el número de reserva, el DNI, el email, el teléfono ni el id del turno
        assertThat(links).allSatisfy(link -> assertThat(link).doesNotContain(numero, DNI_UNO, EMAIL_UNO, "5555",
                "?", "=", "@").doesNotContainPattern("\\d"));
        assertThat(String.valueOf(turnoId)).isNotBlank();
        assertThat(links).containsExactly(BASE + "/mis-turnos/cancelar", BASE + "/mis-turnos/reprogramar");
    }
}
