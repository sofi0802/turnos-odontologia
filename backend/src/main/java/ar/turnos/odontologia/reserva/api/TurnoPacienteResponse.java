package ar.turnos.odontologia.reserva.api;

import ar.turnos.odontologia.reserva.application.SenasDeReserva.VistaSena;
import java.math.BigDecimal;
import java.util.Map;
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
        Boolean modificableOnline,
        // C-23: siempre presente, null si el turno no tiene seña (spec turnos-paciente)
        @JsonInclude(JsonInclude.Include.ALWAYS) SenaResponse sena) {

    record Referencia(Long id, String nombre) {}

    /** C-23: la seña del turno; {@code linkPago} solo con la seña pendiente (spec {@code turnos-paciente}). */
    record SenaResponse(String estado, BigDecimal importe, OffsetDateTime venceEn, String linkPago) {}

    static TurnoPacienteResponse de(TurnoDelPaciente turno, ZoneId zona, Boolean modificableOnline,
                                    Map<Long, VistaSena> senas) {
        VistaSena sena = senas.get(turno.turnoId());
        TurnoPacienteResponse base = de(turno, zona, modificableOnline);
        return sena == null ? base : new TurnoPacienteResponse(base.numeroReserva(), base.inicio(), base.fin(),
                base.estado(), base.prestacion(), base.profesional(), base.modificableOnline(),
                new SenaResponse(sena.estado(), sena.importe(), sena.venceEn().atZone(zona).toOffsetDateTime(),
                        sena.linkPago()));
    }

    static TurnoPacienteResponse de(TurnoDelPaciente turno, ZoneId zona, Boolean modificableOnline) {
        return new TurnoPacienteResponse(turno.numeroReserva(),
                turno.inicio().atZone(zona).toOffsetDateTime(), turno.fin().atZone(zona).toOffsetDateTime(),
                turno.estado().name(), new Referencia(turno.prestacionId(), turno.prestacionNombre()),
                new Referencia(turno.profesionalId(), turno.profesionalNombre()), modificableOnline, null);
    }
}
