package ar.turnos.odontologia.clinica;

import ar.turnos.odontologia.shared.security.IntegracionSeguridadBase;
import ar.turnos.odontologia.shared.security.Navegador;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Base de los tests de integración de C-13 (design D10): PostgreSQL real, sesión y CSRF de verdad, y datos ficticios.
 *
 * <ul>
 *   <li>Profesionales: la Dra. Ana Ejemplo y el Dr. Bruno Prueba, con franjas de 00:00 a 23:59 todos los días
 *       (C-07), así los escenarios de la ausencia pueden volver a dar el horario.</li>
 *   <li>Usuarios: {@code admin}, {@code recepcion}, {@code ana.ejemplo} y {@code bruno.prueba}.</li>
 *   <li>Pacientes: "Paciente Uno" (DNI {@code 99000001}) y "Paciente Dos".</li>
 *   <li>Reloj ajustable: arranca el 2026-12-01 (martes) a las 08:00 en Buenos Aires y cada escenario lo mueve.</li>
 * </ul>
 */
@Tag("integration")
@Import(IntegracionClinicaBase.ConRelojAjustable.class)
public abstract class IntegracionClinicaBase extends IntegracionSeguridadBase {

    protected static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    protected static final String DNI_PACIENTE_UNO = "99000001";

    @Autowired
    protected RelojAjustable reloj;

    protected final String clave = contrasenaFicticia();
    protected long draA;
    protected long drB;
    protected long sillon1;
    protected long consulta;
    protected long pacienteUno;
    protected long pacienteDos;

    @BeforeEach
    void cargarDatosClinicos() {
        reloj.ahoraEs(diciembre(1, 8, 0));
        draA = id("INSERT INTO profesional(nombre, apellido, matricula) VALUES ('Ana','Ejemplo','MP-0001') RETURNING id");
        drB = id("INSERT INTO profesional(nombre, apellido, matricula) VALUES ('Bruno','Prueba','MP-0002') RETURNING id");
        sillon1 = id("INSERT INTO sillon(nombre) VALUES ('Sillón 1') RETURNING id");
        consulta = id("INSERT INTO prestacion(nombre, duracion_minutos) VALUES ('Consulta', 30) RETURNING id");
        pacienteUno = crearPacienteSinCuenta("Paciente", "Uno", DNI_PACIENTE_UNO, "paciente.uno@example.com");
        pacienteDos = crearPacienteSinCuenta("Paciente", "Dos", "99000002", "paciente.dos@example.com");
        crearPersonal("admin", "ADMIN", clave);
        crearPersonal("recepcion", "RECEPCIONISTA", clave);
        odontologo("ana.ejemplo", draA);
        odontologo("bruno.prueba", drB);
        jdbc.update("INSERT INTO horario_trabajo(profesional_id, dia_semana, hora_desde, hora_hasta) "
                + "SELECT p.id, d, TIME '00:00', TIME '23:59' FROM profesional p, generate_series(1, 7) d");
    }

    private void odontologo(String username, long profesional) {
        jdbc.update("INSERT INTO usuario(username, password_hash, rol, profesional_id) VALUES (?,?,'ODONTOLOGO',?)",
                username, passwordEncoder.encode(clave), profesional);
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    protected Navegador como(String username) throws Exception {
        return iniciarSesion(username, clave);
    }

    protected static Instant diciembre(int dia, int hora, int minuto) {
        return LocalDateTime.of(2026, 12, dia, hora, minuto).atZone(BUENOS_AIRES).toInstant();
    }

    /** El instante en ISO-8601 con la zona de Buenos Aires, como lo devuelve la API. */
    protected static String iso(Instant instante) {
        return instante.atZone(BUENOS_AIRES).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
    }

    /** Inserta un turno de "Consulta" de 30 minutos (margen de 10) por JDBC, aunque quede en el pasado. */
    protected long turno(long paciente, long profesional, Instant inicio, String estado) {
        Instant fin = inicio.plusSeconds(30 * 60);
        return jdbc.queryForObject("INSERT INTO turno(profesional_id, sillon_id, prestacion_id, paciente_id, inicio,"
                        + " fin, fin_con_margen, estado) VALUES (?,?,?,?,?,?,?,?) RETURNING id", Long.class,
                profesional, sillon1, consulta, paciente, Timestamp.from(inicio), Timestamp.from(fin),
                Timestamp.from(fin.plusSeconds(10 * 60)), estado);
    }

    protected String estadoDelTurno(long turnoId) {
        return jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, turnoId);
    }

    protected static String ficha(long paciente) {
        return "/api/v1/pacientes/" + paciente + "/ficha";
    }

    protected static String odontograma(long paciente) {
        return "/api/v1/pacientes/" + paciente + "/odontograma";
    }

    protected static String ausencias(long paciente) {
        return "/api/v1/pacientes/" + paciente + "/ausencias";
    }

    protected MvcResult put(Navegador navegador, String url, Object cuerpo) throws Exception {
        return navegador.enviar(MockMvcRequestBuilders.put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo))
                .header(Navegador.ENCABEZADO_CSRF, navegador.tokenCsrf())).andReturn();
    }

    protected MvcResult guardarFicha(Navegador navegador, long paciente, String antecedentes, String alergias,
                                     String medicacion) throws Exception {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("antecedentes", antecedentes);
        pedido.put("alergias", alergias);
        pedido.put("medicacion", medicacion);
        return put(navegador, ficha(paciente), pedido);
    }

    protected static Map<String, Object> cambio(int pieza, String cara, String estado) {
        Map<String, Object> cambio = new LinkedHashMap<>();
        cambio.put("pieza", pieza);
        cambio.put("cara", cara);
        cambio.put("estado", estado);
        return cambio;
    }

    @SafeVarargs
    protected final MvcResult registrarOdontograma(Navegador navegador, long paciente,
                                                   Map<String, Object>... cambios) throws Exception {
        return put(navegador, odontograma(paciente), Map.of("cambios", java.util.List.of(cambios)));
    }

    protected MvcResult marcarAusencia(Navegador navegador, long paciente, long turnoId) throws Exception {
        return navegador.post(ausencias(paciente), json.writeValueAsString(Map.of("turnoId", turnoId))).andReturn();
    }

    protected JsonNode cuerpo(MvcResult resultado) throws Exception {
        return json.readTree(texto(resultado));
    }

    protected static String texto(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    protected static int status(MvcResult resultado) {
        return resultado.getResponse().getStatus();
    }

    protected String codigo(MvcResult resultado) throws Exception {
        return cuerpo(resultado).path("codigo").asText();
    }

    /** Reloj de los tests: un instante fijo que cada escenario puede mover. */
    public static class RelojAjustable extends Clock {

        private volatile Instant ahora = Instant.EPOCH;

        public void ahoraEs(Instant instante) {
            this.ahora = instante;
        }

        @Override
        public ZoneId getZone() {
            return BUENOS_AIRES;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return Clock.fixed(ahora, zona);
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    @TestConfiguration
    public static class ConRelojAjustable {
        @Bean
        @Primary
        RelojAjustable relojAjustable() {
            return new RelojAjustable();
        }
    }
}
