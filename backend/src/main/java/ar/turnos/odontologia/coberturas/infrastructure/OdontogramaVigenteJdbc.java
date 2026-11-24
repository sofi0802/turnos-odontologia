package ar.turnos.odontologia.coberturas.infrastructure;

import ar.turnos.odontologia.coberturas.application.OdontogramaVigente;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de solo lectura de {@link OdontogramaVigente}: lee {@code odontograma_registro} (de {@code clinica/},
 * V8) con {@code DISTINCT ON}, el mismo índice {@code odontograma_registro_vigente_idx} que usa la ficha clínica. No
 * escribe ni depende de código de {@code clinica/}: si esa tabla cambia, el test de sugerencias lo detecta.
 */
@Repository
class OdontogramaVigenteJdbc implements OdontogramaVigente {

    private final JdbcTemplate jdbc;

    OdontogramaVigenteJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PiezaVigente> aTratar(Long pacienteId) {
        return jdbc.query("""
                SELECT pieza, cara, estado FROM (
                    SELECT DISTINCT ON (pieza, cara) pieza, cara, estado
                    FROM odontograma_registro WHERE paciente_id = ?
                    ORDER BY pieza, cara, registrado_en DESC, id DESC) vigente
                WHERE estado IN ('A_TRATAR', 'CARIES')
                ORDER BY pieza, cara NULLS FIRST
                """, (rs, fila) -> new PiezaVigente(rs.getInt("pieza"), rs.getString("cara"), rs.getString("estado")),
                pacienteId);
    }
}
