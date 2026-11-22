package ar.turnos.odontologia.reserva.infrastructure;

import ar.turnos.odontologia.reserva.application.CatalogoReserva;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lectura propia de la reserva sobre {@code prestacion}, {@code profesional}, {@code profesional_prestacion} y
 * {@code sillon} (design D10): SQL parametrizado, solo lectura y sin entidades JPA.
 */
@Repository
@Transactional(readOnly = true)
class CatalogoReservaJdbc implements CatalogoReserva {

    private static final String PROFESIONAL =
            "SELECT id, nombre || ' ' || apellido AS nombre, activo, sillon_habitual_id FROM profesional";

    private final JdbcTemplate jdbc;

    CatalogoReservaJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Prestacion> prestacion(Long id) {
        return jdbc.query("SELECT id, nombre, duracion_minutos, activo FROM prestacion WHERE id = ?",
                (rs, fila) -> prestacion(rs), id).stream().findFirst();
    }

    @Override
    public Optional<Profesional> profesional(Long id) {
        return jdbc.query(PROFESIONAL + " WHERE id = ?", (rs, fila) -> profesional(rs), id).stream().findFirst();
    }

    @Override
    public List<Prestacion> prestacionesActivas() {
        return jdbc.query("SELECT id, nombre, duracion_minutos, activo FROM prestacion WHERE activo ORDER BY nombre, id",
                (rs, fila) -> prestacion(rs));
    }

    @Override
    public List<Profesional> profesionalesHabilitados(Long prestacionId) {
        return jdbc.query(PROFESIONAL + " WHERE activo AND id IN "
                        + "(SELECT profesional_id FROM profesional_prestacion WHERE prestacion_id = ?) ORDER BY id",
                (rs, fila) -> profesional(rs), prestacionId);
    }

    @Override
    public boolean estaHabilitado(Long profesionalId, Long prestacionId) {
        Boolean habilitado = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM profesional_prestacion "
                + "WHERE profesional_id = ? AND prestacion_id = ?)", Boolean.class, profesionalId, prestacionId);
        return Boolean.TRUE.equals(habilitado);
    }

    @Override
    public List<Long> sillonesActivos() {
        return jdbc.queryForList("SELECT id FROM sillon WHERE activo ORDER BY id", Long.class);
    }

    private static Prestacion prestacion(ResultSet rs) throws SQLException {
        return new Prestacion(rs.getLong("id"), rs.getString("nombre"),
                Duration.ofMinutes(rs.getInt("duracion_minutos")), rs.getBoolean("activo"));
    }

    private static Profesional profesional(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        String nombre = rs.getString("nombre");
        boolean activo = rs.getBoolean("activo");
        // wasNull() se refiere a la última columna leída: el sillón habitual tiene que ser la última
        long habitual = rs.getLong("sillon_habitual_id");
        return new Profesional(id, nombre, activo, rs.wasNull() ? null : habitual);
    }
}
