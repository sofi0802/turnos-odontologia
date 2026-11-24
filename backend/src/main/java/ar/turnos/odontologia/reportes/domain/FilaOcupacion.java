package ar.turnos.odontologia.reportes.domain;

/**
 * Ocupación de un profesional o de un sillón (RN-RE-04).
 *
 * @param porcentaje minutos ocupados sobre minutos de atención, en entero; {@code null} sin minutos de atención
 */
public record FilaOcupacion(long id, String nombre, long minutosOcupados, long minutosAtencion, Integer porcentaje) {
}
