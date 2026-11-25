package ar.turnos.odontologia.coberturas;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Base de los tests de integración de C-20 (liquidaciones a obras sociales): suma a la base de C-19 el sillón, los
 * turnos ATENDIDO insertados por SQL (la agenda no se toca) y los pedidos de la API de liquidaciones. Reloj ajustable:
 * hoy es 2026-11-24 a las 12:00 en Buenos Aires. Solo datos ficticios del seed de la KB.
 */
@Tag("integration")
public abstract class IntegracionLiquidacionesBase extends IntegracionPresupuestosBase {

    protected static final String LIQUIDACIONES = "/api/v1/liquidaciones";
    protected static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");

    protected long sillon;

    @BeforeEach
    void prepararLiquidaciones() {
        jdbc.execute("TRUNCATE liquidacion_turno_quitado, liquidacion_linea, liquidacion RESTART IDENTITY CASCADE");
        ahoraEs("2026-11-24T15:00:00Z");
        sillon = jdbc.queryForObject("INSERT INTO sillon(nombre) VALUES ('Sillón 1') RETURNING id", Long.class);
    }

    // --- Datos ---

    /** Inserta un turno con el estado dado, a la hora local de Buenos Aires {@code yyyy-MM-ddTHH:mm}. */
    protected long turno(long paciente, long prestacion, String inicioLocal, String estado) {
        Instant inicio = LocalDateTime.parse(inicioLocal).atZone(BUENOS_AIRES).toInstant();
        Instant fin = inicio.plusSeconds(30 * 60);
        // Los turnos de los tests usan días y horas distintos: las EXCLUDE de la agenda cuentan solo los activos.
        return jdbc.queryForObject("INSERT INTO turno(profesional_id, sillon_id, prestacion_id, paciente_id, inicio, "
                + "fin, fin_con_margen, estado) VALUES (?,?,?,?,?,?,?,?) RETURNING id", Long.class, profesionalAna,
                sillon, prestacion, paciente, Timestamp.from(inicio), Timestamp.from(fin),
                Timestamp.from(fin.plusSeconds(10 * 60)), estado);
    }

    protected long atendido(long paciente, long prestacion, String inicioLocal) {
        return turno(paciente, prestacion, inicioLocal, "ATENDIDO");
    }

    protected void marcarAtendido(long turno) {
        jdbc.update("UPDATE turno SET estado = 'ATENDIDO' WHERE id = ?", turno);
    }

    protected int lineas(long liquidacion) {
        return jdbc.queryForObject("SELECT count(*) FROM liquidacion_linea WHERE liquidacion_id = ?", Integer.class,
                liquidacion);
    }

    protected BigDecimal totalSolicitadoGuardado(long liquidacion) {
        return jdbc.queryForObject("SELECT total_solicitado FROM liquidacion WHERE id = ?", BigDecimal.class,
                liquidacion);
    }

    protected BigDecimal totalCobradoGuardado(long liquidacion) {
        return jdbc.queryForObject("SELECT total_cobrado FROM liquidacion WHERE id = ?", BigDecimal.class,
                liquidacion);
    }

    protected BigDecimal sumaDeLasLineas(long liquidacion) {
        return jdbc.queryForObject("SELECT COALESCE(sum(monto_solicitado), 0) FROM liquidacion_linea "
                + "WHERE liquidacion_id = ?", BigDecimal.class, liquidacion);
    }

    protected BigDecimal sumaDeLoCobrado(long liquidacion) {
        return jdbc.queryForObject("SELECT COALESCE(sum(monto_cobrado), 0) FROM liquidacion_linea "
                + "WHERE liquidacion_id = ?", BigDecimal.class, liquidacion);
    }

    protected String estadoGuardadoLiquidacion(long liquidacion) {
        return jdbc.queryForObject("SELECT estado FROM liquidacion WHERE id = ?", String.class, liquidacion);
    }

    /** Todos los turnos que figuran en alguna línea de la liquidación. */
    protected List<Long> turnosLiquidados(long liquidacion) {
        return jdbc.queryForList("SELECT turno_id FROM liquidacion_linea WHERE liquidacion_id = ? ORDER BY turno_id",
                Long.class, liquidacion);
    }

    /** Presenta por la API la liquidación de la obra social y el período, que el test ya armó como borrador. */
    protected void presentar(long liquidacion) throws Exception {
        MvcResult r = post(como("admin"), url(liquidacion) + "/presentar", null);
        if (status(r) != 200) {
            throw new AssertionError("Presentar de preparación respondió " + status(r) + ": " + texto(r));
        }
    }

