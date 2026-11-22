package ar.turnos.odontologia.reserva.infrastructure;

import ar.turnos.odontologia.agenda.application.CancelarTurnoCommand;
import ar.turnos.odontologia.agenda.application.CrearTurnoCommand;
import ar.turnos.odontologia.agenda.application.ReprogramarTurnoCommand;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.agenda.infrastructure.ServicioAgenda;
import ar.turnos.odontologia.reserva.application.AgendaDeTurnos;
import org.springframework.stereotype.Component;

/**
 * Adaptador de {@link AgendaDeTurnos} sobre {@link ServicioAgenda}, el borde transaccional de la agenda (design D1):
 * la reserva online escribe turnos por el mismo camino que el personal, con la validación del dominio, los
 * {@code EXCLUDE} de PostgreSQL y la auditoría.
 */
@Component
class AgendaDeTurnosServicio implements AgendaDeTurnos {

    private final ServicioAgenda agenda;

    AgendaDeTurnosServicio(ServicioAgenda agenda) {
        this.agenda = agenda;
    }

    @Override
    public Turno crear(CrearTurnoCommand comando, String actor) {
        return agenda.crear(comando, actor);
    }

    @Override
    public Turno cancelar(CancelarTurnoCommand comando, String actor) {
        return agenda.cancelar(comando, actor);
    }

    @Override
    public Turno reprogramar(ReprogramarTurnoCommand comando, String actor) {
        return agenda.reprogramar(comando, actor);
    }
}
