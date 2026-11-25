package ar.turnos.odontologia.notificaciones.domain;

/**
 * Tipos de notificación. {@code REASIGNACION} (RN-AG-16) es nuevo respecto de la KB; {@code CONFIRMACION} y
 * {@code CANCELACION} quedan reservados para changes posteriores.
 */
public enum TipoNotificacion {
    RECORDATORIO,
    CONFIRMACION,
    CANCELACION,
    REASIGNACION,
    /** C-23: se reintegró la seña del turno (RN-SE-11), sin el importe. */
    REINTEGRO_SENA
}
