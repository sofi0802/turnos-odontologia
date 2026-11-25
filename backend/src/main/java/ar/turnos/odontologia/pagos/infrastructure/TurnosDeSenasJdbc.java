package ar.turnos.odontologia.pagos.infrastructure;

import ar.turnos.odontologia.pagos.application.TurnosDeSenas;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Lee la auditoría de la agenda, el turno y el cargo por JDBC, sin mapear tablas de otros módulos (design D4, como C-22).
 */
class TurnosDeSenasJdbc implements TurnosDeSenas {

    private final JdbcTemplate jdbc;

    TurnosDeSenasJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Long> turnoReprogramado(long turnoId) {
        return jdbc.queryForList("SELECT turno_relacionado_id FROM turno_auditoria WHERE turno_id = ? "
                        + "AND accion = 'REPROGRAMAR' AND turno_relacionado_id IS NOT NULL ORDER BY id DESC LIMIT 1",
                Long.class, turnoId).stream().findFirst();
    }

    @Override
    public Optional<Cancelacion> cancelacion(long turnoId) {
        return jdbc.query("SELECT actor, fecha, causa FROM turno_auditoria WHERE turno_id = ? AND accion = 'CANCELAR' "
                        + "ORDER BY id DESC LIMIT 1",
                (rs, n) -> new Cancelacion(rs.getString("actor"), rs.getTimestamp("fecha").toInstant(),
                        rs.getString("causa")), turnoId).stream().findFirst();
    }

    @Override
    public Instant inicio(long turnoId) {
        return jdbc.queryForObject("SELECT inicio FROM turno WHERE id = ?",
                (rs, n) -> rs.getTimestamp("inicio").toInstant(), turnoId);
    }

    @Override
    public Optional<CargoDelTurno> cargo(long turnoId) {
        return jdbc.query("SELECT id, copago FROM cargo WHERE turno_id = ?",
                (rs, n) -> new CargoDelTurno(rs.getLong("id"), rs.getBigDecimal("copago")), turnoId).stream().findFirst();
    }
}
