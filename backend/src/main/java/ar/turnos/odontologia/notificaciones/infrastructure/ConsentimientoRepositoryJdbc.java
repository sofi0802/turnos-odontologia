package ar.turnos.odontologia.notificaciones.infrastructure;

import ar.turnos.odontologia.notificaciones.application.ConsentimientoRepository;
import ar.turnos.odontologia.notificaciones.domain.CambioConsentimiento;
import ar.turnos.odontologia.notificaciones.domain.EstadoConsentimiento;
import ar.turnos.odontologia.notificaciones.domain.EventoConsentimiento;
import ar.turnos.odontologia.notificaciones.domain.MedioConsentimiento;
import ar.turnos.odontologia.notificaciones.domain.OrigenConsentimiento;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consentimiento con SQL nativo (C-17, design D1). El estado vigente sale de las columnas de V7 en {@code paciente},
 * que siguen leyendo C-11 y C-15; el historial, de {@code consentimiento_comunicacion_evento} (V11). Comparte la
 * transacción de quien lo llama.
 */
@Repository
class ConsentimientoRepositoryJdbc implements ConsentimientoRepository {

    private final JdbcTemplate jdbc;

    ConsentimientoRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Boolean> vigenteBloqueando(long pacienteId) {
        return jdbc.query("SELECT consentimiento_comunicaciones FROM paciente WHERE id = ? FOR UPDATE",
                (rs, fila) -> rs.getBoolean(1), pacienteId).stream().findFirst();
    }

    @Override
    public void registrar(long pacienteId, CambioConsentimiento cambio, Instant cuando) {
        jdbc.update("INSERT INTO consentimiento_comunicacion_evento(paciente_id, otorgado, registrado_en, origen, "
                        + "medio, usuario_id) VALUES (?,?,?,?,?,?)",
                pacienteId, cambio.otorgado(), TurnosARecordarJdbc.fecha(cuando), cambio.origen().name(),
                cambio.medio() == null ? null : cambio.medio().name(), cambio.usuarioId());
        jdbc.update("UPDATE paciente SET consentimiento_comunicaciones = ?, consentimiento_fecha = ? WHERE id = ?",
                cambio.otorgado(), TurnosARecordarJdbc.fecha(cuando), pacienteId);
    }

    @Override
    public Optional<EstadoConsentimiento> consultar(long pacienteId) {
        List<EventoConsentimiento> historial = jdbc.query("""
                SELECT e.otorgado, e.registrado_en, e.origen, e.medio, u.username
                FROM consentimiento_comunicacion_evento e
                LEFT JOIN usuario u ON u.id = e.usuario_id
                WHERE e.paciente_id = ?
                ORDER BY e.id
                """, (rs, fila) -> evento(rs), pacienteId);
        return jdbc.query("SELECT consentimiento_comunicaciones, consentimiento_fecha FROM paciente WHERE id = ?",
                (rs, fila) -> new EstadoConsentimiento(rs.getBoolean(1),
                        TurnosARecordarJdbc.instante(rs, "consentimiento_fecha"), historial), pacienteId)
                .stream().findFirst();
    }

    private static EventoConsentimiento evento(ResultSet rs) throws SQLException {
        String medio = rs.getString("medio");
        return new EventoConsentimiento(rs.getBoolean("otorgado"), TurnosARecordarJdbc.instante(rs, "registrado_en"),
                OrigenConsentimiento.valueOf(rs.getString("origen")),
                medio == null ? null : MedioConsentimiento.valueOf(medio), rs.getString("username"));
    }
}
