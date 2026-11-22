package ar.turnos.odontologia.clinica.domain;

/** Catálogo de estados del odontograma (KB, modelo de datos). */
public enum EstadoPieza {
    SANO,
    CARIES,
    OBTURADO,
    A_TRATAR,
    AUSENTE,
    CORONA,
    ENDODONCIA;

    /** @throws ClinicaException {@code DATOS_INVALIDOS} si el estado no está en el catálogo */
    public static EstadoPieza de(String estado) {
        if (estado != null) {
            for (EstadoPieza valor : values()) {
                if (valor.name().equals(estado)) {
                    return valor;
                }
            }
        }
        throw new ClinicaException(CodigoClinica.DATOS_INVALIDOS, "El estado no está en el catálogo del odontograma");
    }
}
