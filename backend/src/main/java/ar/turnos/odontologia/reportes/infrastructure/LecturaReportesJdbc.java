package ar.turnos.odontologia.reportes.infrastructure;

import ar.turnos.odontologia.reportes.application.LecturaReportes;
import ar.turnos.odontologia.reportes.domain.AusenciaLeida;
import ar.turnos.odontologia.reportes.domain.BloqueoLeido;
import ar.turnos.odontologia.reportes.domain.DatosReporte;
import ar.turnos.odontologia.reportes.domain.HorarioLeido;
import ar.turnos.odontologia.reportes.domain.Intervalo;
import ar.turnos.odontologia.reportes.domain.ProfesionalLeido;
import ar.turnos.odontologia.reportes.domain.SillonLeido;
import ar.turnos.odontologia.reportes.domain.TurnoLeido;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lee por JDBC, sin escribir ni mapear con JPA, las tablas de agenda, disponibilidad y auditoría que usan los reportes
 * (design D2). Ningún dato del paciente sale de acá (RN-RE-02).
 */
@Repository
class LecturaReportesJdbc implements LecturaReportes {

    private static final String TURNOS = """
            SELECT t.id, t.profesional_id, t.sillon_id, t.inicio, t.fin, t.estado,
                   (SELECT a.actor FROM turno_auditoria a
                     WHERE a.turno_id = t.id AND a.accion IN ('CREAR', 'SOBRETURNO')
                     ORDER BY a.fecha, a.id LIMIT 1) AS actor_creacion,
                   EXISTS (SELECT 1 FROM turno_auditoria a
                            WHERE a.turno_id = t.id AND a.accion = 'REPROGRAMAR') AS reprogramado,
                   (SELECT a.actor FROM turno_auditoria a
                     WHERE a.turno_id = t.id AND a.accion = 'CANCELAR'
                     ORDER BY a.fecha DESC, a.id DESC LIMIT 1) AS actor_cancelacion
              FROM turno t
             WHERE t.inicio >= ? AND t.inicio < ?
               AND (CAST(? AS BIGINT) IS NULL OR t.profesional_id = ?)
            """;

    private static final String AUSENCIAS = """
            SELECT profesional_id, tipo, inicio, fin FROM ausencia_profesional
             WHERE inicio < ? AND fin > ? AND (CAST(? AS BIGINT) IS NULL OR profesional_id = ?)
            """;

    private static final String BLOQUEOS = """
            SELECT profesional_id, inicio, fin FROM bloqueo_agenda
             WHERE inicio < ? AND fin > ? AND (CAST(? AS BIGINT) IS NULL OR profesional_id = ?)
            """;

    private final JdbcTemplate jdbc;

    LecturaReportesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public DatosReporte leer(Intervalo periodo, Long profesionalId) {
        OffsetDateTime desde = periodo.inicio().atOffset(ZoneOffset.UTC);
        OffsetDateTime hasta = periodo.fin().atOffset(ZoneOffset.UTC);
        List<TurnoLeido> turnos = jdbc.query(TURNOS, LecturaReportesJdbc::turno, desde, hasta, profesionalId,
                profesionalId);
        List<AusenciaLeida> ausencias = jdbc.query(AUSENCIAS, (rs, n) -> new AusenciaLeida(rs.getLong("profesional_id"),
                rs.getString("tipo"), instante(rs, "inicio"), instante(rs, "fin")), hasta, desde, profesionalId,
                profesionalId);
        List<BloqueoLeido> bloqueos = jdbc.query(BLOQUEOS, (rs, n) -> new BloqueoLeido(rs.getLong("profesional_id"),
                instante(rs, "inicio"), instante(rs, "fin")), hasta, desde, profesionalId, profesionalId);
        List<HorarioLeido> horarios = jdbc.query(
                "SELECT profesional_id, dia_semana, hora_desde, hora_hasta FROM horario_trabajo",
                (rs, n) -> new HorarioLeido(rs.getLong("profesional_id"), rs.getInt("dia_semana"),
                        rs.getObject("hora_desde", LocalTime.class), rs.getObject("hora_hasta", LocalTime.class)));
        List<ProfesionalLeido> profesionales = jdbc.query(
                "SELECT id, nombre, apellido, activo, sillon_habitual_id FROM profesional",
                (rs, n) -> new ProfesionalLeido(rs.getLong("id"), rs.getString("nombre"), rs.getString("apellido"),
                        rs.getBoolean("activo"), rs.getObject("sillon_habitual_id", Long.class)));
        List<SillonLeido> sillones = jdbc.query("SELECT id, nombre, activo FROM sillon",
                (rs, n) -> new SillonLeido(rs.getLong("id"), rs.getString("nombre"), rs.getBoolean("activo")));
        return new DatosReporte(profesionales, sillones, horarios, turnos, ausencias, bloqueos);
    }

    @Override
    public boolean existeProfesional(long profesionalId) {
        Boolean existe = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM profesional WHERE id = ?)", Boolean.class,
                profesionalId);
        return Boolean.TRUE.equals(existe);
    }

    private static TurnoLeido turno(ResultSet rs, int fila) throws SQLException {
        return new TurnoLeido(rs.getLong("id"), rs.getLong("profesional_id"), rs.getLong("sillon_id"),
                instante(rs, "inicio"), instante(rs, "fin"), rs.getString("estado"), rs.getString("actor_creacion"),
                rs.getBoolean("reprogramado"), rs.getString("actor_cancelacion"));
    }

    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        return rs.getObject(columna, OffsetDateTime.class).toInstant();
    }
}
