package ar.turnos.odontologia.caja;

import ar.turnos.odontologia.caja.application.ServicioCobros;
import ar.turnos.odontologia.clinica.IntegracionClinicaBase.ConRelojAjustable;
import ar.turnos.odontologia.clinica.IntegracionClinicaBase.RelojAjustable;
import ar.turnos.odontologia.shared.security.IntegracionSeguridadBase;
import ar.turnos.odontologia.shared.security.Navegador;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Base de los tests de integración de C-22: PostgreSQL real, sesión y CSRF de verdad y datos ficticios.
 *
 * <ul>
 *   <li>Hoy es el lunes 2026-12-07 a las 12:00 en Buenos Aires.</li>
 *   <li>"Consulta o control" con arancel de $15.000 y el código 01.01, que el "Plan 40" de "OS Ficticia" cubre al
 *       40 %; "Limpieza (profilaxis)" sin arancel; "Extracción simple" con arancel de $30.000 y sin códigos.</li>
 *   <li>"Paciente Uno" (DNI 99000001) afiliado al "Plan 40" desde el 2026-01-01; "Paciente Dos" particular.</li>
 *   <li>Cuentas: {@code admin}, {@code recepcion}, {@code ana.ejemplo} (odontóloga) y {@code paciente.uno@example.com}.</li>
 * </ul>
 */
@Tag("integration")
@Import(ConRelojAjustable.class)
public abstract class IntegracionCajaBase extends IntegracionSeguridadBase {

    protected static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    protected static final String CAJAS = "/api/v1/cajas";

    @Autowired protected RelojAjustable reloj;
    @Autowired protected ServicioCobros servicioCobros;

    protected final String clave = contrasenaFicticia();
    protected long sillon;
    protected long ana;
    protected long consulta;
    protected long limpieza;
    protected long extraccion;
    protected long pacienteUno;
    protected long pacienteDos;
    protected long afiliacionUno;
    private int minutoLibre;

    @BeforeEach
    void cargarDatosDeCaja() {
        jdbc.execute("TRUNCATE cobro, cargo, caja, cobertura_plan, arancel, afiliacion_paciente, nomenclador, plan, "
                + "obra_social RESTART IDENTITY CASCADE");
        reloj.ahoraEs(instante("2026-12-07", "12:00"));
        minutoLibre = 0;
        sillon = id("INSERT INTO sillon(nombre) VALUES ('Sillón 1') RETURNING id");
        ana = id("INSERT INTO profesional(nombre, apellido, matricula, sillon_habitual_id) "
                + "VALUES ('Ana','Ejemplo','MP-0001',?) RETURNING id", sillon);
        consulta = id("INSERT INTO prestacion(nombre, duracion_minutos) VALUES ('Consulta o control', 30) RETURNING id");
        limpieza = id("INSERT INTO prestacion(nombre, duracion_minutos) VALUES ('Limpieza (profilaxis)', 30) RETURNING id");
        extraccion = id("INSERT INTO prestacion(nombre, duracion_minutos) VALUES ('Extracción simple', 30) RETURNING id");
        arancel(consulta, "15000.00");
        arancel(extraccion, "30000.00");
        long obraSocial = id("INSERT INTO obra_social(nombre, sigla, tipo) VALUES ('OS Ficticia','OSF','OBRA_SOCIAL') "
                + "RETURNING id");
        long plan = id("INSERT INTO plan(obra_social_id, nombre) VALUES (?, 'Plan 40') RETURNING id", obraSocial);
        long codigo = id("INSERT INTO nomenclador(codigo, descripcion, prestacion_id) "
                + "VALUES ('01.01', 'Consulta', ?) RETURNING id", consulta);
        jdbc.update("INSERT INTO cobertura_plan(plan_id, nomenclador_id, porcentaje) VALUES (?, ?, 40)", plan, codigo);
        pacienteUno = crearPacienteConCuenta("Paciente", "Uno", "99000001", "paciente.uno@example.com", clave);
        pacienteDos = crearPacienteSinCuenta("Paciente", "Dos", "99000002", "paciente.dos@example.com");
        afiliacionUno = id("INSERT INTO afiliacion_paciente(paciente_id, plan_id, obra_social_id, numero_afiliado, "
                + "vigente_desde) VALUES (?, ?, ?, 'A-0001', DATE '2026-01-01') RETURNING id", pacienteUno, plan,
                obraSocial);
        crearPersonal("admin", "ADMIN", clave);
        crearPersonal("recepcion", "RECEPCIONISTA", clave);
        jdbc.update("INSERT INTO usuario(username, password_hash, rol, profesional_id) VALUES (?,?,'ODONTOLOGO',?)",
                "ana.ejemplo", passwordEncoder.encode(clave), ana);
    }

