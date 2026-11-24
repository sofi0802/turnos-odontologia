package ar.turnos.odontologia.reportes.domain;

/** Ausencia o licencia de un profesional (RN-AG-16); {@code tipo} es {@code AUSENCIA} o {@code LICENCIA}. */
public record AusenciaLeida(long profesionalId, String tipo, java.time.Instant inicio, java.time.Instant fin) {
}
