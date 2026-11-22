package ar.turnos.odontologia.reserva.application;

import ar.turnos.odontologia.agenda.domain.EstadoTurno;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de lectura (design D1, D10): los turnos de un paciente. Toda búsqueda lleva el id del paciente de la
 * sesión, así un número de reserva ajeno es indistinguible de uno inexistente.
 */
public interface ReservasPaciente {

    /** @param numeroNormalizado el número en mayúsculas y sin espacios en los bordes */
    Optional<TurnoDelPaciente> buscar(String numeroNormalizado, Long pacienteId);

    /** Turnos del paciente en cualquier estado, ordenados por inicio. */
    List<TurnoDelPaciente> turnosDe(Long pacienteId);

    /** DNI del paciente, para componer los casos de uso del personal (RN-AG-07). */
    Optional<String> dni(Long pacienteId);

    /** Vista del turno para el paciente: sin sillón ni datos de otros pacientes. */
    record TurnoDelPaciente(
            Long turnoId,
            String numeroReserva,
            Instant inicio,
            Instant fin,
            EstadoTurno estado,
            Long prestacionId,
            String prestacionNombre,
            Long profesionalId,
            String profesionalNombre) {}
}
