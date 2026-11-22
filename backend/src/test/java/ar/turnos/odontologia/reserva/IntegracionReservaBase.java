package ar.turnos.odontologia.reserva;

import ar.turnos.odontologia.reserva.domain.Desempate;
import ar.turnos.odontologia.shared.security.IntegracionSeguridadBase;
import ar.turnos.odontologia.shared.security.Navegador;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Base de los tests de integración de la reserva online (C-10, design D11): PostgreSQL real, MockMvc con sesión y CSRF
 * como el frontend, datos ficticios, reloj fijo y modificable (por defecto el 2026-12-01 a las 08:00 en Buenos Aires) y
 * un desempate controlable. Hereda de la base de seguridad (C-05) sin modificarla; no usa la base de la agenda.
 *
 * <p>Datos de cada test: Dra. A (sillón habitual: Sillón 1) y Dr. B (sillón habitual: Sillón 2), los dos habilitados
 * para "Consulta" (30 min) y solo el Dr. B para "Tratamiento de conducto" (30 min); "Paciente Uno" y "Paciente Dos" con
 * cuenta. Los profesionales no tienen franjas: cada escenario carga las suyas.
 */
@Tag("integration")
@Import(IntegracionReservaBase.Controlables.class)
public abstract class IntegracionReservaBase extends IntegracionSeguridadBase {

    protected static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    protected static final String DNI_UNO = "99000001";
    protected static final String DNI_DOS = "99000002";
    protected static final String EMAIL_UNO = "paciente.uno@example.com";
    protected static final String EMAIL_DOS = "paciente.dos@example.com";
    private static final LocalDateTime AHORA_POR_DEFECTO = LocalDateTime.of(2026, 12, 1, 8, 0);

    @Autowired protected RelojMutable reloj;
    @Autowired protected DesempateFijo desempate;

    protected final String clave = contrasenaFicticia();
    protected long draA;
    protected long drB;
    protected long sillon1;
    protected long sillon2;
    protected long consulta;
    protected long conducto;
    protected long pacienteUno;
    protected long pacienteDos;
    protected long cuentaUno;

    @BeforeEach
    void cargarDatosFicticios() {
        reloj.fijarEn(AHORA_POR_DEFECTO);
        desempate.primero();
        jdbc.execute("TRUNCATE usuario, turno, paciente, prestacion, sillon, profesional RESTART IDENTITY CASCADE");
        sillon1 = id("INSERT INTO sillon(nombre) VALUES ('Sillón 1') RETURNING id");
        sillon2 = id("INSERT INTO sillon(nombre) VALUES ('Sillón 2') RETURNING id");
        draA = id("INSERT INTO profesional(nombre, apellido, matricula, sillon_habitual_id) "
                + "VALUES ('Ana','Ejemplo','MP-0001',?) RETURNING id", sillon1);
        drB = id("INSERT INTO profesional(nombre, apellido, matricula, sillon_habitual_id) "
                + "VALUES ('Bruno','Prueba','MP-0002',?) RETURNING id", sillon2);
        consulta = id("INSERT INTO prestacion(nombre, duracion_minutos) VALUES ('Consulta', 30) RETURNING id");
        conducto = id("INSERT INTO prestacion(nombre, duracion_minutos) "
                + "VALUES ('Tratamiento de conducto', 30) RETURNING id");
        jdbc.update("INSERT INTO profesional_prestacion(profesional_id, prestacion_id) VALUES (?,?),(?,?),(?,?)",
                draA, consulta, drB, consulta, drB, conducto);
        pacienteUno = crearPacienteConCuenta("Paciente", "Uno", DNI_UNO, EMAIL_UNO, clave);
        pacienteDos = crearPacienteConCuenta("Paciente", "Dos", DNI_DOS, EMAIL_DOS, clave);
        cuentaUno = id("SELECT id FROM usuario WHERE paciente_id = ?", pacienteUno);
        crearPersonal("recepcion", "RECEPCIONISTA", clave);
    }

