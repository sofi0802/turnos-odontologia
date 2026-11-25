package ar.turnos.odontologia.notificaciones.domain;

import java.time.Instant;
import java.util.List;

/**
 * Consentimiento de comunicaciones de un paciente (C-17): el estado vigente es siempre el del último registro del
 * historial. Sin historial, el paciente no tiene consentimiento ni fecha de actualización.
 */
public record EstadoConsentimiento(boolean otorgado, Instant actualizadoEn, List<EventoConsentimiento> historial) {

    public EstadoConsentimiento {
        historial = List.copyOf(historial);
    }

    /** Arma el estado a partir del historial, ordenado del más viejo al más nuevo. */
    public static EstadoConsentimiento de(List<EventoConsentimiento> historial) {
        if (historial.isEmpty()) {
            return new EstadoConsentimiento(false, null, historial);
        }
        EventoConsentimiento ultimo = historial.get(historial.size() - 1);
        return new EstadoConsentimiento(ultimo.otorgado(), ultimo.registradoEn(), historial);
    }
}
