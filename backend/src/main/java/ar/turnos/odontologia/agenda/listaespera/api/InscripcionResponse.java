package ar.turnos.odontologia.agenda.listaespera.api;

import ar.turnos.odontologia.agenda.listaespera.application.ListaEsperaRepository.FilaInscripcion;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Una inscripción activa con su oferta pendiente, si la hay. El nombre y el DNI del paciente solo los ve la recepción;
 * al paciente se le devuelven vacíos ({@link #delPaciente}).
 */
record InscripcionResponse(Long id, Long pacienteId, String pacienteNombre, String pacienteDni, Long prestacionId,
                           String prestacionNombre, Long profesionalId, String profesionalNombre, LocalDate desde,
                           LocalDate hasta, OffsetDateTime inscriptaEn, String estado, OfertaPendiente oferta) {

    record OfertaPendiente(OffsetDateTime inicio, OffsetDateTime venceEn) {}

    static InscripcionResponse deLaRecepcion(FilaInscripcion fila, ZoneId zona) {
        return de(fila, zona, true);
    }

    static InscripcionResponse delPaciente(FilaInscripcion fila, ZoneId zona) {
        return de(fila, zona, false);
    }

    private static InscripcionResponse de(FilaInscripcion fila, ZoneId zona, boolean conPaciente) {
        OfertaPendiente oferta = fila.ofertaInicio() == null ? null
                : new OfertaPendiente(fila.ofertaInicio().atZone(zona).toOffsetDateTime(),
                        fila.ofertaVenceEn().atZone(zona).toOffsetDateTime());
        return new InscripcionResponse(fila.id(), conPaciente ? fila.pacienteId() : null,
                conPaciente ? fila.pacienteNombre() : null, conPaciente ? fila.pacienteDni() : null,
                fila.prestacionId(), fila.prestacionNombre(), fila.profesionalId(), fila.profesionalNombre(),
                fila.desde(), fila.hasta(), fila.inscriptaEn().atZone(zona).toOffsetDateTime(), "ACTIVA", oferta);
    }
}
