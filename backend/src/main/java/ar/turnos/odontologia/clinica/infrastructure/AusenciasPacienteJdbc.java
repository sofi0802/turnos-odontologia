package ar.turnos.odontologia.clinica.infrastructure;

import ar.turnos.odontologia.clinica.application.AusenciasPaciente;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Adaptador de lectura de {@link AusenciasPaciente}: turnos {@code AUSENTE} del paciente (design D7). */
@Repository
class AusenciasPacienteJdbc implements AusenciasPaciente {

    private final JdbcTemplate jdbc;

    AusenciasPacienteJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Ausencia> de(Long pacienteId) {
        return jdbc.query("""
                SELECT t.id, t.inicio, t.profesional_id, p.nombre || ' ' || p.apellido AS profesional
                FROM turno t JOIN profesional p ON p.id = t.profesional_id
                WHERE t.paciente_id = ? AND t.estado = 'AUSENTE'
                ORDER BY t.inicio DESC, t.id DESC""",
                (rs, fila) -> new Ausencia(rs.getLong("id"), rs.getTimestamp("inicio").toInstant(),
                        rs.getLong("profesional_id"), rs.getString("profesional")),
                pacienteId);
    }
}
