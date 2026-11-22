package ar.turnos.odontologia.reserva.infrastructure;

import ar.turnos.odontologia.agenda.domain.EstadoTurno;
import ar.turnos.odontologia.reserva.application.ReservasPaciente;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lectura propia de la reserva sobre {@code turno}, {@code prestacion}, {@code profesional} y {@code paciente}
 * (design D10): SQL parametrizado y de solo lectura. Toda búsqueda filtra por el paciente de la sesión en la misma
 * consulta, así un número ajeno es indistinguible de uno inexistente (design D6).
 */
@Repository
@Transactional(readOnly = true)
class ReservasPacienteJdbc implements ReservasPaciente {

    private static final String TURNOS = """
            SELECT t.id, t.codigo_reserva, t.inicio, t.fin, t.estado,
                   t.prestacion_id, pr.nombre AS prestacion_nombre,
                   t.profesional_id, po.nombre || ' ' || po.apellido AS profesional_nombre
            FROM turno t
            JOIN prestacion pr ON pr.id = t.prestacion_id
            JOIN profesional po ON po.id = t.profesional_id
            """;

    private final JdbcTemplate jdbc;

    ReservasPacienteJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<TurnoDelPaciente> buscar(String numeroNormalizado, Long pacienteId) {
        return jdbc.query(TURNOS + " WHERE upper(trim(t.codigo_reserva)) = ? AND t.paciente_id = ?",
                (rs, fila) -> turno(rs), numeroNormalizado, pacienteId).stream().findFirst();
    }

    @Override
    public List<TurnoDelPaciente> turnosDe(Long pacienteId) {
        return jdbc.query(TURNOS + " WHERE t.paciente_id = ? ORDER BY t.inicio, t.id",
                (rs, fila) -> turno(rs), pacienteId);
    }

    @Override
    public Optional<String> dni(Long pacienteId) {
        return jdbc.queryForList("SELECT dni FROM paciente WHERE id = ?", String.class, pacienteId).stream()
                .findFirst();
    }

    private static TurnoDelPaciente turno(ResultSet rs) throws SQLException {
        return new TurnoDelPaciente(rs.getLong("id"), rs.getString("codigo_reserva"),
                rs.getTimestamp("inicio").toInstant(), rs.getTimestamp("fin").toInstant(),
                EstadoTurno.valueOf(rs.getString("estado")), rs.getLong("prestacion_id"),
                rs.getString("prestacion_nombre"), rs.getLong("profesional_id"), rs.getString("profesional_nombre"));
    }
}
