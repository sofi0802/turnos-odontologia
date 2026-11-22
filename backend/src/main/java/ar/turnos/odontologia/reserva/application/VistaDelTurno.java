package ar.turnos.odontologia.reserva.application;

import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.reserva.application.ReservasPaciente.TurnoDelPaciente;

/** Arma lo que el paciente ve de un turno recién escrito: sin sillón ni datos de otros pacientes. */
final class VistaDelTurno {

    private VistaDelTurno() {}

    /** El mismo turno del paciente con el estado y el horario que devolvió la agenda (por ejemplo, ya cancelado). */
    static TurnoDelPaciente de(Turno turno, TurnoDelPaciente original) {
        return new TurnoDelPaciente(turno.id(), turno.numeroReserva().valor(), turno.intervalo().inicio(),
                turno.intervalo().fin(), turno.estado(), original.prestacionId(), original.prestacionNombre(),
                original.profesionalId(), original.profesionalNombre());
    }

    static TurnoDelPaciente de(Turno turno, String prestacionNombre, CatalogoReserva catalogo) {
        String profesional = catalogo.profesional(turno.profesionalId())
                .map(CatalogoReserva.Profesional::nombre)
                .orElseThrow(() -> new IllegalStateException("El turno apunta a un profesional que no existe"));
        return new TurnoDelPaciente(turno.id(), turno.numeroReserva().valor(), turno.intervalo().inicio(),
                turno.intervalo().fin(), turno.estado(), turno.prestacionId(), prestacionNombre,
                turno.profesionalId(), profesional);
    }
}
