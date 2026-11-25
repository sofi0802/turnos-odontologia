package ar.turnos.odontologia.agenda.application;

import java.util.Objects;

/**
 * Datos para cancelar un turno: el DNI del paciente y el número de reserva lo identifican.
 *
 * @param causa C-23: {@code PACIENTE} o {@code CONSULTORIO}, obligatoria si el turno tiene una seña pagada (RN-SE-06)
 */
public record CancelarTurnoCommand(Long turnoId, String dni, String numeroReserva, String causa) {

    public CancelarTurnoCommand {
        Objects.requireNonNull(turnoId, "turnoId");
    }

    /** Sin causa, como antes de C-23. */
    public CancelarTurnoCommand(Long turnoId, String dni, String numeroReserva) {
        this(turnoId, dni, numeroReserva, null);
    }
}
