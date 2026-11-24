package ar.turnos.odontologia.usuarios.infrastructure;

import ar.turnos.odontologia.usuarios.application.ProfesionalesParaCuentas;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Lectura de la tabla profesional para validar el vínculo de una cuenta, sin tocar configuracion/ (C-41 D6). */
@Repository
class ProfesionalesParaCuentasJdbc implements ProfesionalesParaCuentas {

    private final JdbcTemplate jdbc;

    ProfesionalesParaCuentasJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean estaActivo(long profesionalId) {
        List<Boolean> activo = jdbc.queryForList("SELECT activo FROM profesional WHERE id = ?", Boolean.class,
                profesionalId);
        return !activo.isEmpty() && Boolean.TRUE.equals(activo.get(0));
    }
}
