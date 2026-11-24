package ar.turnos.odontologia.coberturas;

import ar.turnos.odontologia.clinica.IntegracionClinicaBase.ConRelojAjustable;
import ar.turnos.odontologia.clinica.IntegracionClinicaBase.RelojAjustable;
import ar.turnos.odontologia.shared.security.IntegracionSeguridadBase;
import ar.turnos.odontologia.shared.security.Navegador;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Base de los tests de integración de C-18: PostgreSQL real, sesión y CSRF de verdad y datos ficticios.
 *
 * <ul>
 *   <li>Usuarios: {@code admin}, {@code recepcion}, {@code ana.ejemplo} (odontóloga), {@code paciente.uno} y
 *       {@code paciente.dos}.</li>
 *   <li>Pacientes: "Paciente Uno" (DNI {@code 99000001}) y "Paciente Dos" (DNI {@code 99000002}), cada uno con su
 *       cuenta.</li>
 *   <li>Reloj ajustable: arranca el 2026-11-24 a las 12:00 en Buenos Aires.</li>
 * </ul>
 * Las tablas de coberturas se vacían antes de cada test; las de pacientes y prestaciones las vacía la base de
 * seguridad con {@code CASCADE}.
 */
@Tag("integration")
@Import(ConRelojAjustable.class)
public abstract class IntegracionCoberturasBase extends IntegracionSeguridadBase {

    protected static final String OBRAS_SOCIALES = "/api/v1/obras-sociales";
    protected static final String NOMENCLADOR = "/api/v1/nomenclador";
    protected static final String COBERTURA_PROPIA = "/api/v1/paciente/cobertura";

    @Autowired
    protected RelojAjustable reloj;

    protected final String clave = contrasenaFicticia();
    protected long pacienteUno;
    protected long pacienteDos;
    protected long profesionalAna;

    @BeforeEach
    void cargarDatosDeCoberturas() {
        jdbc.execute("TRUNCATE afiliacion_paciente, nomenclador, plan, obra_social RESTART IDENTITY CASCADE");
        reloj.ahoraEs(Instant.parse("2026-11-24T15:00:00Z"));
        pacienteUno = crearPacienteSinCuenta("Paciente", "Uno", "99000001", "paciente.uno@example.com");
        pacienteDos = crearPacienteSinCuenta("Paciente", "Dos", "99000002", "paciente.dos@example.com");
        crearPersonal("admin", "ADMIN", clave);
        crearPersonal("recepcion", "RECEPCIONISTA", clave);
        profesionalAna = jdbc.queryForObject(
                "INSERT INTO profesional(nombre, apellido, matricula) VALUES ('Ana','Ejemplo','MP-0001') RETURNING id",
                Long.class);
        jdbc.update("INSERT INTO usuario(username, password_hash, rol, profesional_id) VALUES (?,?,'ODONTOLOGO',?)",
                "ana.ejemplo", passwordEncoder.encode(clave), profesionalAna);
        cuenta("paciente.uno", pacienteUno);
        cuenta("paciente.dos", pacienteDos);
    }

    private void cuenta(String username, long paciente) {
        jdbc.update("INSERT INTO usuario(username, password_hash, rol, paciente_id) VALUES (?,?,'PACIENTE',?)",
                username, passwordEncoder.encode(clave), paciente);
    }

    protected Navegador como(String username) throws Exception {
        return iniciarSesion(username, clave);
    }

    // --- Datos del catálogo por JDBC (el escenario parte de un estado sin pasar por la API) ---

    protected long obraSocial(String nombre, String sigla, String tipo) {
        return jdbc.queryForObject("INSERT INTO obra_social(nombre, sigla, tipo) VALUES (?,?,?) RETURNING id",
                Long.class, nombre, sigla, tipo);
    }

    protected long plan(long obraSocial, String nombre) {
        return jdbc.queryForObject("INSERT INTO plan(obra_social_id, nombre) VALUES (?,?) RETURNING id", Long.class,
                obraSocial, nombre);
    }

    protected long codigo(String codigo, String descripcion, Long prestacion) {
        return jdbc.queryForObject("INSERT INTO nomenclador(codigo, descripcion, prestacion_id) VALUES (?,?,?) "
                + "RETURNING id", Long.class, codigo, descripcion, prestacion);
    }

    protected long prestacion(String nombre) {
        return jdbc.queryForObject("INSERT INTO prestacion(nombre, duracion_minutos) VALUES (?, 30) RETURNING id",
                Long.class, nombre);
    }

    /** Inserta una afiliación por JDBC. {@code hasta} nulo es abierta. */
    protected long afiliacion(long paciente, long plan, String numero, String desde, String hasta) {
        return jdbc.queryForObject("INSERT INTO afiliacion_paciente(paciente_id, plan_id, obra_social_id, "
                + "numero_afiliado, vigente_desde, vigente_hasta) SELECT ?, p.id, p.obra_social_id, ?, "
                + "CAST(? AS date), CAST(? AS date) FROM plan p WHERE p.id = ? RETURNING id", Long.class,
                paciente, numero, desde, hasta, plan);
    }

    protected int cantidad(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class);
    }

    // --- Pedidos ---

    protected static String afiliaciones(long paciente) {
        return "/api/v1/pacientes/" + paciente + "/afiliaciones";
    }

    protected static String cobertura(long paciente) {
        return "/api/v1/pacientes/" + paciente + "/cobertura";
    }

    protected MvcResult get(Navegador navegador, String url) throws Exception {
        return navegador.get(url).andReturn();
    }

    protected MvcResult post(Navegador navegador, String url, Object cuerpo) throws Exception {
        return navegador.post(url, cuerpo == null ? null : json.writeValueAsString(cuerpo)).andReturn();
    }

    protected MvcResult put(Navegador navegador, String url, Object cuerpo) throws Exception {
        return navegador.enviar(MockMvcRequestBuilders.put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo))
                .header(Navegador.ENCABEZADO_CSRF, navegador.tokenCsrf())).andReturn();
    }

    protected MvcResult delete(Navegador navegador, String url) throws Exception {
        return navegador.enviar(MockMvcRequestBuilders.delete(url)
                .header(Navegador.ENCABEZADO_CSRF, navegador.tokenCsrf())).andReturn();
    }

    /** Cuerpo de alta o modificación de una obra social. */
    protected static Map<String, Object> obraSocialSolicitud(String nombre, String sigla, String tipo) {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("nombre", nombre);
        pedido.put("sigla", sigla);
        pedido.put("tipo", tipo);
        return pedido;
    }

    protected static Map<String, Object> codigoSolicitud(String codigo, String descripcion, Long prestacionId) {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("codigo", codigo);
        pedido.put("descripcion", descripcion);
        pedido.put("prestacionId", prestacionId);
        return pedido;
    }

    protected static Map<String, Object> afiliacionSolicitud(long planId, String numero, String desde, String hasta) {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("planId", planId);
        pedido.put("numeroAfiliado", numero);
        pedido.put("vigenteDesde", desde);
        pedido.put("vigenteHasta", hasta);
        return pedido;
    }

    // --- Respuestas ---

    protected JsonNode cuerpo(MvcResult resultado) throws Exception {
        return json.readTree(texto(resultado));
    }

    protected static String texto(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    protected static int status(MvcResult resultado) {
        return resultado.getResponse().getStatus();
    }

    protected String codigoDeError(MvcResult resultado) throws Exception {
        return cuerpo(resultado).path("codigo").asText();
    }
}
