package ar.turnos.odontologia.clinica.infrastructure;

import ar.turnos.odontologia.clinica.application.AusenciasPaciente;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de lectura de {@link AusenciasPaciente}: turnos {@code AUSENTE} del paciente (design D7) y, desde C-43,
 * los que tuvieron la ausencia deshecha, con el último registro {@code DESHACER_AUSENCIA} de la auditoría. Un turno
 * que volvió a quedar {@code AUSENTE} se muestra como ausencia vigente.
 */
@Repository
class AusenciasPacienteJdbc implements AusenciasPaciente {

    private final JdbcTemplate jdbc;

    AusenciasPacienteJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Ausencia> de(Long pacienteId) {
        return jdbc.query("""
                SELECT t.id, t.inicio, t.profesional_id, p.nombre || ' ' || p.apellido AS profesional,
                       CASE WHEN t.estado = 'AUSENTE' THEN NULL ELSE d.fecha END AS deshecha_en,
                       CASE WHEN t.estado = 'AUSENTE' THEN NULL ELSE d.actor END AS deshecha_por
                FROM turno t JOIN profesional p ON p.id = t.profesional_id
                LEFT JOIN LATERAL (
                    SELECT a.fecha, a.actor FROM turno_auditoria a
                    WHERE a.turno_id = t.id AND a.accion = 'DESHACER_AUSENCIA'
                    ORDER BY a.fecha DESC, a.id DESC LIMIT 1) d ON TRUE
                WHERE t.paciente_id = ? AND (t.estado = 'AUSENTE' OR d.fecha IS NOT NULL)
                ORDER BY t.inicio DESC, t.id DESC""",
                (rs, fila) -> {
                    java.sql.Timestamp deshecha = rs.getTimestamp("deshecha_en");
                    return new Ausencia(rs.getLong("id"), rs.getTimestamp("inicio").toInstant(),
                            rs.getLong("profesional_id"), rs.getString("profesional"),
                            deshecha == null ? null : deshecha.toInstant(), rs.getString("deshecha_por"));
                },
                pacienteId);
    }
}
