package ar.turnos.odontologia.agenda.domain;

/** Códigos estables de rechazo. La API los expone tal cual, como 409 (C-06). */
public enum CodigoRechazo {
    SOLAPAMIENTO_PROFESIONAL,
    SOLAPAMIENTO_SILLON,
    MARGEN_INSUFICIENTE,
    INICIO_EN_EL_PASADO,
    MARGEN_INVALIDO,
    RECURSO_INACTIVO,
    DATOS_DE_RESERVA_INVALIDOS,
    TURNO_NO_ACTIVO,
    SIN_SILLON_DISPONIBLE,
    TURNO_NO_EXTENSIBLE,
    DATOS_INVALIDOS,
    /** C-07: el turno se cruza con una ausencia o licencia del profesional (RN-AG-16). */
    PROFESIONAL_AUSENTE,
    /** C-07: el turno se cruza con un bloqueo de agenda del profesional (RN-AG-10). */
    AGENDA_BLOQUEADA,
    /** C-07: el turno no queda completo dentro de una franja de trabajo del profesional (RN-AG-13). */
    FUERA_DE_HORARIO,
    /** C-07: el profesional destino de una reasignación no está habilitado para la prestación (RN-AG-15). */
    PROFESIONAL_NO_HABILITADO,
    /** C-07: el turno que se quiere reasignar no es del profesional o no se cruza con la ausencia citada. */
    TURNO_NO_AFECTADO,
    /** C-13: la ausencia del paciente solo se marca después del inicio del turno (RN-AG-17). */
    TURNO_NO_INICIADO
}
