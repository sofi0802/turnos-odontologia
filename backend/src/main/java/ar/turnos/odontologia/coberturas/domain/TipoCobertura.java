package ar.turnos.odontologia.coberturas.domain;

/** Tipo de una obra social del catálogo (RN-OS-01). */
public enum TipoCobertura {
    OBRA_SOCIAL,
    PREPAGA;

    /** Interpreta el texto del pedido; cualquier otro valor es {@code DATOS_INVALIDOS} en el campo {@code tipo}. */
    public static TipoCobertura de(String texto) {
        for (TipoCobertura tipo : values()) {
            if (tipo.name().equals(texto)) {
                return tipo;
            }
        }
        throw CoberturaException.datosInvalidos("tipo", "El tipo debe ser OBRA_SOCIAL o PREPAGA");
    }
}
