package ar.turnos.odontologia.clinica.infrastructure;

import ar.turnos.odontologia.clinica.application.RegistroPlantillas;
import ar.turnos.odontologia.clinica.domain.VersionPlantilla;
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
 * Adaptador JDBC de {@link RegistroPlantillas} sobre las tablas de V18 (C-27, design D5, D6 y D10). Solo inserta
 * versiones y cambia {@code activa}; los triggers de la base rechazan cualquier otro cambio.
 */
@Repository
class RegistroPlantillasJdbc implements RegistroPlantillas {

    private static final String VERSION = """
            SELECT v.id, v.numero, v.titulo, v.texto, v.creada_en, u.username AS creada_por
            FROM plantilla_consentimiento_version v
            JOIN usuario u ON u.id = v.creada_por""";

    /** Cada plantilla con su última versión. */
    private static final String PLANTILLA = """
            SELECT p.id AS plantilla_id, p.activa, v.id, v.numero, v.titulo, v.texto, v.creada_en,
                   u.username AS creada_por
            FROM plantilla_consentimiento p
            JOIN plantilla_consentimiento_version v ON v.plantilla_id = p.id
                 AND v.numero = (SELECT max(numero) FROM plantilla_consentimiento_version WHERE plantilla_id = p.id)
            JOIN usuario u ON u.id = v.creada_por""";

    private final JdbcTemplate jdbc;

    RegistroPlantillasJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long crear(Long usuarioId, Instant momento, VersionPlantilla contenido) {
        Long id = jdbc.queryForObject("INSERT INTO plantilla_consentimiento(creada_en, creada_por) VALUES (?,?)"
                + " RETURNING id", Long.class, Timestamp.from(momento), usuarioId);
        insertarVersion(id, 1, contenido, usuarioId, momento);
        return id;
    }

    @Override
    public Optional<Plantilla> buscar(long plantillaId) {
        return jdbc.query(PLANTILLA + " WHERE p.id = ?", (rs, fila) -> plantilla(rs), plantillaId).stream().findFirst();
    }

    @Override
    public Optional<Plantilla> bloquearParaEditar(long plantillaId) {
        List<Long> bloqueada = jdbc.queryForList("SELECT id FROM plantilla_consentimiento WHERE id = ? FOR UPDATE",
                Long.class, plantillaId);
        return bloqueada.isEmpty() ? Optional.empty() : buscar(plantillaId);
    }

    @Override
    public boolean agregarVersion(long plantillaId, int numero, VersionPlantilla contenido, Long usuarioId,
                                  Instant momento) {
        try {
            insertarVersion(plantillaId, numero, contenido, usuarioId, momento);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    public boolean cambiarEstado(long plantillaId, boolean activa) {
        return jdbc.update("UPDATE plantilla_consentimiento SET activa = ? WHERE id = ?", activa, plantillaId) == 1;
    }

    @Override
    public List<Plantilla> listar(boolean incluirInactivas) {
        String filtro = incluirInactivas ? "" : " WHERE p.activa";
        return jdbc.query(PLANTILLA + filtro + " ORDER BY v.titulo, p.id", (rs, fila) -> plantilla(rs));
    }

    @Override
    public List<Version> versiones(long plantillaId) {
        return jdbc.query(VERSION + " WHERE v.plantilla_id = ? ORDER BY v.numero DESC", (rs, fila) -> version(rs),
                plantillaId);
    }

    @Override
    public Optional<Version> version(long plantillaId, int numero) {
        return jdbc.query(VERSION + " WHERE v.plantilla_id = ? AND v.numero = ?", (rs, fila) -> version(rs),
                plantillaId, numero).stream().findFirst();
    }

    private void insertarVersion(long plantillaId, int numero, VersionPlantilla contenido, Long usuarioId,
                                 Instant momento) {
        jdbc.update("INSERT INTO plantilla_consentimiento_version(plantilla_id, numero, titulo, texto, creada_en,"
                        + " creada_por) VALUES (?,?,?,?,?,?)", plantillaId, numero, contenido.titulo(), contenido.texto(),
                Timestamp.from(momento), usuarioId);
    }

    private static Plantilla plantilla(ResultSet rs) throws SQLException {
        return new Plantilla(rs.getLong("plantilla_id"), rs.getBoolean("activa"), version(rs));
    }

    private static Version version(ResultSet rs) throws SQLException {
        return new Version(rs.getLong("id"), rs.getInt("numero"), rs.getString("titulo"), rs.getString("texto"),
                rs.getTimestamp("creada_en").toInstant(), rs.getString("creada_por"));
    }
}