    // --- Pedidos ---

    protected static String url(long liquidacion) {
        return LIQUIDACIONES + "/" + liquidacion;
    }

    protected static String urlLinea(long liquidacion, long linea) {
        return url(liquidacion) + "/lineas/" + linea;
    }

    protected static Map<String, Object> generacion(long obraSocial, Object periodo) {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("obraSocialId", obraSocial);
        pedido.put("periodo", periodo);
        return pedido;
    }

    protected MvcResult generarComo(String usuario, long obraSocial, String periodo) throws Exception {
        return post(como(usuario), LIQUIDACIONES, generacion(obraSocial, periodo));
    }

    protected MvcResult generar(long obraSocial, String periodo) throws Exception {
        return generarComo("admin", obraSocial, periodo);
    }

    /** Genera por la API y devuelve el detalle; el test falla si no responde 201. */
    protected JsonNode generada(long obraSocial, String periodo) throws Exception {
        MvcResult r = generar(obraSocial, periodo);
        if (status(r) != 201) {
            throw new AssertionError("Generar de preparación respondió " + status(r) + ": " + texto(r));
        }
        return cuerpo(r);
    }

    protected JsonNode detalle(long liquidacion) throws Exception {
        MvcResult r = get(como("admin"), url(liquidacion));
        if (status(r) != 200) {
            throw new AssertionError("El detalle respondió " + status(r) + ": " + texto(r));
        }
        return cuerpo(r);
    }

    protected MvcResult regenerar(long liquidacion) throws Exception {
        return post(como("admin"), url(liquidacion) + "/regenerar", null);
    }

    protected static Map<String, Object> codigoSolicitud(long nomenclador) {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("nomencladorId", nomenclador);
        return pedido;
    }

    protected static Map<String, Object> conciliacion(Object montoCobrado, String motivo) {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("montoCobrado", montoCobrado);
        if (motivo != null) {
            pedido.put("motivo", motivo);
        }
        return pedido;
    }

    protected MvcResult conciliar(long liquidacion, long linea, Object montoCobrado, String motivo) throws Exception {
        return put(como("admin"), urlLinea(liquidacion, linea) + "/conciliacion", conciliacion(montoCobrado, motivo));
    }

    // --- Lectura de respuestas ---

    /** Todas las líneas del detalle, de todos los grupos de plan. */
    protected static List<JsonNode> lineasDe(JsonNode detalle) {
        List<JsonNode> lineas = new ArrayList<>();
        detalle.path("grupos").forEach(grupo -> grupo.path("lineas").forEach(lineas::add));
        return lineas;
    }

    /** La única línea del detalle de esa prestación. */
    protected static JsonNode lineaDe(JsonNode detalle, String prestacion) {
        List<JsonNode> coinciden = lineasDe(detalle).stream()
                .filter(l -> prestacion.equals(l.path("prestacion").path("nombre").asText())).toList();
        if (coinciden.size() != 1) {
            throw new AssertionError("Se esperaba una línea de " + prestacion + " y hay " + coinciden.size() + ": "
                    + detalle);
        }
        return coinciden.get(0);
    }

    protected static JsonNode grupoDe(JsonNode detalle, String plan) {
        for (JsonNode grupo : detalle.path("grupos")) {
            if (plan.equals(grupo.path("plan").asText())) {
                return grupo;
            }
        }
        throw new AssertionError("El detalle no tiene el grupo " + plan + ": " + detalle);
    }

    protected static List<Long> idsDeTurnos(JsonNode detalle) {
        return lineasDe(detalle).stream().map(l -> l.path("turnoId").asLong()).toList();
    }

    /** Lanza los pedidos a la vez desde la misma línea de largada y espera a todos (como las carreras de C-18). */
    @SafeVarargs
    protected static List<MvcResult> enParalelo(Callable<MvcResult>... pedidos) throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(pedidos.length);
        try {
            List<Future<MvcResult>> futuros = new ArrayList<>();
            for (Callable<MvcResult> pedido : pedidos) {
                futuros.add(hilos.submit(() -> {
                    largada.await();
                    return pedido.call();
                }));
            }
            largada.countDown();
            List<MvcResult> resultados = new ArrayList<>();
            for (Future<MvcResult> futuro : futuros) {
                resultados.add(futuro.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            hilos.shutdownNow();
        }
    }
}
