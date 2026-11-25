package ar.turnos.odontologia.notificaciones.domain;

/**
 * Un pedido de cambio del consentimiento de comunicaciones (C-17). El medio y el usuario del personal van solo y
 * siempre con {@link OrigenConsentimiento#RECEPCION}, igual que la restricción de la migración V11.
 */
public record CambioConsentimiento(boolean otorgado, OrigenConsentimiento origen, MedioConsentimiento medio,
                                   Long usuarioId) {

    public CambioConsentimiento {
        if (origen == null) {
            throw new IllegalArgumentException("El cambio necesita un origen");
        }
        boolean deLaRecepcion = origen == OrigenConsentimiento.RECEPCION;
        if (deLaRecepcion && (medio == null || usuarioId == null)) {
            throw new IllegalArgumentException("Un cambio de la recepción necesita el medio y el usuario");
        }
        if (!deLaRecepcion && (medio != null || usuarioId != null)) {
            throw new IllegalArgumentException("Solo la recepción indica el medio y el usuario");
        }
    }

    public static CambioConsentimiento delPaciente(boolean otorgado) {
        return new CambioConsentimiento(otorgado, OrigenConsentimiento.PACIENTE, null, null);
    }

    /** La casilla del registro marcada: el consentimiento queda otorgado. */
    public static CambioConsentimiento alRegistrarse() {
        return new CambioConsentimiento(true, OrigenConsentimiento.REGISTRO, null, null);
    }

    public static CambioConsentimiento deLaRecepcion(boolean otorgado, MedioConsentimiento medio, Long usuarioId) {
        return new CambioConsentimiento(otorgado, OrigenConsentimiento.RECEPCION, medio, usuarioId);
    }
}
