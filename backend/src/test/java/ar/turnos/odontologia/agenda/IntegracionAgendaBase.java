package ar.turnos.odontologia.agenda;

import ar.turnos.odontologia.shared.security.IntegracionSeguridadBase;
import ar.turnos.odontologia.shared.security.Navegador;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Base de los tests de integración de la agenda (C-06): PostgreSQL real, MockMvc con sesión y CSRF como el
 * frontend (hereda de la base de C-05 sin modificarla) y datos ficticios. El momento actual es el 2026-12-01 a
 * las 08:00 en Buenos Aires, como en los specs.
 */
@Tag("integration")
@Import(IntegracionAgendaBase.RelojFijo.class)
public abstract class IntegracionAgendaBase extends IntegracionSeguridadBase {

    protected static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    protected static final String TURNOS = "/api/v1/turnos";
    protected static final String DNI_PACIENTE_UNO = "99000001";

    protected final String clave = contrasenaFicticia();
    protected long draA;
    protected long drB;
    protected long sillon1;
    protected long sillon2;
    protected long consulta;
    protected long pacienteUno;
    protected long pacienteDos;

    @BeforeEach
    void cargarDatosFicticios() {
        jdbc.execute("TRUNCATE usuario, turno, paciente, prestacion, sillon, profesional RESTART IDENTITY CASCADE");
        draA = id("INSERT INTO profesional(nombre, apellido, matricula) VALUES ('Ana','Ejemplo','MP-0001') RETURNING id");
        drB = id("INSERT INTO profesional(nombre, apellido, matricula) VALUES ('Bruno','Prueba','MP-0002') RETURNING id");
        sillon1 = id("INSERT INTO sillon(nombre) VALUES ('Sillón 1') RETURNING id");
        sillon2 = id("INSERT INTO sillon(nombre) VALUES ('Sillón 2') RETURNING id");
        consulta = id("INSERT INTO prestacion(nombre, duracion_minutos) VALUES ('Consulta', 30) RETURNING id");
        // RN-AG-15: los dos profesionales están habilitados para "Consulta" (change validar-habilitacion-profesional).
        jdbc.update("INSERT INTO profesional_prestacion(profesional_id, prestacion_id) SELECT p.id, ? FROM profesional p",
                consulta);
        pacienteUno = crearPacienteConCuenta("Paciente", "Uno", DNI_PACIENTE_UNO, "paciente.uno@example.com", clave);
        pacienteDos = crearPacienteSinCuenta("Paciente", "Dos", "99000002", "paciente.dos@example.com");

        crearPersonal("admin", "ADMIN", clave);
        crearPersonal("recepcion", "RECEPCIONISTA", clave);
        jdbc.update("INSERT INTO usuario(username, password_hash, rol, profesional_id) VALUES ('ana.ejemplo',?,'ODONTOLOGO',?)",
                passwordEncoder.encode(clave), draA);

        // C-07: los escenarios de C-06 y C-08 suponen profesionales que atienden todo el día (design D11).
        jdbc.update("INSERT INTO horario_trabajo(profesional_id, dia_semana, hora_desde, hora_hasta) "
                + "SELECT p.id, d, TIME '00:00', TIME '23:59' FROM profesional p, generate_series(1, 7) d");
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    /** Hora del 2026-12-01 en ISO-8601 con la zona de Buenos Aires, como la manda el frontend. */
    protected static String hora(int hora, int minuto) {
        // formato explícito: OffsetDateTime.toString() omite los segundos cuando valen cero
        return LocalDateTime.of(2026, 12, 1, hora, minuto).atZone(BUENOS_AIRES)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
    }

    protected Navegador como(String username) throws Exception {
        return iniciarSesion(username, clave);
    }

    protected MvcResult crearTurno(Navegador navegador, long paciente, long profesional, long sillon, String inicio)
            throws Exception {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("pacienteId", paciente);
        pedido.put("profesionalId", profesional);
        pedido.put("sillonId", sillon);
        pedido.put("prestacionId", consulta);
        pedido.put("inicio", inicio);
        return navegador.post(TURNOS, json.writeValueAsString(pedido)).andReturn();
    }

    /** Crea un turno con recepción y devuelve el cuerpo de la respuesta (falla si no es 201). */
    protected JsonNode turnoCreado(long paciente, long profesional, long sillon, String inicio) throws Exception {
        MvcResult resultado = crearTurno(como("recepcion"), paciente, profesional, sillon, inicio);
        if (resultado.getResponse().getStatus() != 201) {
            throw new AssertionError("No se creó el turno: " + resultado.getResponse().getContentAsString());
        }
        return cuerpo(resultado);
    }

    protected MvcResult cancelar(Navegador navegador, long turnoId, String dni, String numeroReserva) throws Exception {
        return navegador.post(TURNOS + "/" + turnoId + "/cancelar",
                json.writeValueAsString(Map.of("dni", dni, "numeroReserva", numeroReserva))).andReturn();
    }

    protected MvcResult reprogramar(Navegador navegador, long turnoId, String numeroReserva, String inicio,
                                    Long profesional, Long sillon) throws Exception {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("dni", DNI_PACIENTE_UNO);
        pedido.put("numeroReserva", numeroReserva);
        pedido.put("inicio", inicio);
        if (profesional != null) {
            pedido.put("profesionalId", profesional);
        }
        if (sillon != null) {
            pedido.put("sillonId", sillon);
        }
        return navegador.post(TURNOS + "/" + turnoId + "/reprogramar", json.writeValueAsString(pedido)).andReturn();
    }

    protected JsonNode cuerpo(MvcResult resultado) throws Exception {
        return json.readTree(resultado.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    protected String estadoEnLaBase(long turnoId) {
        return jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, turnoId);
    }

    @TestConfiguration
    public static class RelojFijo {
        @Bean
        @Primary
        Clock relojFijo() {
            return Clock.fixed(LocalDateTime.of(2026, 12, 1, 8, 0).atZone(BUENOS_AIRES).toInstant(), BUENOS_AIRES);
        }
    }
}
