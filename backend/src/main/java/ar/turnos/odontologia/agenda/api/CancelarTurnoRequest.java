package ar.turnos.odontologia.agenda.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** El personal identifica el turno con el DNI del paciente y el número de reserva (RN-AG-07). */
record CancelarTurnoRequest(
        @NotBlank String dni,
        @NotBlank String numeroReserva,
        // C-23: PACIENTE o CONSULTORIO; obligatoria solo si el turno tiene una seña pagada (RN-SE-06)
        @Pattern(regexp = "PACIENTE|CONSULTORIO", message = "Elegí si la causa es del paciente o del consultorio.")
        String causa) {}
