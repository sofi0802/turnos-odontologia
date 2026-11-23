package ar.turnos.odontologia.usuarios.infrastructure;

import ar.turnos.odontologia.usuarios.application.BusquedaPacientes;
import ar.turnos.odontologia.usuarios.domain.PacienteResumen;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Lectura de solo consulta sobre {@code paciente} (design D3 y D8). El {@code SELECT} nombra las cuatro columnas
 * mínimas, así que la consulta no puede traer datos de contacto ni de consentimiento. SQL siempre parametrizado: el
 * término nunca se concatena. La normalización de {@code translate(lower(...))} coincide con la de
 * {@code TerminoBusqueda}.
 */
@Repository
@Transactional(readOnly = true)
class BusquedaPacientesJdbc implements BusquedaPacientes {

    private static final String COLUMNAS = "SELECT id, nombre, apellido, dni FROM paciente";
    private static final String NORMALIZAR = "translate(lower(%s), 'áéíóúüñ', 'aeiouun')";
    private static final String NOMBRE = NORMALIZAR.formatted("nombre");
    private static final String APELLIDO = NORMALIZAR.formatted("apellido");

    private final JdbcTemplate jdbc;

    BusquedaPacientesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PacienteResumen> porDni(String prefijo, int limite) {
        return jdbc.query(COLUMNAS + " WHERE dni LIKE ? ESCAPE '\\' ORDER BY dni LIMIT ?",
                (rs, fila) -> resumen(rs.getLong("id"), rs.getString("nombre"), rs.getString("apellido"),
                        rs.getString("dni")),
                prefijo + "%", limite);
    }

    @Override
    public List<PacienteResumen> porTexto(List<String> palabrasParaLike, int limite) {
        StringBuilder sql = new StringBuilder(COLUMNAS).append(" WHERE true");
        List<Object> parametros = new ArrayList<>();
        for (String palabra : palabrasParaLike) {
            sql.append(" AND (").append(NOMBRE).append(" LIKE ? ESCAPE '\\' OR ")
                    .append(APELLIDO).append(" LIKE ? ESCAPE '\\')");
            String patron = "%" + palabra + "%";
            parametros.add(patron);
            parametros.add(patron);
        }
        sql.append(" ORDER BY ").append(APELLIDO).append(", ").append(NOMBRE).append(", id LIMIT ?");
        parametros.add(limite);
        return jdbc.query(sql.toString(),
                (rs, fila) -> resumen(rs.getLong("id"), rs.getString("nombre"), rs.getString("apellido"),
                        rs.getString("dni")),
                parametros.toArray());
    }

    private static PacienteResumen resumen(long id, String nombre, String apellido, String dni) {
        return new PacienteResumen(id, nombre, apellido, dni);
    }
}
