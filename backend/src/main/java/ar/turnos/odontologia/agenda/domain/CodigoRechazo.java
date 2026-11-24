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
    /**
     * C-13: la ausencia del paciente solo se marcaba después del inicio del turno. Desde C-43 no se usa: la reemplaza
     * {@link #AUSENCIA_ANTES_DE_TOLERANCIA}. Se conserva para no cambiar el significado de un código publicado.
     */
    TURNO_NO_INICIADO,
    /** C-43: la ausencia se marca recién a partir de los 15 minutos del inicio (RN-AG-17). */
    AUSENCIA_ANTES_DE_TOLERANCIA,
    /** C-43: el turno ya tiene la llegada registrada (RN-AG-20). */
    LLEGADA_REGISTRADA,
    /** C-43: la llegada se registra desde las 00:00 del día del turno. */
    LLEGADA_ANTICIPADA,
    /** C-43: solo se deshace la ausencia de un turno {@code AUSENTE}. */
    TURNO_NO_AUSENTE,
    /** C-43: la ausencia ya no se deshace después del fin del turno. */
    TURNO_TERMINADO
}
