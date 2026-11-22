package ar.turnos.odontologia.reserva.api;

import ar.turnos.odontologia.reserva.application.ReservasPaciente.TurnoDelPaciente;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Un turno visto por su paciente: número de reserva, horario, estado, prestación y profesional. Nunca el sillón ni
 * datos de otros pacientes. {@code modificableOnline} solo aparece en la lista de "mis turnos".
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record TurnoPacienteResponse(
        String numeroReserva,
        OffsetDateTime inicio,
        OffsetDateTime fin,
        String estado,
        Referencia prestacion,
        Referencia profesional,
        Boolean modificableOnline) {

    record Referencia(Long id, String nombre) {}

    static TurnoPacienteResponse de(TurnoDelPaciente turno, ZoneId zona, Boolean modificableOnline) {
        return new TurnoPacienteResponse(turno.numeroReserva(),
                turno.inicio().atZone(zona).toOffsetDateTime(), turno.fin().atZone(zona).toOffsetDateTime(),
                turno.estado().name(), new Referencia(turno.prestacionId(), turno.prestacionNombre()),
                new Referencia(turno.profesionalId(), turno.profesionalNombre()), modificableOnline);
    }
}
