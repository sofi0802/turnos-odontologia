package ar.turnos.odontologia.clinica.infrastructure;

import ar.turnos.odontologia.clinica.application.RegistroImagenes;
import ar.turnos.odontologia.clinica.domain.CategoriaImagen;
import ar.turnos.odontologia.clinica.domain.ImagenClinica;
import ar.turnos.odontologia.clinica.domain.TipoArchivoClinico;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Adaptador JDBC de {@link RegistroImagenes} sobre la tabla de V17 (C-26, design D7 y D8). Inserta, lee y registra la
 * anulación con un {@code UPDATE} condicional ({@code anulada_en IS NULL}): dos anulaciones simultáneas dejan solo
 * la primera. El trigger de la base rechaza cualquier otro cambio.
 */
@Repository
class RegistroImagenesJdbc implements RegistroImagenes {

    private static final String SELECT = """
            SELECT i.id, i.paciente_id, i.categoria, i.pieza, i.tomada_el, i.descripcion, i.tipo_contenido,
                   i.tamano_bytes, i.sha256, i.clave_almacen, i.profesional_id,
                   p.nombre || ' ' || p.apellido AS profesional, i.registrada_en,
                   i.anulada_en, i.anulada_por, a.nombre || ' ' || a.apellido AS anulada_por_nombre, i.motivo_anulacion
            FROM imagen_clinica i
            JOIN profesional p ON p.id = i.profesional_id
            LEFT JOIN profesional a ON a.id = i.anulada_por""";

    private final JdbcTemplate jdbc;

    RegistroImagenesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long registrar(Long pacienteId, Long profesionalId, Instant momento, ImagenClinica imagen, String sha256,
                          String claveAlmacen) {
        return jdbc.queryForObject("INSERT INTO imagen_clinica(paciente_id, profesional_id, categoria, pieza,"
                        + " tomada_el, descripcion, tipo_contenido, tamano_bytes, sha256, clave_almacen, registrada_en)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?) RETURNING id", Long.class,
                pacienteId, profesionalId, imagen.categoria().name(), imagen.pieza(), Date.valueOf(imagen.tomadaEl()),
                imagen.descripcion(), imagen.tipo().tipoContenido(), imagen.tamanoBytes(), sha256, claveAlmacen,
                Timestamp.from(momento));
    }

    @Override
    public List<Imagen> listar(Long pacienteId, Integer pieza, boolean incluirAnuladas) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE i.paciente_id = ?");
        List<Object> parametros = new ArrayList<>(List.of(pacienteId));
        if (pieza != null) {
            sql.append(" AND i.pieza = ?");
            parametros.add(pieza);
        }
        if (!incluirAnuladas) {
            sql.append(" AND i.anulada_en IS NULL");
        }
        sql.append(" ORDER BY i.tomada_el DESC, i.registrada_en DESC, i.id DESC");
        return jdbc.query(sql.toString(), (rs, fila) -> imagen(rs), parametros.toArray());
    }

    @Override
    public Optional<Imagen> buscar(Long pacienteId, Long imagenId) {
        return jdbc.query(SELECT + " WHERE i.id = ? AND i.paciente_id = ?", (rs, fila) -> imagen(rs),
                imagenId, pacienteId).stream().findFirst();
    }

    @Override
    public boolean anular(Long pacienteId, Long imagenId, Long profesionalId, Instant momento, String motivo) {
        return jdbc.update("UPDATE imagen_clinica SET anulada_en = ?, anulada_por = ?, motivo_anulacion = ?"
                        + " WHERE id = ? AND paciente_id = ? AND anulada_en IS NULL",
                Timestamp.from(momento), profesionalId, motivo, imagenId, pacienteId) == 1;
    }

    private static Imagen imagen(ResultSet rs) throws SQLException {
        Timestamp anuladaEn = rs.getTimestamp("anulada_en");
        Anulacion anulacion = anuladaEn == null ? null : new Anulacion(rs.getLong("anulada_por"),
                rs.getString("anulada_por_nombre"), anuladaEn.toInstant(), rs.getString("motivo_anulacion"));
        return new Imagen(rs.getLong("id"), rs.getLong("paciente_id"), CategoriaImagen.valueOf(rs.getString("categoria")),
                rs.getObject("pieza", Integer.class), rs.getDate("tomada_el").toLocalDate(), rs.getString("descripcion"),
                TipoArchivoClinico.deTipoContenido(rs.getString("tipo_contenido")), rs.getInt("tamano_bytes"),
                rs.getString("sha256"), rs.getString("clave_almacen"), rs.getLong("profesional_id"),
                rs.getString("profesional"), rs.getTimestamp("registrada_en").toInstant(), anulacion);
    }
}
