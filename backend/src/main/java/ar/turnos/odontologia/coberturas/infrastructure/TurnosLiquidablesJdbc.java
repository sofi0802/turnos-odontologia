package ar.turnos.odontologia.coberturas.infrastructure;

import ar.turnos.odontologia.coberturas.application.TurnosLiquidables;
import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion.Candidato;
import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion.CodigoCubierto;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de solo lectura de {@link TurnosLiquidables} (C-20, design D4 paso 2): un {@code SELECT} de los turnos
 * atendidos con la afiliación vigente en su fecha, y dos más para los códigos activos de las prestaciones y la cobertura
 * de los planes. No escribe ni usa código de {@code agenda/} ni de pacientes. Nada de lo que lee va a logs.
 */
@Repository
class TurnosLiquidablesJdbc implements TurnosLiquidables {

    private static final String FECHA_DEL_TURNO = "(t.inicio AT TIME ZONE 'America/Argentina/Buenos_Aires')::date";

    private static final String CANDIDATOS = """
            SELECT t.id AS turno_id, t.paciente_id, t.prestacion_id, %s AS fecha,
                   p.nombre, p.apellido, p.dni, a.numero_afiliado, pl.id AS plan_id, pl.nombre AS plan_nombre,
                   pr.nombre AS prestacion_nombre, ar.precio
            FROM turno t
            JOIN paciente p ON p.id = t.paciente_id
            JOIN afiliacion_paciente a ON a.paciente_id = t.paciente_id AND a.obra_social_id = ?
                 AND %s >= a.vigente_desde AND (a.vigente_hasta IS NULL OR %s <= a.vigente_hasta)
            JOIN plan pl ON pl.id = a.plan_id
            JOIN prestacion pr ON pr.id = t.prestacion_id
            LEFT JOIN arancel ar ON ar.prestacion_id = t.prestacion_id
            WHERE t.estado = 'ATENDIDO' AND t.inicio < ?
              AND NOT EXISTS (SELECT 1 FROM liquidacion_linea l WHERE l.turno_id = t.id AND l.liquidacion_id <> ?)
              AND NOT EXISTS (SELECT 1 FROM liquidacion_turno_quitado q
                              WHERE q.turno_id = t.id AND q.liquidacion_id = ?)
            ORDER BY t.inicio, t.id
            """.formatted(FECHA_DEL_TURNO, FECHA_DEL_TURNO, FECHA_DEL_TURNO);

    private final JdbcTemplate jdbc;

    TurnosLiquidablesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Candidato> candidatos(Long obraSocialId, Instant finExclusivo, Long liquidacionId) {
        List<Fila> filas = jdbc.query(CANDIDATOS, TurnosLiquidablesJdbc::fila, obraSocialId,
                OffsetDateTime.ofInstant(finExclusivo, ZoneOffset.UTC), liquidacionId, liquidacionId);
        if (filas.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Codigo>> codigosPorPrestacion = codigosActivos(
                filas.stream().map(Fila::prestacionId).collect(Collectors.toCollection(java.util.LinkedHashSet::new)));
        Map<String, Integer> cobertura = coberturas(
                filas.stream().map(Fila::planId).collect(Collectors.toCollection(java.util.LinkedHashSet::new)));
        List<Candidato> candidatos = new ArrayList<>();
        for (Fila f : filas) {
            List<CodigoCubierto> codigos = codigosPorPrestacion.getOrDefault(f.prestacionId(), List.of()).stream()
                    .map(c -> new CodigoCubierto(c.id(), c.codigo(),
                            cobertura.getOrDefault(f.planId() + ":" + c.id(), 0)))
                    .toList();
            candidatos.add(new Candidato(f.turnoId(), f.pacienteId(), f.planId(), f.prestacionId(), f.fecha(),
                    f.nombre() + " " + f.apellido(), f.dni(), f.numeroAfiliado(), f.planNombre(),
                    f.prestacionNombre(), f.arancel(), codigos));
        }
        return candidatos;
    }

    /** Los códigos activos de cada prestación, ordenados por código sin distinguir mayúsculas. */
    private Map<Long, List<Codigo>> codigosActivos(Set<Long> prestaciones) {
        Map<Long, List<Codigo>> porPrestacion = new LinkedHashMap<>();
        jdbc.query("SELECT n.prestacion_id, n.id, n.codigo FROM nomenclador n WHERE n.activo AND n.prestacion_id IN ("
                + marcas(prestaciones.size()) + ") ORDER BY lower(n.codigo), n.id",
                rs -> {
                    porPrestacion.computeIfAbsent(rs.getLong("prestacion_id"), k -> new ArrayList<>())
                            .add(new Codigo(rs.getLong("id"), rs.getString("codigo")));
                }, prestaciones.toArray());
        return porPrestacion;
    }

    /** El porcentaje de cada par plan y código con fila en {@code cobertura_plan}; sin fila es 0 %. */
    private Map<String, Integer> coberturas(Set<Long> planes) {
        Map<String, Integer> porcentajes = new HashMap<>();
        jdbc.query("SELECT plan_id, nomenclador_id, porcentaje FROM cobertura_plan WHERE plan_id IN ("
                + marcas(planes.size()) + ")",
                rs -> {
                    porcentajes.put(rs.getLong("plan_id") + ":" + rs.getLong("nomenclador_id"), rs.getInt("porcentaje"));
                }, planes.toArray());
        return porcentajes;
    }

    private static String marcas(int cantidad) {
        return String.join(",", Collections.nCopies(cantidad, "?"));
    }

    private static Fila fila(ResultSet rs, int numero) throws SQLException {
        return new Fila(rs.getLong("turno_id"), rs.getLong("paciente_id"), rs.getLong("prestacion_id"),
                rs.getObject("fecha", LocalDate.class), rs.getString("nombre"), rs.getString("apellido"),
                rs.getString("dni"), rs.getString("numero_afiliado"), rs.getLong("plan_id"),
                rs.getString("plan_nombre"), rs.getString("prestacion_nombre"), rs.getBigDecimal("precio"));
    }

    private record Fila(Long turnoId, Long pacienteId, Long prestacionId, LocalDate fecha, String nombre,
                        String apellido, String dni, String numeroAfiliado, Long planId, String planNombre,
                        String prestacionNombre, BigDecimal arancel) {}

    private record Codigo(Long id, String codigo) {}
}
