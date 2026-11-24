package ar.turnos.odontologia.coberturas.infrastructure;

import ar.turnos.odontologia.coberturas.application.LecturaCoberturas;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Adaptador de lectura de {@link LecturaCoberturas}: una afiliación con su plan y su obra social en un {@code JOIN}. */
@Repository
class LecturaCoberturasJdbc implements LecturaCoberturas {

    private static final String SELECCION = """
            SELECT a.id, o.id AS obra_social_id, o.nombre AS obra_social, o.sigla, o.tipo, p.id AS plan_id,
                   p.nombre AS plan, a.numero_afiliado, a.vigente_desde, a.vigente_hasta
            FROM afiliacion_paciente a
            JOIN plan p ON p.id = a.plan_id AND p.obra_social_id = a.obra_social_id
            JOIN obra_social o ON o.id = a.obra_social_id
            """;

    private final JdbcTemplate jdbc;

    LecturaCoberturasJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AfiliacionDetalle> detalle(Long pacienteId, Long afiliacionId) {
        return jdbc.query(SELECCION + "WHERE a.paciente_id = ? AND a.id = ?", LecturaCoberturasJdbc::detalle,
                pacienteId, afiliacionId).stream().findFirst();
    }

    @Override
    public Optional<AfiliacionDetalle> vigenteEn(Long pacienteId, LocalDate fecha) {
        java.sql.Date dia = java.sql.Date.valueOf(fecha);
        return jdbc.query(SELECCION + "WHERE a.paciente_id = ? AND a.vigente_desde <= ? "
                + "AND (a.vigente_hasta IS NULL OR a.vigente_hasta >= ?)", LecturaCoberturasJdbc::detalle,
                pacienteId, dia, dia).stream().findFirst();
    }

    @Override
    public List<AfiliacionDetalle> historial(Long pacienteId) {
        return jdbc.query(SELECCION + "WHERE a.paciente_id = ? ORDER BY a.vigente_desde DESC, a.id DESC",
                LecturaCoberturasJdbc::detalle, pacienteId);
    }

    private static AfiliacionDetalle detalle(ResultSet rs, int fila) throws SQLException {
        java.sql.Date hasta = rs.getDate("vigente_hasta");
        return new AfiliacionDetalle(rs.getLong("id"), rs.getLong("obra_social_id"), rs.getString("obra_social"),
                rs.getString("sigla"), rs.getString("tipo"), rs.getLong("plan_id"), rs.getString("plan"),
                rs.getString("numero_afiliado"), rs.getDate("vigente_desde").toLocalDate(),
                hasta == null ? null : hasta.toLocalDate());
    }
}
