package ar.turnos.odontologia.caja.infrastructure;

import ar.turnos.odontologia.caja.application.Cajas;
import ar.turnos.odontologia.caja.domain.Arqueo;
import ar.turnos.odontologia.caja.domain.MedioPago;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Cajas por JDBC con los bloqueos de design D4. */
@Repository
class CajasJdbc implements Cajas {

    private static final String COLUMNAS = "SELECT id, fecha, monto_inicial, abierta_por, abierta_en, cerrada_por, "
            + "cerrada_en, efectivo_contado, diferencia, motivo_diferencia FROM caja ";

    private final NamedParameterJdbcTemplate jdbc;

    CajasJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Long> abrir(LocalDate fecha, BigDecimal montoInicial, String usuario, Instant ahora) {
        // ON CONFLICT sobre el índice único parcial: si otra caja está abierta, no inserta y la transacción sigue sana.
        List<Long> ids = jdbc.queryForList("INSERT INTO caja(fecha, monto_inicial, abierta_por, abierta_en) "
                        + "VALUES (:fecha, :monto, :usuario, :ahora) "
                        + "ON CONFLICT ((true)) WHERE cerrada_en IS NULL DO NOTHING RETURNING id",
                new MapSqlParameterSource().addValue("fecha", fecha).addValue("monto", montoInicial)
                        .addValue("usuario", usuario).addValue("ahora", Fechas.aOffset(ahora)), Long.class);
        return ids.stream().findFirst();
    }

    @Override
    public Optional<CajaFila> abierta() {
        return una(COLUMNAS + "WHERE cerrada_en IS NULL", new MapSqlParameterSource());
    }

    @Override
    public Optional<CajaFila> abiertaParaCobrar() {
        return una(COLUMNAS + "WHERE cerrada_en IS NULL FOR SHARE", new MapSqlParameterSource());
    }

    @Override
    public Optional<CajaFila> porId(long id) {
        return una(COLUMNAS + "WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    @Override
    public Optional<CajaFila> bloquearParaCerrar(long id) {
        return una(COLUMNAS + "WHERE id = :id FOR UPDATE", new MapSqlParameterSource("id", id));
    }

    @Override
    public Optional<CajaFila> bloquearParaAnular(long id) {
        return una(COLUMNAS + "WHERE id = :id FOR SHARE", new MapSqlParameterSource("id", id));
    }

    @Override
    public void cerrar(long id, String usuario, Instant ahora, Arqueo arqueo) {
        jdbc.update("UPDATE caja SET cerrada_por = :usuario, cerrada_en = :ahora, efectivo_contado = :contado, "
                        + "diferencia = :diferencia, motivo_diferencia = :motivo WHERE id = :id AND cerrada_en IS NULL",
                new MapSqlParameterSource().addValue("id", id).addValue("usuario", usuario)
                        .addValue("ahora", Fechas.aOffset(ahora)).addValue("contado", arqueo.contado().valor())
                        .addValue("diferencia", arqueo.diferencia()).addValue("motivo", arqueo.motivo()));
    }

    @Override
    public List<CajaFila> entre(LocalDate desde, LocalDate hasta) {
        return jdbc.query(COLUMNAS + "WHERE fecha BETWEEN :desde AND :hasta ORDER BY abierta_en DESC, id DESC",
                new MapSqlParameterSource().addValue("desde", desde).addValue("hasta", hasta), CajasJdbc::fila);
    }

    @Override
    public Map<Long, Map<MedioPago, BigDecimal>> totales(List<Long> cajaIds) {
        Map<Long, Map<MedioPago, BigDecimal>> totales = new HashMap<>();
        if (cajaIds.isEmpty()) {
            return totales;
        }
        jdbc.query("SELECT caja_id, medio, SUM(importe) AS total FROM cobro WHERE caja_id IN (:ids) AND NOT anulado "
                        + "GROUP BY caja_id, medio", new MapSqlParameterSource("ids", cajaIds),
                rs -> {
                    totales.computeIfAbsent(rs.getLong("caja_id"), k -> new EnumMap<>(MedioPago.class))
                            .put(MedioPago.valueOf(rs.getString("medio")), rs.getBigDecimal("total"));
                });
        return totales;
    }

    private Optional<CajaFila> una(String sql, MapSqlParameterSource parametros) {
        return jdbc.query(sql, parametros, CajasJdbc::fila).stream().findFirst();
    }

    private static CajaFila fila(ResultSet rs, int n) throws SQLException {
        return new CajaFila(rs.getLong("id"), Fechas.fecha(rs, "fecha"), rs.getBigDecimal("monto_inicial"),
                rs.getString("abierta_por"), Fechas.instante(rs, "abierta_en"), rs.getString("cerrada_por"),
                Fechas.instante(rs, "cerrada_en"), rs.getBigDecimal("efectivo_contado"),
                rs.getBigDecimal("diferencia"), rs.getString("motivo_diferencia"));
    }
}
