package ar.turnos.odontologia.coberturas.domain;

import java.util.Objects;

/**
 * Error del módulo de coberturas con código estable y, si corresponde, el campo del pedido. El mensaje es fijo por
 * código: nunca repite un número de afiliado ni datos de otro paciente (RN-OS-07).
 */
public class CoberturaException extends RuntimeException {

    private final CodigoCobertura codigo;
    private final String campo;

    public CoberturaException(CodigoCobertura codigo, String mensaje) {
        this(codigo, null, mensaje);
    }

    public CoberturaException(CodigoCobertura codigo, String campo, String mensaje) {
        super(mensaje);
        this.codigo = Objects.requireNonNull(codigo, "codigo");
        this.campo = campo;
    }

    public CodigoCobertura codigo() {
        return codigo;
    }

    /** El campo del pedido que originó el error, o {@code null}. */
    public String campo() {
        return campo;
    }

    /** El mismo error apuntando a otro campo del pedido (por ejemplo {@code items[0].pieza}). */
    public CoberturaException conCampo(String otroCampo) {
        return new CoberturaException(codigo, otroCampo, getMessage());
    }

    public static CoberturaException datosInvalidos(String campo, String mensaje) {
        return new CoberturaException(CodigoCobertura.DATOS_INVALIDOS, campo, mensaje);
    }

    public static CoberturaException noEncontrado(String mensaje) {
        return new CoberturaException(CodigoCobertura.RECURSO_NO_ENCONTRADO, mensaje);
    }
}
