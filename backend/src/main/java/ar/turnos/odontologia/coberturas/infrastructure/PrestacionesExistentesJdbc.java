package ar.turnos.odontologia.coberturas.infrastructure;

import ar.turnos.odontologia.coberturas.application.PrestacionesExistentes;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Adaptador de lectura de {@link PrestacionesExistentes}: solo lee {@code prestacion}, no toca el módulo ajeno. */
@Repository
class PrestacionesExistentesJdbc implements PrestacionesExistentes {

    private final JdbcTemplate jdbc;

    PrestacionesExistentesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<Long, String> nombres(Collection<Long> ids) {
        Map<Long, String> nombres = new HashMap<>();
        if (ids.isEmpty()) {
            return nombres;
        }
        String marcas = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<Object[]> filas = jdbc.query("SELECT id, nombre FROM prestacion WHERE id IN (" + marcas + ")",
                (rs, fila) -> new Object[] {rs.getLong("id"), rs.getString("nombre")}, ids.toArray());
        filas.forEach(f -> nombres.put((Long) f[0], (String) f[1]));
        return nombres;
    }
}