    protected long id(String sql, Object... argumentos) {
        Long valor = jdbc.queryForObject(sql, Long.class, argumentos);
        return valor == null ? 0 : valor;
    }

    protected void arancel(long prestacion, String precio) {
        jdbc.update("INSERT INTO arancel(prestacion_id, precio, actualizado_en) VALUES (?, ?, now()) "
                + "ON CONFLICT (prestacion_id) DO UPDATE SET precio = EXCLUDED.precio", prestacion, new BigDecimal(precio));
    }

    protected static Instant instante(String fecha, String hora) {
        return LocalDateTime.of(LocalDate.parse(fecha), LocalTime.parse(hora)).atZone(ZONA).toInstant();
    }

    /** Inserta un turno del 2026-12-07 en el estado dado, cada uno en un horario distinto para no chocar. */
    protected long turno(long paciente, long prestacion, String estado) {
        return turnoEn(paciente, prestacion, estado, "2026-12-07");
    }

    protected long turnoEn(long paciente, long prestacion, String estado, String fecha) {
        LocalDateTime inicio = LocalDate.parse(fecha).atTime(8, 0).plusMinutes(40L * minutoLibre++);
        OffsetDateTime desde = inicio.atZone(ZONA).toOffsetDateTime();
        return id("INSERT INTO turno(profesional_id, sillon_id, prestacion_id, paciente_id, inicio, fin, fin_con_margen, "
                        + "estado) VALUES (?,?,?,?,?,?,?,?) RETURNING id", ana, sillon, prestacion, paciente, desde,
                desde.plusMinutes(30), desde.plusMinutes(40), estado);
    }

    /** Un turno atendido de ese paciente con su cargo ya generado; devuelve el id del cargo. */
    protected long cargoAtendido(long paciente, long prestacion) {
        long turno = turno(paciente, prestacion, "ATENDIDO");
        servicioCobros.generarCargos();
        return id("SELECT id FROM cargo WHERE turno_id = ?", turno);
    }

    protected long cajaAbiertaHoy(String monto) {
        return id("INSERT INTO caja(fecha, monto_inicial, abierta_por, abierta_en) VALUES (DATE '2026-12-07', ?, "
                + "'recepcion', ?) RETURNING id", new BigDecimal(monto), instante("2026-12-07", "08:05").atZone(ZONA)
                .toOffsetDateTime());
    }

    protected long cajaCerradaEl(String fecha, String esperado, String contado, String motivo) {
        BigDecimal diferencia = new BigDecimal(contado).subtract(new BigDecimal(esperado));
        return id("INSERT INTO caja(fecha, monto_inicial, abierta_por, abierta_en, cerrada_por, cerrada_en, "
                        + "efectivo_contado, diferencia, motivo_diferencia) VALUES (?, ?, 'recepcion', ?, 'recepcion', ?, ?, ?, ?) "
                        + "RETURNING id", LocalDate.parse(fecha), new BigDecimal(esperado),
                instante(fecha, "08:00").atZone(ZONA).toOffsetDateTime(),
                instante(fecha, "19:00").atZone(ZONA).toOffsetDateTime(), new BigDecimal(contado), diferencia, motivo);
    }

    protected long cobroDirecto(long cargo, long caja, String importe, String medio, boolean anulado) {
        return id("INSERT INTO cobro(cargo_id, caja_id, importe, medio, registrado_por, registrado_en, anulado, "
                        + "anulado_por, anulado_en, motivo_anulacion) VALUES (?,?,?,?, 'recepcion', now(), ?, ?, ?, ?) "
                        + "RETURNING id", cargo, caja, new BigDecimal(importe), medio, anulado,
                anulado ? "admin" : null, anulado ? OffsetDateTime.now() : null, anulado ? "Prueba ficticia" : null);
    }

    protected Navegador como(String usuario) throws Exception {
        return iniciarSesion(usuario, clave);
    }

    protected String cuerpoJson(Map<String, Object> datos) throws Exception {
        return json.writeValueAsString(datos);
    }

    protected JsonNode cuerpo(MvcResult resultado) throws Exception {
        return json.readTree(resultado.getResponse().getContentAsString());
    }
}
