package ar.turnos.odontologia.clinica.infrastructure;

import ar.turnos.odontologia.clinica.application.RegistroClinico;
import ar.turnos.odontologia.clinica.domain.CaraDental;
import ar.turnos.odontologia.clinica.domain.EstadoPieza;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de {@link RegistroClinico}. La ficha y los registros del odontograma se escriben con JPA; las lecturas
 * del odontograma van por SQL, con {@code DISTINCT ON} para el estado vigente y el nombre del profesional.
 */
@Repository
class RegistroClinicoJpa implements RegistroClinico {

    /** El último registro de cada pieza y cara; {@code DISTINCT ON} trata las caras nulas como iguales (D3). */
    private static final String VIGENTE = """
            SELECT * FROM (
                SELECT DISTINCT ON (o.pieza, o.cara) o.id, o.pieza, o.cara, o.estado, o.profesional_id,
                       p.nombre || ' ' || p.apellido AS profesional, o.registrado_en
                FROM odontograma_registro o JOIN profesional p ON p.id = o.profesional_id
                WHERE o.paciente_id = ?
                ORDER BY o.pieza, o.cara, o.registrado_en DESC, o.id DESC
            ) vigente ORDER BY pieza, cara NULLS FIRST""";

    private static final String HISTORIAL = """
            SELECT o.id, o.pieza, o.cara, o.estado, o.profesional_id,
                   p.nombre || ' ' || p.apellido AS profesional, o.registrado_en
            FROM odontograma_registro o JOIN profesional p ON p.id = o.profesional_id
            WHERE o.paciente_id = ?
            ORDER BY o.registrado_en DESC, o.id DESC""";

    private final JdbcTemplate jdbc;
    private final FichaClinicaJpaRepository fichas;
    private final OdontogramaRegistroJpaRepository odontograma;

    RegistroClinicoJpa(JdbcTemplate jdbc, FichaClinicaJpaRepository fichas,
                       OdontogramaRegistroJpaRepository odontograma) {
        this.jdbc = jdbc;
        this.fichas = fichas;
        this.odontograma = odontograma;
    }

    @Override
    public boolean existePaciente(Long pacienteId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM paciente WHERE id = ?)", Boolean.class, pacienteId));
    }

    @Override
    public Optional<Ficha> ficha(Long pacienteId) {
        return fichas.findByPacienteId(pacienteId).map(f -> new Ficha(f.pacienteId(), f.antecedentes(),
                f.alergias(), f.medicacion(), f.actualizadoPor(), f.actualizadoEn()));
    }

    @Override
    public Ficha guardarFicha(Ficha ficha) {
        FichaClinicaEntity entidad = fichas.findByPacienteId(ficha.pacienteId())
                .orElseGet(() -> new FichaClinicaEntity(ficha.pacienteId()));
        entidad.reemplazar(ficha.antecedentes(), ficha.alergias(), ficha.medicacion(), ficha.actualizadoPor(),
                ficha.actualizadoEn());
        fichas.saveAndFlush(entidad);
        return ficha;
    }

    @Override
    public void registrarCambios(Long pacienteId, Long profesionalId, Instant momento, List<Cambio> cambios) {
        odontograma.saveAll(cambios.stream()
                .map(c -> new OdontogramaRegistroEntity(pacienteId, c.pieza().numero(),
                        c.cara() == null ? null : c.cara().name(), c.estado().name(), profesionalId, momento))
                .toList());
    }

    @Override
    public List<RegistroOdontograma> vigente(Long pacienteId) {
        return jdbc.query(VIGENTE, RegistroClinicoJpa::registro, pacienteId);
    }

    @Override
    public List<RegistroOdontograma> historial(Long pacienteId) {
        return jdbc.query(HISTORIAL, RegistroClinicoJpa::registro, pacienteId);
    }

    private static RegistroOdontograma registro(ResultSet rs, int fila) throws SQLException {
        String cara = rs.getString("cara");
        return new RegistroOdontograma(rs.getLong("id"), rs.getInt("pieza"),
                cara == null ? null : CaraDental.valueOf(cara), EstadoPieza.valueOf(rs.getString("estado")),
                rs.getLong("profesional_id"), rs.getString("profesional"),
                rs.getTimestamp("registrado_en").toInstant());
    }
}
