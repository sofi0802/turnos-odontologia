package ar.turnos.odontologia.agenda.listaespera.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Una inscripción en la lista de espera (RN-LE-01). {@code profesionalId} es {@code null} para "sin preferencia".
 * El orden de llegada es {@code inscriptaEn}.
 */
public record Inscripcion(Long id, Long pacienteId, Long prestacionId, Long profesionalId, RangoDeFechas rango,
                          EstadoInscripcion estado, Instant inscriptaEn) {

    public Inscripcion {
        Objects.requireNonNull(pacienteId, "pacienteId");
        Objects.requireNonNull(prestacionId, "prestacionId");
        Objects.requireNonNull(rango, "rango");
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(inscriptaEn, "inscriptaEn");
    }

    public static Inscripcion nueva(Long pacienteId, Long prestacionId, Long profesionalId, RangoDeFechas rango,
                                    Instant ahora) {
        return new Inscripcion(null, pacienteId, prestacionId, profesionalId, rango, EstadoInscripcion.ACTIVA, ahora);
    }

    public Inscripcion conEstado(EstadoInscripcion nuevo) {
        return new Inscripcion(id, pacienteId, prestacionId, profesionalId, rango, nuevo, inscriptaEn);
    }

    /**
     * RN-LE-03, condiciones 1 y 2: activa, con la fecha del horario dentro del rango y el mismo profesional o "sin
     * preferencia". Que la prestación entre en el horario lo decide la agenda.
     */
    public boolean admite(Long profesionalDelHorario, LocalDate fechaDelHorario) {
        return estado == EstadoInscripcion.ACTIVA
                && rango.contiene(fechaDelHorario)
                && (profesionalId == null || profesionalId.equals(profesionalDelHorario));
    }
}
