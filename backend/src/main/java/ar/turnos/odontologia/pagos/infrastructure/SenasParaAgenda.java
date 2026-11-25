package ar.turnos.odontologia.pagos.infrastructure;

import ar.turnos.odontologia.agenda.application.SenasDeAgenda;
import org.springframework.jdbc.core.JdbcTemplate;

/** Adaptador del puerto {@link SenasDeAgenda}: si el turno tiene una seña pagada (RN-SE-06, design D2). */
class SenasParaAgenda implements SenasDeAgenda {

    private final JdbcTemplate jdbc;

    SenasParaAgenda(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean tieneSenaPagada(Long turnoId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM sena WHERE turno_id = ? AND estado = 'PAGADA')", Boolean.class, turnoId));
    }
}
