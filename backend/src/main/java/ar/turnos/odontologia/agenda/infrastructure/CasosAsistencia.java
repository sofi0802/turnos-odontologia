package ar.turnos.odontologia.agenda.infrastructure;

import ar.turnos.odontologia.agenda.application.DeshacerAusencia;
import ar.turnos.odontologia.agenda.application.PasarAAtendidos;
import ar.turnos.odontologia.agenda.application.RegistrarLlegada;

/** C-43: casos de uso de la asistencia que {@link ServicioAgenda} envuelve en una transacción con su auditoría. */
public record CasosAsistencia(RegistrarLlegada registrarLlegada, DeshacerAusencia deshacerAusencia,
                              PasarAAtendidos pasarAAtendidos) {}
