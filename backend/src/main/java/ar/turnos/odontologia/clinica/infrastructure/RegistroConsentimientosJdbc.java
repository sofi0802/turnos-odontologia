package ar.turnos.odontologia.clinica.infrastructure;

import ar.turnos.odontologia.clinica.application.RegistroConsentimientos;
import ar.turnos.odontologia.clinica.domain.ConsentimientoFirmado;
import ar.turnos.odontologia.clinica.domain.ContenidoCanonico;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Adaptador JDBC de {@link RegistroConsentimientos} sobre las tablas de V18 (C-27, design D4, D5, D6 y D10). Solo
 * inserta: el trigger de la base rechaza cualquier cambio o borrado del documento firmado y de la revocación.
 */
@Repository
class RegistroConsentimientosJdbc implements RegistroConsentimientos {

    private static final String SELECT = """
            SELECT c.id, c.paciente_id, v.plantilla_id, c.plantilla_version_id, v.numero AS version_numero,
                   c.titulo, c.texto, c.firmante_nombre, c.firmante_dni, c.profesional_id,
                   p.nombre || ' ' || p.apellido AS profesional, c.firmado_en, c.ip_origen, c.dispositivo,
                   c.tipo_firma, c.formato_canonico, c.hash_algoritmo, c.hash,
                   r.revocado_en, r.revocado_por, rp.nombre || ' ' || rp.apellido AS revocado_por_nombre, r.motivo
            FROM consentimiento_firmado c
            JOIN plantilla_consentimiento_version v ON v.id = c.plantilla_version_id
            JOIN profesional p ON p.id = c.profesional_id
            LEFT JOIN consentimiento_revocacion r ON r.consentimiento_id = c.id
            LEFT JOIN profesional rp ON rp.id = r.revocado_por""";

    private final JdbcTemplate jdbc;

    RegistroConsentimientosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> dniDelPaciente(Long pacienteId) {
        return jdbc.queryForList("SELECT dni FROM paciente WHERE id = ?", String.class, pacienteId).stream()
                .findFirst();
    }

    @Override
    public Optional<PlantillaParaFirmar> plantillaParaFirmar(long plantillaId) {
        // FOR SHARE solo sobre la plantilla: una edición (FOR UPDATE) o una desactivación espera a que termine la firma.
        return jdbc.query("""
                        SELECT p.id, p.activa, v.id AS version_id, v.numero, v.titulo, v.texto
                        FROM plantilla_consentimiento p
                        JOIN plantilla_consentimiento_version v ON v.plantilla_id = p.id
                        WHERE p.id = ?
                        ORDER BY v.numero DESC
                        LIMIT 1
                        FOR SHARE OF p""",
                (rs, fila) -> new PlantillaParaFirmar(rs.getLong("id"), rs.getBoolean("activa"),
                        rs.getLong("version_id"), rs.getInt("numero"), rs.getString("titulo"), rs.getString("texto")),
                plantillaId).stream().findFirst();
    }

    @Override
    public long registrar(ConsentimientoFirmado c, String hash) {
        return jdbc.queryForObject("INSERT INTO consentimiento_firmado(paciente_id, plantilla_version_id,"
                        + " profesional_id, titulo, texto, firmante_nombre, firmante_dni, firmado_en, ip_origen,"
                        + " dispositivo, tipo_firma, formato_canonico, hash_algoritmo, hash)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id", Long.class,
                c.pacienteId(), c.plantillaVersionId(), c.profesionalId(), c.titulo(), c.texto(), c.firmanteNombre(),
                c.firmanteDni(), Timestamp.from(c.firmadoEn()), c.ipOrigen(), c.dispositivo(),
                ConsentimientoFirmado.TIPO_FIRMA, ContenidoCanonico.FORMATO, ContenidoCanonico.ALGORITMO, hash);
    }

    @Override
    public List<Consentimiento> listar(Long pacienteId) {
        return jdbc.query(SELECT + " WHERE c.paciente_id = ? ORDER BY c.firmado_en DESC, c.id DESC",
                (rs, fila) -> consentimiento(rs), pacienteId);
    }

    @Override
    public Optional<Consentimiento> buscar(Long pacienteId, Long consentimientoId) {
        return jdbc.query(SELECT + " WHERE c.id = ? AND c.paciente_id = ?", (rs, fila) -> consentimiento(rs),
                consentimientoId, pacienteId).stream().findFirst();
    }

    @Override
    public boolean revocar(Long consentimientoId, Long profesionalId, Instant momento, String motivo) {
        try {
            jdbc.update("INSERT INTO consentimiento_revocacion(consentimiento_id, revocado_en, revocado_por, motivo)"
                    + " VALUES (?,?,?,?)", consentimientoId, Timestamp.from(momento), profesionalId, motivo);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    private static Consentimiento consentimiento(ResultSet rs) throws SQLException {
        Timestamp revocadoEn = rs.getTimestamp("revocado_en");
        Revocacion revocacion = revocadoEn == null ? null : new Revocacion(rs.getLong("revocado_por"),
                rs.getString("revocado_por_nombre"), revocadoEn.toInstant(), rs.getString("motivo"));
        return new Consentimiento(rs.getLong("id"), rs.getLong("paciente_id"), rs.getLong("plantilla_id"),
                rs.getLong("plantilla_version_id"), rs.getInt("version_numero"), rs.getString("titulo"),
                rs.getString("texto"), rs.getString("firmante_nombre"), rs.getString("firmante_dni"),
                rs.getLong("profesional_id"), rs.getString("profesional"), rs.getTimestamp("firmado_en").toInstant(),
                rs.getString("ip_origen"), rs.getString("dispositivo"), rs.getString("tipo_firma"),
                rs.getInt("formato_canonico"), rs.getString("hash_algoritmo"), rs.getString("hash"), revocacion);
    }
}
