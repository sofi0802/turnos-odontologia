package ar.turnos.odontologia.coberturas.infrastructure;

import ar.turnos.odontologia.coberturas.application.PacientesExistentes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Adaptador de lectura de {@link PacientesExistentes}: solo consulta si el id existe en {@code paciente}. */
@Repository
class PacientesExistentesJdbc implements PacientesExistentes {

    private final JdbcTemplate jdbc;

    PacientesExistentesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existe(Long pacienteId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM paciente WHERE id = ?)", Boolean.class, pacienteId));
    }
}