    protected long id(String sql, Object... argumentos) {
        return jdbc.queryForObject(sql, Long.class, argumentos);
    }

    // ---------- sesiones ----------

    protected Navegador comoPacienteUno() throws Exception {
        return iniciarSesion(EMAIL_UNO, clave);
    }

    protected Navegador comoPacienteDos() throws Exception {
        return iniciarSesion(EMAIL_DOS, clave);
    }

    protected Navegador comoRecepcion() throws Exception {
        return iniciarSesion("recepcion", clave);
    }

    /** Un navegador que ya pidió el token CSRF pero no inició sesión. */
    protected Navegador sinSesion() throws Exception {
        Navegador navegador = navegador();
        navegador.get(SESION);
        return navegador;
    }

    // ---------- tiempo ----------

    protected static final DateTimeFormatter ISO_CON_SEGUNDOS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    /** Fecha y hora de diciembre de 2026 en ISO-8601 con la zona de Buenos Aires, como la manda el frontend. */
    protected static String en(int dia, int hora, int minuto) {
        return LocalDateTime.of(2026, 12, dia, hora, minuto).atZone(BUENOS_AIRES).format(ISO_CON_SEGUNDOS);
    }

    /** Hora del 2026-12-01. */
    protected static String hora(int hora, int minuto) {
        return en(1, hora, minuto);
    }

    protected static String fecha(int dia) {
        return String.format("2026-12-%02d", dia);
    }

    private static Instant instante(int dia, int hora, int minuto) {
        return LocalDateTime.of(2026, 12, dia, hora, minuto).atZone(BUENOS_AIRES).toInstant();
    }

    // ---------- datos ----------

    /** Franja semanal del profesional; {@code diaSemana}: 1 = lunes … 7 = domingo. */
    protected void franja(long profesional, int diaSemana, String desde, String hasta) {
        jdbc.update("INSERT INTO horario_trabajo(profesional_id, dia_semana, hora_desde, hora_hasta) "
                + "VALUES (?,?,?::time,?::time)", profesional, diaSemana, desde, hasta);
    }

    /** Franja del martes 2026-12-01, el día de la mayoría de los escenarios. */
    protected void martes(long profesional, String desde, String hasta) {
        franja(profesional, 2, desde, hasta);
    }

    /** Inserta un turno de 30 minutos directo en la base (con margen de 10 minutos) y devuelve su id. */
    protected long turno(long paciente, long profesional, long sillon, int dia, int hora, int minuto, String estado) {
        Instant inicio = instante(dia, hora, minuto);
        Instant fin = inicio.plusSeconds(30 * 60);
        return id("INSERT INTO turno(profesional_id, sillon_id, prestacion_id, paciente_id, inicio, fin, "
                        + "fin_con_margen, estado) VALUES (?,?,?,?,?,?,?,?) RETURNING id",
                profesional, sillon, consulta, paciente, java.sql.Timestamp.from(inicio),
                java.sql.Timestamp.from(fin), java.sql.Timestamp.from(fin.plusSeconds(10 * 60)), estado);
    }

    protected long turnoActivo(long paciente, long profesional, long sillon, int dia, int hora, int minuto) {
        return turno(paciente, profesional, sillon, dia, hora, minuto, "RESERVADO");
    }

    protected long turnoCancelado(long paciente, long profesional, long sillon, int dia, int hora, int minuto) {
        return turno(paciente, profesional, sillon, dia, hora, minuto, "CANCELADO");
    }

    protected String numeroDe(long turnoId) {
        return jdbc.queryForObject("SELECT codigo_reserva FROM turno WHERE id = ?", String.class, turnoId);
    }

