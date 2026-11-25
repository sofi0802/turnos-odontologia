package ar.turnos.odontologia.caja.infrastructure;

import ar.turnos.odontologia.caja.application.TurnosACobrar;
import ar.turnos.odontologia.caja.domain.CodigoActivo;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Turnos atendidos sin cargo y con arancel, con su tarifa (design D2 y D3): la afiliación vigente en la fecha del
 * turno, los códigos activos del nomenclador de la prestación y los porcentajes del plan. Solo lee.
 */
@Repository
class TurnosACobrarJdbc implements TurnosACobrar {

    private final NamedParameterJdbcTemplate jdbc;
    private final ZoneId zona;

    TurnosACobrarJdbc(NamedParameterJdbcTemplate jdbc, ZoneId zonaHoraria) {
        this.jdbc = jdbc;
        this.zona = zonaHoraria;
    }

    @Override
    public List<TurnoACobrar> atendidosSinCargo() {
        record Fila(long turnoId, long pacienteId, long prestacionId, java.time.LocalDate fecha,
                    java.math.BigDecimal precio, Long afiliacionId, Long planId) {}
        List<Fila> filas = jdbc.query("""
                SELECT t.id, t.paciente_id, t.prestacion_id, x.fecha, a.precio, af.id AS afiliacion_id, af.plan_id
                  FROM turno t
                  JOIN arancel a ON a.prestacion_id = t.prestacion_id
                  CROSS JOIN LATERAL (SELECT CAST(t.inicio AT TIME ZONE :zona AS DATE) AS fecha) x
                  LEFT JOIN LATERAL (
                      SELECT ap.id, ap.plan_id FROM afiliacion_paciente ap
                       WHERE ap.paciente_id = t.paciente_id AND ap.vigente_desde <= x.fecha
                         AND (ap.vigente_hasta IS NULL OR ap.vigente_hasta >= x.fecha)
                       ORDER BY ap.vigente_desde DESC, ap.id DESC LIMIT 1) af ON true
                 WHERE t.estado = 'ATENDIDO' AND NOT EXISTS (SELECT 1 FROM cargo c WHERE c.turno_id = t.id)
                 ORDER BY t.id
                """, new MapSqlParameterSource("zona", zona.getId()),
                (rs, n) -> new Fila(rs.getLong("id"), rs.getLong("paciente_id"), rs.getLong("prestacion_id"),
                        rs.getObject("fecha", java.time.LocalDate.class), rs.getBigDecimal("precio"),
                        rs.getObject("afiliacion_id", Long.class), rs.getObject("plan_id", Long.class)));
        Map<Long, List<CodigoActivo>> codigos = new HashMap<>();
        Map<Long, Map<Long, Integer>> porcentajes = new HashMap<>();
        return filas.stream().map(f -> new TurnoACobrar(f.turnoId(), f.pacienteId(), f.prestacionId(), f.fecha(),
                f.precio(), f.afiliacionId(),
                codigos.computeIfAbsent(f.prestacionId(), this::codigosActivos),
                f.planId() == null ? Map.of() : porcentajes.computeIfAbsent(f.planId(), this::porcentajes))).toList();
    }

    private List<CodigoActivo> codigosActivos(long prestacionId) {
        return jdbc.query("SELECT id, codigo FROM nomenclador WHERE prestacion_id = :p AND activo ORDER BY codigo",
                new MapSqlParameterSource("p", prestacionId),
                (rs, n) -> new CodigoActivo(rs.getLong("id"), rs.getString("codigo")));
    }

    private Map<Long, Integer> porcentajes(long planId) {
        Map<Long, Integer> porCodigo = new HashMap<>();
        jdbc.query("SELECT nomenclador_id, porcentaje FROM cobertura_plan WHERE plan_id = :plan",
                new MapSqlParameterSource("plan", planId),
                rs -> {
                    porCodigo.put(rs.getLong("nomenclador_id"), rs.getInt("porcentaje"));
                });
        return porCodigo;
    }
}
