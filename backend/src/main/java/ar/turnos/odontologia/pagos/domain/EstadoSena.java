package ar.turnos.odontologia.pagos.domain;

/** Estados de una seña (RN-SE-04 a RN-SE-09, diagrama de 05 de la KB). */
public enum EstadoSena {
    PENDIENTE,
    PAGADA,
    VENCIDA,
    IMPUTADA,
    DEVUELTA,
    RETENIDA
}
