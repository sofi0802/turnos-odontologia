package ar.turnos.odontologia.coberturas.infrastructure;

import ar.turnos.odontologia.coberturas.application.CoberturasPlan;
import ar.turnos.odontologia.coberturas.domain.Porcentaje;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Adaptador de {@link CoberturasPlan}: el upsert es una sola sentencia {@code ON CONFLICT} por plan y código. */
@Repository
class CoberturasPlanJdbc implements CoberturasPlan {

    private static final String SELECCION = """
            SELECT n.id, n.codigo, n.descripcion, n.activo, c.porcentaje
            FROM cobertura_plan c JOIN nomenclador n ON n.id = c.nomenclador_id
            WHERE c.plan_id = ?
            """;

    private final JdbcTemplate jdbc;

    CoberturasPlanJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CoberturaFila> delPlan(Long planId) {
        return jdbc.query(SELECCION + "ORDER BY upper(n.codigo), n.id", CoberturasPlanJdbc::fila, planId);
    }

    @Override
    public Optional<CoberturaFila> guardar(Long planId, Long nomencladorId, Porcentaje porcentaje) {
        int filas = jdbc.update("""
                INSERT INTO cobertura_plan (plan_id, nomenclador_id, porcentaje)
                SELECT ?, n.id, ? FROM nomenclador n WHERE n.id = ?
                ON CONFLICT (plan_id, nomenclador_id) DO UPDATE SET porcentaje = EXCLUDED.porcentaje
                """, planId, porcentaje.valor(), nomencladorId);
        if (filas == 0) {
            return Optional.empty();
        }
        return jdbc.query(SELECCION + "AND n.id = ?", CoberturasPlanJdbc::fila, planId, nomencladorId).stream()
                .findFirst();
    }

    @Override
    public Map<Long, Integer> porcentajes(Long planId, Collection<Long> nomencladorIds) {
        Map<Long, Integer> porcentajes = new HashMap<>();
        if (nomencladorIds.isEmpty()) {
            return porcentajes;
        }
        String marcas = String.join(",", Collections.nCopies(nomencladorIds.size(), "?"));
        Object[] args = new Object[nomencladorIds.size() + 1];
        args[0] = planId;
        int i = 1;
        for (Long id : nomencladorIds) {
            args[i++] = id;
        }
        jdbc.query("SELECT nomenclador_id, porcentaje FROM cobertura_plan WHERE plan_id = ? AND nomenclador_id IN ("
                + marcas + ")", rs -> {
                    porcentajes.put(rs.getLong("nomenclador_id"), rs.getInt("porcentaje"));
                }, args);
        return porcentajes;
    }

    private static CoberturaFila fila(ResultSet rs, int numero) throws SQLException {
        return new CoberturaFila(rs.getLong("id"), rs.getString("codigo"), rs.getString("descripcion"),
                rs.getBoolean("activo"), rs.getInt("porcentaje"));
    }
}
