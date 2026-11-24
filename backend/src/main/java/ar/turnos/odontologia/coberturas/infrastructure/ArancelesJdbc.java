package ar.turnos.odontologia.coberturas.infrastructure;

import ar.turnos.odontologia.coberturas.application.Aranceles;
import ar.turnos.odontologia.coberturas.domain.Precio;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de {@link Aranceles}: escribe solo {@code arancel} y lee {@code prestacion} (de {@code configuracion/}).
 * El upsert es una sola sentencia {@code ON CONFLICT}: dos pedidos a la vez no chocan con la restricción única.
 */
@Repository
class ArancelesJdbc implements Aranceles {

    private static final String SELECCION = """
            SELECT p.id, p.nombre, p.activo, a.precio, a.actualizado_en
            FROM prestacion p LEFT JOIN arancel a ON a.prestacion_id = p.id
            """;

    private final JdbcTemplate jdbc;

    ArancelesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ArancelFila> todos() {
        return jdbc.query(SELECCION + "ORDER BY p.nombre, p.id", ArancelesJdbc::fila);
    }

    @Override
    public Optional<ArancelFila> guardar(Long prestacionId, Precio precio, Instant actualizadoEn) {
        int filas = jdbc.update("""
                INSERT INTO arancel (prestacion_id, precio, actualizado_en)
                SELECT p.id, ?, ? FROM prestacion p WHERE p.id = ?
                ON CONFLICT (prestacion_id) DO UPDATE
                    SET precio = EXCLUDED.precio, actualizado_en = EXCLUDED.actualizado_en
                """, precio.valor(), OffsetDateTime.ofInstant(actualizadoEn, ZoneOffset.UTC), prestacionId);
        if (filas == 0) {
            return Optional.empty();
        }
        return jdbc.query(SELECCION + "WHERE p.id = ?", ArancelesJdbc::fila, prestacionId).stream().findFirst();
    }

    private static ArancelFila fila(ResultSet rs, int numero) throws SQLException {
        OffsetDateTime actualizado = rs.getObject("actualizado_en", OffsetDateTime.class);
        return new ArancelFila(rs.getLong("id"), rs.getString("nombre"), rs.getBoolean("activo"),
                rs.getBigDecimal("precio"), actualizado == null ? null : actualizado.toInstant());
    }
}
