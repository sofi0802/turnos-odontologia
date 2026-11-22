package ar.turnos.odontologia.reserva.application;

import ar.turnos.odontologia.agenda.application.CancelarTurnoCommand;
import ar.turnos.odontologia.agenda.application.CrearTurnoCommand;
import ar.turnos.odontologia.agenda.application.ReprogramarTurnoCommand;
import ar.turnos.odontologia.agenda.domain.Turno;

/**
 * Puerto de salida hacia el borde transaccional de la agenda ({@code ServicioAgenda}). Es la única forma en que la
 * reserva online escribe turnos: así valen la validación del dominio, los {@code EXCLUDE} de PostgreSQL y la
 * auditoría, igual que para el personal (design D1, D5).
 */
public interface AgendaDeTurnos {

    Turno crear(CrearTurnoCommand comando, String actor);

    Turno cancelar(CancelarTurnoCommand comando, String actor);

    Turno reprogramar(ReprogramarTurnoCommand comando, String actor);
}
