package ar.turnos.odontologia.coberturas;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import ar.turnos.odontologia.shared.security.Navegador;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Base de los tests de integración de C-19 (presupuestos y cobertura): suma a la base de C-18 a {@code bruno.prueba}
 * ("Dr. Bruno Prueba"), a "Paciente Tres", las cuatro prestaciones del seed de la KB y {@link #semilla()}, que carga los
 * aranceles, el nomenclador, la cobertura y las afiliaciones del seed de la KB. Cada test parte de lo mínimo y pide la
 * semilla (o parte de ella) en su GIVEN. Reloj ajustable: 2026-11-24 a las 12:00 en Buenos Aires. Solo datos ficticios.
 */
@Tag("integration")
public abstract class IntegracionPresupuestosBase extends IntegracionCoberturasBase {

    protected static final String ARANCELES = "/api/v1/aranceles";

    protected long pacienteTres;
    protected long profesionalBruno;
    protected long consulta;
    protected long limpieza;
    protected long conducto;
    protected long extraccion;

    protected long obraSocialEjemplo;
    protected long planBase;
    protected long planIntegral;
    protected long prepaga;
    protected long planOro;
    protected long n0101;
    protected long n0201;
    protected long n0301;

    @BeforeEach
    void cargarDatosDePresupuestos() {
        pacienteTres = crearPacienteSinCuenta("Paciente", "Tres", "99000003", "paciente.tres@example.com");
        profesionalBruno = jdbc.queryForObject(
                "INSERT INTO profesional(nombre, apellido, matricula) VALUES ('Bruno','Prueba','MP-0002') RETURNING id",
                Long.class);
        jdbc.update("INSERT INTO usuario(username, password_hash, rol, profesional_id) VALUES (?,?,'ODONTOLOGO',?)",
                "bruno.prueba", passwordEncoder.encode(clave), profesionalBruno);
        consulta = prestacion("Consulta o control");
        limpieza = prestacion("Limpieza (profilaxis)");
        conducto = prestacion("Tratamiento de conducto");
        extraccion = prestacion("Extracción simple");
    }

    /**
     * El seed de la KB: aranceles de las cuatro prestaciones, los códigos {@code N-0101}, {@code N-0201} y
     * {@code N-0301} vinculados a su prestación (el tratamiento de conducto no tiene código), "Plan Base" al 100 % y 50 %,
     * "Plan Oro" al 80 %, "Paciente Uno" con "Plan Base" y "Paciente Tres" con "Plan Oro". "Paciente Dos" es particular.
     */
    protected void semilla() {
        obraSocialEjemplo = obraSocial("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL");
        planBase = plan(obraSocialEjemplo, "Plan Base");
        planIntegral = plan(obraSocialEjemplo, "Plan Integral");
        prepaga = obraSocial("Prepaga Prueba", null, "PREPAGA");
        planOro = plan(prepaga, "Plan Oro");
        n0101 = codigo("N-0101", "Consulta", consulta);
        n0201 = codigo("N-0201", "Limpieza", limpieza);
        n0301 = codigo("N-0301", "Extracción simple", extraccion);
        arancel(consulta, "15000.00");
        arancel(limpieza, "25000.00");
        arancel(conducto, "90000.00");
        arancel(extraccion, "30000.00");
        cobertura(planBase, n0101, 100);
        cobertura(planBase, n0201, 50);
        for (long codigo : new long[] {n0101, n0201, n0301}) {
            cobertura(planOro, codigo, 80);
        }
        afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);
        afiliacion(pacienteTres, planOro, "PP-000003", "2026-01-01", null);
    }

    protected void arancel(long prestacion, String precio) {
        jdbc.update("INSERT INTO arancel(prestacion_id, precio, actualizado_en) VALUES (?, CAST(? AS numeric), now()) "
                + "ON CONFLICT (prestacion_id) DO UPDATE SET precio = EXCLUDED.precio", prestacion, precio);
    }

    protected void cobertura(long plan, long codigo, int porcentaje) {
        jdbc.update("INSERT INTO cobertura_plan(plan_id, nomenclador_id, porcentaje) VALUES (?,?,?) "
                + "ON CONFLICT (plan_id, nomenclador_id) DO UPDATE SET porcentaje = EXCLUDED.porcentaje",
                plan, codigo, porcentaje);
    }

    protected BigDecimal precioGuardado(long prestacion) {
        List<BigDecimal> precios = jdbc.queryForList("SELECT precio FROM arancel WHERE prestacion_id = ?",
                BigDecimal.class, prestacion);
        return precios.isEmpty() ? null : precios.get(0);
    }

    protected Integer porcentajeGuardado(long plan, long codigo) {
        List<Integer> valores = jdbc.queryForList("SELECT porcentaje FROM cobertura_plan WHERE plan_id = ? "
                + "AND nomenclador_id = ?", Integer.class, plan, codigo);
        return valores.isEmpty() ? null : valores.get(0);
    }

    protected void ahoraEs(String instante) {
        reloj.ahoraEs(Instant.parse(instante));
    }

    // --- Pedidos ---

    protected static String arancelUrl(long prestacion) {
        return ARANCELES + "/" + prestacion;
    }

    protected static String coberturaPlanUrl(long obraSocial, long plan) {
        return OBRAS_SOCIALES + "/" + obraSocial + "/planes/" + plan + "/coberturas";
    }

    protected static String presupuestosUrl(long paciente) {
        return "/api/v1/pacientes/" + paciente + "/presupuestos";
    }

    protected static String presupuestoUrl(long paciente, long presupuesto) {
        return presupuestosUrl(paciente) + "/" + presupuesto;
    }

    protected static Map<String, Object> precioSolicitud(Object precio) {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("precio", precio);
        return pedido;
    }

    protected static Map<String, Object> porcentajeSolicitud(Object porcentaje) {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("porcentaje", porcentaje);
        return pedido;
    }

    protected static Map<String, Object> item(long prestacion, int cantidad) {
        return item(prestacion, null, null, null, cantidad);
    }

    protected static Map<String, Object> item(long prestacion, Long nomenclador, Integer pieza, String cara,
                                              int cantidad) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("prestacionId", prestacion);
        if (nomenclador != null) {
            item.put("nomencladorId", nomenclador);
        }
        if (pieza != null) {
            item.put("pieza", pieza);
        }
        if (cara != null) {
            item.put("cara", cara);
        }
        item.put("cantidad", cantidad);
        return item;
    }

    @SafeVarargs
    protected static Map<String, Object> presupuestoSolicitud(Map<String, Object>... items) {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("items", List.of(items));
        return pedido;
    }

    /** Emite un presupuesto por la API como {@code usuario} y devuelve el id; el test falla si no responde 201. */
    @SafeVarargs
    protected final long emitirComo(String usuario, long paciente, Map<String, Object>... items) throws Exception {
        MvcResult r = post(como(usuario), presupuestosUrl(paciente), presupuestoSolicitud(items));
        if (status(r) != 201) {
            throw new AssertionError("La emisión de preparación respondió " + status(r) + ": " + texto(r));
        }
        return cuerpo(r).path("id").asLong();
    }

    protected String estadoGuardado(long presupuesto) {
        return jdbc.queryForObject("SELECT estado FROM presupuesto WHERE id = ?", String.class, presupuesto);
    }

    // --- Lectura de respuestas ---

    protected JsonNode item(JsonNode presupuesto, String prestacion) {
        for (JsonNode item : presupuesto.path("items")) {
            if (prestacion.equals(item.path("prestacion").path("nombre").asText())) {
                return item;
            }
        }
        throw new AssertionError("El presupuesto no tiene un ítem de " + prestacion + ": " + presupuesto);
    }

    protected static BigDecimal numero(JsonNode nodo) {
        return nodo.decimalValue();
    }

    protected List<String> campos(MvcResult resultado) throws Exception {
        List<String> campos = new ArrayList<>();
        cuerpo(resultado).path("errores").forEach(error -> campos.add(error.path("campo").asText()));
        return campos;
    }

    protected JsonNode presupuesto(Navegador navegador, long paciente, long presupuesto) throws Exception {
        return cuerpo(get(navegador, presupuestoUrl(paciente, presupuesto)));
    }
}
