package ar.turnos.odontologia.caja.infrastructure;

import ar.turnos.odontologia.caja.application.Cobros;
import ar.turnos.odontologia.caja.application.Vistas.CobroVista;
import ar.turnos.odontologia.caja.domain.MedioPago;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Cobros por JDBC: se insertan y se anulan, nunca se borran (RN-CA-07). */
@Repository
class CobrosJdbc implements Cobros {

    private static final String VISTA = "SELECT cb.id, cb.cargo_id, p.apellido || ', ' || p.nombre AS paciente, "
            + "cb.registrado_en, cb.medio, cb.importe, cb.registrado_por, cb.anulado, cb.anulado_por, cb.anulado_en, "
            + "cb.motivo_anulacion FROM cobro cb JOIN cargo c ON c.id = cb.cargo_id JOIN paciente p ON p.id = c.paciente_id ";

    private final NamedParameterJdbcTemplate jdbc;

    CobrosJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long registrar(long cargoId, long cajaId, BigDecimal importe, MedioPago medio, String usuario,
                          Instant ahora) {
        Long id = jdbc.queryForObject("INSERT INTO cobro(cargo_id, caja_id, importe, medio, registrado_por, "
                        + "registrado_en) VALUES (:cargo, :caja, :importe, :medio, :usuario, :ahora) RETURNING id",
                new MapSqlParameterSource().addValue("cargo", cargoId).addValue("caja", cajaId)
                        .addValue("importe", importe).addValue("medio", medio.name()).addValue("usuario", usuario)
                        .addValue("ahora", Fechas.aOffset(ahora)), Long.class);
        return id == null ? 0 : id;
    }

    @Override
    public Optional<CobroBloqueado> bloquear(long cobroId) {
        return jdbc.query("SELECT id, caja_id, anulado FROM cobro WHERE id = :id FOR UPDATE",
                new MapSqlParameterSource("id", cobroId),
                (rs, n) -> new CobroBloqueado(rs.getLong("id"), rs.getLong("caja_id"), rs.getBoolean("anulado")))
                .stream().findFirst();
    }

    @Override
    public void anular(long cobroId, String usuario, Instant ahora, String motivo) {
        jdbc.update("UPDATE cobro SET anulado = true, anulado_por = :usuario, anulado_en = :ahora, "
                        + "motivo_anulacion = :motivo WHERE id = :id AND NOT anulado",
                new MapSqlParameterSource().addValue("id", cobroId).addValue("usuario", usuario)
                        .addValue("ahora", Fechas.aOffset(ahora)).addValue("motivo", motivo));
    }

    @Override
    public Optional<CobroVista> porId(long cobroId) {
        return jdbc.query(VISTA + "WHERE cb.id = :id", new MapSqlParameterSource("id", cobroId), CobrosJdbc::vista)
                .stream().findFirst();
    }

    @Override
    public List<CobroVista> deLaCaja(long cajaId) {
        return jdbc.query(VISTA + "WHERE cb.caja_id = :caja ORDER BY cb.registrado_en, cb.id",
                new MapSqlParameterSource("caja", cajaId), CobrosJdbc::vista);
    }

    @Override
    public List<CobroVista> deLosCargos(List<Long> cargoIds) {
        if (cargoIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(VISTA + "WHERE cb.cargo_id IN (:ids) ORDER BY cb.registrado_en, cb.id",
                new MapSqlParameterSource("ids", cargoIds), CobrosJdbc::vista);
    }

    private static CobroVista vista(ResultSet rs, int n) throws SQLException {
        return new CobroVista(rs.getLong("id"), rs.getLong("cargo_id"), rs.getString("paciente"),
                Fechas.instante(rs, "registrado_en"), MedioPago.valueOf(rs.getString("medio")),
                rs.getBigDecimal("importe"), rs.getString("registrado_por"), rs.getBoolean("anulado"),
                rs.getString("anulado_por"), Fechas.instante(rs, "anulado_en"), rs.getString("motivo_anulacion"));
    }
}
