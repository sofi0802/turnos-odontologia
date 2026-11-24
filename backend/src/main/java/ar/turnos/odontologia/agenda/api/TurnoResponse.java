package ar.turnos.odontologia.agenda.api;

import ar.turnos.odontologia.agenda.domain.Turno;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Turno recién creado, cancelado o reprogramado. Incluye el número de reserva para dárselo al paciente; la
 * agenda ({@link TurnoAgendaResponse}) no lo muestra.
 */
record TurnoResponse(
        Long id,
        Long pacienteId,
        Long profesionalId,
        Long sillonId,
        Long prestacionId,
        OffsetDateTime inicio,
        OffsetDateTime fin,
        String estado,
        String numeroReserva,
        boolean esSobreturno,
        boolean extendido,
        OffsetDateTime llegadaEn) {

    static TurnoResponse de(Turno t, ZoneId zona) {
        return new TurnoResponse(t.id(), t.pacienteId(), t.profesionalId(), t.sillonId(), t.prestacionId(),
                t.intervalo().inicio().atZone(zona).toOffsetDateTime(),
                t.intervalo().fin().atZone(zona).toOffsetDateTime(),
                t.estado().name(), t.numeroReserva().valor(), t.esSobreturno(), t.extendido(),
                t.llegadaEn() == null ? null : t.llegadaEn().atZone(zona).toOffsetDateTime());
    }
}
