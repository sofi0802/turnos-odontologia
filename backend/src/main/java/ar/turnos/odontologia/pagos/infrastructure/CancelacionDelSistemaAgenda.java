package ar.turnos.odontologia.pagos.infrastructure;

import ar.turnos.odontologia.agenda.application.CancelarTurnoCommand;
import ar.turnos.odontologia.agenda.infrastructure.ServicioAgenda;
import ar.turnos.odontologia.pagos.application.CancelacionDelSistema;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Cancela un turno por la agenda con el actor {@code sistema}. La agenda pide el DNI y el número de reserva del turno
 * (RN-AG-07): se leen del propio turno, nunca de un pedido.
 */
class CancelacionDelSistemaAgenda implements CancelacionDelSistema {

    private final JdbcTemplate jdbc;
    private final ServicioAgenda agenda;

    CancelacionDelSistemaAgenda(JdbcTemplate jdbc, ServicioAgenda agenda) {
        this.jdbc = jdbc;
        this.agenda = agenda;
    }

    @Override
    public void cancelar(long turnoId) {
        Map<String, Object> turno = jdbc.queryForMap("SELECT p.dni, t.codigo_reserva FROM turno t "
                + "JOIN paciente p ON p.id = t.paciente_id WHERE t.id = ?", turnoId);
        agenda.cancelar(new CancelarTurnoCommand(turnoId, (String) turno.get("dni"),
                (String) turno.get("codigo_reserva")), ACTOR);
    }
}
