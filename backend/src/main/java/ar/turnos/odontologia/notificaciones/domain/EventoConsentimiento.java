package ar.turnos.odontologia.notificaciones.domain;

import java.time.Instant;

/**
 * Un registro del historial del consentimiento (C-17). {@code registradoPor} es el nombre de usuario del personal y
 * solo existe con {@link OrigenConsentimiento#RECEPCION}. No lleva datos de contacto del paciente.
 */
public record EventoConsentimiento(boolean otorgado, Instant registradoEn, OrigenConsentimiento origen,
                                   MedioConsentimiento medio, String registradoPor) {
}
