package ar.turnos.odontologia.notificaciones.domain;

/** Quién registró un cambio del consentimiento de comunicaciones (C-17). */
public enum OrigenConsentimiento {
    /** El paciente, desde "Mi cuenta". */
    PACIENTE,
    /** El paciente, con la casilla del registro. */
    REGISTRO,
    /** La recepción o el administrador, en nombre del paciente. */
    RECEPCION
}
