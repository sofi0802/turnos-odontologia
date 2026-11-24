package ar.turnos.odontologia.coberturas.infrastructure;

import ar.turnos.odontologia.coberturas.application.PrestacionesPresupuestables;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de {@link PrestacionesPresupuestables}: solo lee {@code prestacion} (de {@code configuracion/}),
 * {@code arancel} y {@code nomenclador}. No toca el módulo ajeno.
 */
@Repository
class PrestacionesPresupuestablesJdbc implements PrestacionesPresupuestables {

    private final JdbcTemplate jdbc;

    PrestacionesPresupuestablesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<Long, Presupuestable> de(Collection<Long> prestacionIds) {
        Map<Long, Presupuestable> resultado = new HashMap<>();
        if (prestacionIds.isEmpty()) {
            return resultado;
        }
        String marcas = String.join(",", Collections.nCopies(prestacionIds.size(), "?"));
        Object[] ids = prestacionIds.toArray();
        Map<Long, List<CodigoActivo>> codigos = new HashMap<>();
        jdbc.query("SELECT id, prestacion_id, codigo FROM nomenclador WHERE activo AND prestacion_id IN (" + marcas
                + ") ORDER BY upper(codigo), id", rs -> {
                    codigos.computeIfAbsent(rs.getLong("prestacion_id"), k -> new ArrayList<>())
                            .add(new CodigoActivo(rs.getLong("id"), rs.getString("codigo")));
                }, ids);
        jdbc.query("SELECT p.id, p.nombre, p.activo, a.precio FROM prestacion p "
                + "LEFT JOIN arancel a ON a.prestacion_id = p.id WHERE p.id IN (" + marcas + ")", rs -> {
                    long id = rs.getLong("id");
                    BigDecimal precio = rs.getBigDecimal("precio");
                    resultado.put(id, new Presupuestable(id, rs.getString("nombre"), rs.getBoolean("activo"), precio,
                            codigos.getOrDefault(id, List.of())));
                }, ids);
        return resultado;
    }
}
