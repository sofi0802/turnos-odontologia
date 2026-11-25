package ar.turnos.odontologia.coberturas.infrastructure;

import ar.turnos.odontologia.coberturas.application.Liquidaciones;
import ar.turnos.odontologia.coberturas.domain.EstadoLiquidacion;
import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion;
import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion.CodigoCubierto;
import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion.OrigenPrecio;
import ar.turnos.odontologia.coberturas.domain.Liquidacion;
import ar.turnos.odontologia.coberturas.domain.Liquidacion.Hitos;
import ar.turnos.odontologia.coberturas.domain.Periodo;
import ar.turnos.odontologia.coberturas.domain.ResultadoConciliacion;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de {@link Liquidaciones} con {@code JdbcTemplate} (C-20, design D1): cada operación es una sentencia
 * explícita que coincide con lo que permiten los triggers de V14, sin {@code dirty checking} que emita un
 * {@code UPDATE} inesperado. Escribe solo {@code liquidacion}, {@code liquidacion_linea} y
 * {@code liquidacion_turno_quitado}. Nada de lo que lee ni escribe va a logs (RN-LQ-08).
 */
@Repository
class LiquidacionesJdbc implements Liquidaciones {

    private static final String CABECERA = """
            SELECT id, obra_social_id, periodo, estado, generada_en, generada_por, presentada_en, presentada_por,
                   cerrada_en, cerrada_por
            FROM liquidacion WHERE id = ?""";

    private static final String LINEAS = """
            SELECT id, turno_id, paciente_id, plan_id, prestacion_id, fecha, paciente_nombre, paciente_dni,
                   numero_afiliado, plan_nombre, prestacion_nombre, precio, origen_precio, codigo_elegido,
                   nomenclador_id, codigo_nomenclador, porcentaje, monto_solicitado, monto_cobrado, resultado,
                   motivo_rechazo, conciliada_en, conciliada_por
            FROM liquidacion_linea WHERE liquidacion_id = ? ORDER BY plan_nombre, fecha, turno_id""";

    private final JdbcTemplate jdbc;

    LiquidacionesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existe(Long obraSocialId, Periodo periodo) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM liquidacion WHERE obra_social_id = ? AND periodo = ?)", Boolean.class,
                obraSocialId, periodo.primerDia()));
    }

    @Override
    public Long crear(Long obraSocialId, Periodo periodo, Hitos hitos) {
        return jdbc.queryForObject("INSERT INTO liquidacion (obra_social_id, periodo, generada_en, generada_por) "
                + "VALUES (?, ?, ?, ?) RETURNING id", Long.class, obraSocialId, periodo.primerDia(), hitos.generadaEn(),
                hitos.generadaPor());
    }

    @Override
    public Optional<Liquidacion> bloquear(Long id) {
        return leer(CABECERA + " FOR UPDATE", id);
    }

    @Override
    public Optional<Liquidacion> cargar(Long id) {
        return leer(CABECERA, id);
    }

    private Optional<Liquidacion> leer(String sqlCabecera, Long id) {
        return jdbc.query(sqlCabecera, LiquidacionesJdbc::cabecera, id).stream().findFirst().map(cabecera -> {
            List<LineaLiquidacion> lineas = jdbc.query(LINEAS, LiquidacionesJdbc::linea, id);
            return new Liquidacion(cabecera.id(), cabecera.obraSocialId(), cabecera.periodo(), cabecera.estado(), lineas,
                    cabecera.hitos());
        });
    }

    @Override
    public Long insertarLinea(Long liquidacionId, Long obraSocialId, LineaLiquidacion l) {
        return jdbc.queryForObject("""
                INSERT INTO liquidacion_linea (liquidacion_id, obra_social_id, turno_id, paciente_id, plan_id,
                    prestacion_id, fecha, paciente_nombre, paciente_dni, numero_afiliado, plan_nombre,
                    prestacion_nombre, precio, origen_precio, codigo_elegido, nomenclador_id, codigo_nomenclador,
                    porcentaje, monto_solicitado)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class,
                liquidacionId, obraSocialId, l.turnoId(), l.pacienteId(), l.planId(), l.prestacionId(), l.fecha(),
                l.pacienteNombre(), l.pacienteDni(), l.numeroAfiliado(), l.planNombre(), l.prestacionNombre(),
                l.precio(), l.origenPrecio().name(), l.codigoElegido(), l.nomencladorId(), l.codigo(), l.porcentaje(),
                l.montoSolicitado());
    }

    @Override
    public void actualizarLinea(LineaLiquidacion l) {
        jdbc.update("""
                UPDATE liquidacion_linea SET paciente_id = ?, plan_id = ?, prestacion_id = ?, fecha = ?,
                    paciente_nombre = ?, paciente_dni = ?, numero_afiliado = ?, plan_nombre = ?,
                    prestacion_nombre = ?, precio = ?, origen_precio = ?, codigo_elegido = ?, nomenclador_id = ?,
                    codigo_nomenclador = ?, porcentaje = ?, monto_solicitado = ?
                WHERE id = ?""", l.pacienteId(), l.planId(), l.prestacionId(), l.fecha(), l.pacienteNombre(),
                l.pacienteDni(), l.numeroAfiliado(), l.planNombre(), l.prestacionNombre(), l.precio(),
                l.origenPrecio().name(), l.codigoElegido(), l.nomencladorId(), l.codigo(), l.porcentaje(),
                l.montoSolicitado(), l.id());
    }

    @Override
    public void borrarLinea(Long lineaId) {
        jdbc.update("DELETE FROM liquidacion_linea WHERE id = ?", lineaId);
    }

    @Override
    public void guardarConciliacion(LineaLiquidacion l) {
        jdbc.update("UPDATE liquidacion_linea SET monto_cobrado = ?, resultado = ?, motivo_rechazo = ?, "
                + "conciliada_en = ?, conciliada_por = ? WHERE id = ?", l.montoCobrado(), l.resultado().name(),
                l.motivoRechazo(), l.conciliadaEn(), l.conciliadaPor(), l.id());
    }

    @Override
    public void quitarTurno(Long liquidacionId, Long turnoId) {
        jdbc.update("INSERT INTO liquidacion_turno_quitado (liquidacion_id, turno_id) VALUES (?, ?) "
                + "ON CONFLICT DO NOTHING", liquidacionId, turnoId);
    }

    @Override
    public void guardarCabecera(Liquidacion l) {
        Liquidacion.Totales totales = l.totales();
        Hitos h = l.hitos();
        jdbc.update("""
                UPDATE liquidacion SET estado = ?, total_solicitado = ?, total_cobrado = ?, generada_en = ?,
                    generada_por = ?, presentada_en = ?, presentada_por = ?, cerrada_en = ?, cerrada_por = ?
                WHERE id = ?""", l.estado().name(), totales.totalSolicitado(), totales.totalCobrado(), h.generadaEn(),
                h.generadaPor(), h.presentadaEn(), h.presentadaPor(), h.cerradaEn(), h.cerradaPor(), l.id());
    }

    @Override
    public List<Resumen> listar(Long obraSocialId, EstadoLiquidacion estado) {
        StringBuilder sql = new StringBuilder("""
                SELECT l.id, l.obra_social_id, o.nombre AS obra_social, l.periodo, l.estado,
                       (SELECT count(*) FROM liquidacion_linea x WHERE x.liquidacion_id = l.id) AS lineas,
                       l.total_solicitado, l.total_cobrado
                FROM liquidacion l JOIN obra_social o ON o.id = l.obra_social_id WHERE 1 = 1""");
        List<Object> argumentos = new ArrayList<>();
        if (obraSocialId != null) {
            sql.append(" AND l.obra_social_id = ?");
            argumentos.add(obraSocialId);
        }
        if (estado != null) {
            sql.append(" AND l.estado = ?");
            argumentos.add(estado.name());
        }
        sql.append(" ORDER BY l.periodo DESC, l.id DESC");
        return jdbc.query(sql.toString(), (rs, n) -> new Resumen(rs.getLong("id"), rs.getLong("obra_social_id"),
                rs.getString("obra_social"), YearMonth.from(rs.getObject("periodo", LocalDate.class)).toString(),
                rs.getString("estado"), rs.getInt("lineas"), rs.getBigDecimal("total_solicitado"),
                rs.getBigDecimal("total_cobrado")), argumentos.toArray());
    }

    private static final String DISPONIBLES = """
            SELECT l.id AS linea_id, n.id AS nomenclador_id, n.codigo, cp.porcentaje
            FROM liquidacion_linea l
            JOIN nomenclador n ON n.prestacion_id = l.prestacion_id AND n.activo
            JOIN cobertura_plan cp ON cp.nomenclador_id = n.id AND cp.plan_id = l.plan_id AND cp.porcentaje > 0
            """;

    @Override
    public Map<Long, List<CodigoCubierto>> codigosDisponibles(Long liquidacionId) {
        Map<Long, List<CodigoCubierto>> porLinea = new LinkedHashMap<>();
        jdbc.query(DISPONIBLES + "WHERE l.liquidacion_id = ? AND l.nomenclador_id IS NULL "
                + "ORDER BY l.id, lower(n.codigo), n.id", rs -> {
                    porLinea.computeIfAbsent(rs.getLong("linea_id"), k -> new ArrayList<>()).add(
                            new CodigoCubierto(rs.getLong("nomenclador_id"), rs.getString("codigo"),
                                    rs.getInt("porcentaje")));
                }, liquidacionId);
        return porLinea;
    }

    @Override
    public Optional<CodigoCubierto> codigoDisponible(Long lineaId, Long nomencladorId) {
        return jdbc.query(DISPONIBLES + "WHERE l.id = ? AND n.id = ?", (rs, n) -> new CodigoCubierto(
                rs.getLong("nomenclador_id"), rs.getString("codigo"), rs.getInt("porcentaje")), lineaId, nomencladorId)
                .stream().findFirst();
    }

    private record Cabecera(Long id, Long obraSocialId, Periodo periodo, EstadoLiquidacion estado, Hitos hitos) {}

    private static Cabecera cabecera(ResultSet rs, int numero) throws SQLException {
        Hitos hitos = new Hitos(rs.getObject("generada_en", OffsetDateTime.class), rs.getString("generada_por"),
                rs.getObject("presentada_en", OffsetDateTime.class), rs.getString("presentada_por"),
                rs.getObject("cerrada_en", OffsetDateTime.class), rs.getString("cerrada_por"));
        return new Cabecera(rs.getLong("id"), rs.getLong("obra_social_id"),
                Periodo.de(YearMonth.from(rs.getObject("periodo", LocalDate.class))),
                EstadoLiquidacion.valueOf(rs.getString("estado")), hitos);
    }

    private static LineaLiquidacion linea(ResultSet rs, int numero) throws SQLException {
        String resultado = rs.getString("resultado");
        return new LineaLiquidacion(rs.getLong("id"), rs.getLong("turno_id"), rs.getLong("paciente_id"),
                rs.getLong("plan_id"), rs.getLong("prestacion_id"), rs.getObject("fecha", LocalDate.class),
                rs.getString("paciente_nombre"), rs.getString("paciente_dni"), rs.getString("numero_afiliado"),
                rs.getString("plan_nombre"), rs.getString("prestacion_nombre"), rs.getBigDecimal("precio"),
                OrigenPrecio.valueOf(rs.getString("origen_precio")), rs.getBoolean("codigo_elegido"),
                rs.getObject("nomenclador_id", Long.class), rs.getString("codigo_nomenclador"),
                rs.getObject("porcentaje", Integer.class), rs.getBigDecimal("monto_solicitado"),
                rs.getBigDecimal("monto_cobrado"), resultado == null ? null : ResultadoConciliacion.valueOf(resultado),
                rs.getString("motivo_rechazo"), rs.getObject("conciliada_en", OffsetDateTime.class),
                rs.getString("conciliada_por"));
    }
}
