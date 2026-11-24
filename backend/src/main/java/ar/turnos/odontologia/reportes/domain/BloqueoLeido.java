package ar.turnos.odontologia.reportes.domain;

/** Bloqueo de agenda de un profesional (RN-AG-10). */
public record BloqueoLeido(long profesionalId, java.time.Instant inicio, java.time.Instant fin) {
}
