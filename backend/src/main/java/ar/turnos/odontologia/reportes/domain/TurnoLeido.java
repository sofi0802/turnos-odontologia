package ar.turnos.odontologia.reportes.domain;

/**
 * Un turno del período tal como lo lee la infraestructura (design D2): solo lo que necesitan los cálculos, sin ningún
 * dato del paciente (RN-RE-02).
 *
 * @param actorCreacion    actor del primer registro {@code CREAR} o {@code SOBRETURNO}, o {@code null} si no hay
 * @param reprogramado     {@code true} si la auditoría tiene {@code REPROGRAMAR} para este turno
 * @param actorCancelacion actor del último {@code CANCELAR}, o {@code null} si no hay
 */
public record TurnoLeido(long id, long profesionalId, long sillonId, java.time.Instant inicio, java.time.Instant fin,
                         String estado, String actorCreacion, boolean reprogramado, String actorCancelacion) {
}
