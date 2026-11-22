package ar.turnos.odontologia.reserva.domain;

/**
 * Códigos estables propios de la reserva online (design D7). Los demás que salen por la API ({@code TURNO_NO_ACTIVO},
 * {@code INICIO_EN_EL_PASADO}, {@code PROFESIONAL_NO_HABILITADO}, {@code RECURSO_INACTIVO}) son los de
 * {@code CodigoRechazo} de la agenda y no se repiten acá.
 */
public enum CodigoReserva {
    /** El horario pedido ya no se ofrece. No revela el motivo (ausencia, bloqueo, otro paciente). */
    SIN_DISPONIBILIDAD,
    /** Faltan menos de 24 horas para el turno: hay que llamar al consultorio. */
    CANCELACION_FUERA_DE_PLAZO,
    /** El número de reserva no existe o es de otro paciente: las dos cosas responden igual. */
    RESERVA_INEXISTENTE
}
