package ar.turnos.odontologia.clinica.infrastructure;

import ar.turnos.odontologia.clinica.application.RegistroPeriodontal;
import ar.turnos.odontologia.clinica.domain.MedicionSitio;
import ar.turnos.odontologia.clinica.domain.PiezaDental;
import ar.turnos.odontologia.clinica.domain.PiezaPeriodontal;
import ar.turnos.odontologia.clinica.domain.SitioPeriodontal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Adaptador JDBC de {@link RegistroPeriodontal} sobre las tablas de V16 (C-25, design D3 y D6). Solo inserta y lee:
 * los triggers de la base rechazan cualquier {@code UPDATE} o {@code DELETE}. Corre dentro de la transacción del
 * servicio, así que una versión queda entera o no queda.
 */
@Repository
class RegistroPeriodontalJdbc implements RegistroPeriodontal {

    private static final String RESUMEN = """
            SELECT v.id, v.profesional_id, p.nombre || ' ' || p.apellido AS profesional, v.registrado_en
            FROM periodontograma_version v JOIN profesional p ON p.id = v.profesional_id""";

    private final JdbcTemplate jdbc;

    RegistroPeriodontalJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long registrar(Long pacienteId, Long profesionalId, Instant momento, List<PiezaPeriodontal> piezas) {
        Long versionId = jdbc.queryForObject("INSERT INTO periodontograma_version(paciente_id, profesional_id,"
                + " registrado_en) VALUES (?,?,?) RETURNING id", Long.class,
                pacienteId, profesionalId, Timestamp.from(momento));
        for (PiezaPeriodontal pieza : piezas) {
            Long piezaId = jdbc.queryForObject("INSERT INTO periodontograma_pieza(version_id, pieza, ausente,"
                    + " movilidad, furcacion) VALUES (?,?,?,?,?) RETURNING id", Long.class,
                    versionId, pieza.pieza().numero(), pieza.ausente(), pieza.movilidad(), pieza.furcacion());
            List<Object[]> sitios = pieza.sitios().stream()
                    .map(s -> new Object[] {piezaId, s.sitio().name(), s.profundidadSondaje(), s.recesion(),
                            s.sangrado(), s.placa()})
                    .toList();
            jdbc.batchUpdate("INSERT INTO periodontograma_sitio(pieza_id, sitio, profundidad_sondaje, recesion,"
                    + " sangrado, placa) VALUES (?,?,?,?,?,?)", sitios);
        }
        return versionId;
    }

    @Override
    public List<ResumenVersion> versiones(Long pacienteId) {
        return jdbc.query(RESUMEN + " WHERE v.paciente_id = ? ORDER BY v.registrado_en DESC, v.id DESC",
                (rs, fila) -> resumen(rs), pacienteId);
    }

    @Override
    public Optional<Version> version(Long pacienteId, Long versionId) {
        List<ResumenVersion> encontrada = jdbc.query(RESUMEN + " WHERE v.id = ? AND v.paciente_id = ?",
                (rs, fila) -> resumen(rs), versionId, pacienteId);
        if (encontrada.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Version(encontrada.get(0), piezas(versionId)));
    }

    /** Las piezas en orden FDI, con sus sitios en el orden de {@link SitioPeriodontal}. */
    private List<PiezaPeriodontal> piezas(Long versionId) {
        Map<Long, Fila> filas = new LinkedHashMap<>();
        jdbc.query("SELECT id, pieza, ausente, movilidad, furcacion FROM periodontograma_pieza WHERE version_id = ?"
                        + " ORDER BY pieza",
                rs -> {
                    filas.put(rs.getLong("id"), new Fila(rs.getInt("pieza"), rs.getBoolean("ausente"),
                            rs.getObject("movilidad", Integer.class),
                            rs.getObject("furcacion", Integer.class), new ArrayList<>()));
                },
                versionId);
        jdbc.query("SELECT s.pieza_id, s.sitio, s.profundidad_sondaje, s.recesion, s.sangrado, s.placa"
                        + " FROM periodontograma_sitio s JOIN periodontograma_pieza p ON p.id = s.pieza_id"
                        + " WHERE p.version_id = ?",
                rs -> {
                    filas.get(rs.getLong("pieza_id")).sitios().add(new MedicionSitio(
                            SitioPeriodontal.valueOf(rs.getString("sitio")), rs.getInt("profundidad_sondaje"),
                            rs.getInt("recesion"), rs.getBoolean("sangrado"), rs.getBoolean("placa")));
                },
                versionId);
        return filas.values().stream().map(Fila::aPieza).toList();
    }

    private static ResumenVersion resumen(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ResumenVersion(rs.getLong("id"), rs.getLong("profesional_id"), rs.getString("profesional"),
                rs.getTimestamp("registrado_en").toInstant());
    }

    private record Fila(int pieza, boolean ausente, Integer movilidad, Integer furcacion,
                        List<MedicionSitio> sitios) {

        PiezaPeriodontal aPieza() {
            List<MedicionSitio> ordenados = sitios.stream()
                    .sorted(Comparator.comparing(MedicionSitio::sitio)).toList();
            return PiezaPeriodontal.de(PiezaDental.de(pieza), ausente, movilidad, furcacion, ordenados);
        }
    }
}
