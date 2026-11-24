package ar.turnos.odontologia.agenda.infrastructure;

import ar.turnos.odontologia.agenda.application.PasarAAtendidos.TurnosConLlegada;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de {@link TurnosConLlegada} (C-43, design D2): un solo {@code UPDATE ... RETURNING}. Si dos instancias
 * corren a la vez, solo cambia la fila la que la encuentra activa. Suma uno a {@code version}, como JPA, para que una
 * operación que tenía el turno cargado no lo pise. Pasar a {@code ATENDIDO} libera el turno para las {@code EXCLUDE},
 * por eso la condición es el fin con margen y no el fin.
 */
@Repository
class TurnosConLlegadaJdbc implements TurnosConLlegada {

    private final JdbcTemplate jdbc;

    TurnosConLlegadaJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Long> pasarAAtendidos(Instant ahora) {
        return jdbc.queryForList("""
                UPDATE turno SET estado = 'ATENDIDO', version = version + 1
                WHERE estado IN ('RESERVADO', 'CONFIRMADO') AND llegada_en IS NOT NULL AND fin_con_margen <= ?
                RETURNING id""", Long.class, Timestamp.from(ahora));
    }
}
