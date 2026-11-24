package ar.turnos.odontologia.agenda.api;

import ar.turnos.odontologia.agenda.application.ConsultaAgenda.TurnoEnAgenda;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Turno en la agenda del personal: sin número de reserva, sin DNI y sin datos de salud. */
record TurnoAgendaResponse(
        Long id,
        Long pacienteId,
        String pacienteNombre,
        String pacienteApellido,
        Long profesionalId,
        Long sillonId,
        Long prestacionId,
        OffsetDateTime inicio,
        OffsetDateTime fin,
        String estado,
        boolean esSobreturno,
        OffsetDateTime llegadaEn) {

    static TurnoAgendaResponse de(TurnoEnAgenda t, ZoneId zona) {
        return new TurnoAgendaResponse(t.id(), t.pacienteId(), t.pacienteNombre(), t.pacienteApellido(),
                t.profesionalId(), t.sillonId(), t.prestacionId(),
                t.inicio().atZone(zona).toOffsetDateTime(), t.fin().atZone(zona).toOffsetDateTime(),
                t.estado().name(), t.esSobreturno(),
                t.llegadaEn() == null ? null : t.llegadaEn().atZone(zona).toOffsetDateTime());
    }
}