    protected String estadoDe(long turnoId) {
        return jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, turnoId);
    }

    protected long contarTurnos() {
        return id("SELECT count(*) FROM turno");
    }

    protected long contarTurnosDe(long paciente) {
        return id("SELECT count(*) FROM turno WHERE paciente_id = ?", paciente);
    }

    protected void ausencia(long profesional, String tipo, int dia, int desdeHora, int desdeMin,
                            int hastaHora, int hastaMin, String motivo) {
        jdbc.update("INSERT INTO ausencia_profesional(profesional_id, tipo, inicio, fin, motivo, registrado_por, "
                        + "registrado_en) VALUES (?,?,?,?,?,'admin',now())", profesional, tipo,
                java.sql.Timestamp.from(instante(dia, desdeHora, desdeMin)),
                java.sql.Timestamp.from(instante(dia, hastaHora, hastaMin)), motivo);
    }

    protected void bloqueo(long profesional, int dia, int desdeHora, int desdeMin, int hastaHora, int hastaMin) {
        jdbc.update("INSERT INTO bloqueo_agenda(profesional_id, inicio, fin, motivo, registrado_por, registrado_en) "
                        + "VALUES (?,?,?,?,'admin',now())", profesional,
                java.sql.Timestamp.from(instante(dia, desdeHora, desdeMin)),
                java.sql.Timestamp.from(instante(dia, hastaHora, hastaMin)), "Bloqueo ficticio");
    }

    // ---------- respuestas ----------

    protected JsonNode cuerpo(MvcResult resultado) throws Exception {
        return json.readTree(resultado.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    protected String texto(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Las horas locales ("10:15") de los inicios de una lista de horarios. */
    protected List<String> inicios(JsonNode horarios) {
        List<String> inicios = new ArrayList<>();
        for (JsonNode horario : horarios) {
            inicios.add(java.time.OffsetDateTime.parse(horario.get("inicio").asText()).atZoneSameInstant(BUENOS_AIRES)
                    .toLocalTime().toString());
        }
        return inicios;
    }

    protected List<String> camposConError(JsonNode problema) {
        List<String> campos = new ArrayList<>();
        for (JsonNode error : problema.get("errores")) {
            campos.add(error.get("campo").asText());
        }
        return campos;
    }

    protected String cuerpoJson(Map<String, Object> pedido) throws Exception {
        return json.writeValueAsString(pedido);
    }

    // ---------- reloj y desempate controlables ----------

    /** Reloj que los tests mueven con {@link #fijarEn(LocalDateTime)}; reemplaza al del sistema. */
    public static final class RelojMutable extends Clock {
        private final AtomicReference<Instant> ahora = new AtomicReference<>();

        void fijarEn(LocalDateTime local) {
            ahora.set(local.atZone(BUENOS_AIRES).toInstant());
        }

        /** Mueve el reloj a una hora de diciembre de 2026. */
        public void fijarEn(int dia, int hora, int minuto) {
            fijarEn(LocalDateTime.of(2026, 12, dia, hora, minuto));
        }

        @Override
        public ZoneId getZone() {
            return BUENOS_AIRES;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(ahora.get(), zone);
        }

        @Override
        public Instant instant() {
            return ahora.get();
        }
    }

    /** Desempate que elige siempre el primero o el último de los empatados, como pide el spec. */
    public static final class DesempateFijo implements Desempate {
        private final AtomicReference<Boolean> ultimo = new AtomicReference<>(false);

        public void primero() {
            ultimo.set(false);
        }

        public void ultimo() {
            ultimo.set(true);
        }

        @Override
        public Long elegir(List<Long> empatadosOrdenadosPorId) {
            return ultimo.get() ? empatadosOrdenadosPorId.get(empatadosOrdenadosPorId.size() - 1)
                    : empatadosOrdenadosPorId.get(0);
        }
    }

    @TestConfiguration
    public static class Controlables {
        @Bean
        @Primary
        RelojMutable relojMutable() {
            return new RelojMutable();
        }

        @Bean
        @Primary
        DesempateFijo desempateFijo() {
            return new DesempateFijo();
        }
    }
}
