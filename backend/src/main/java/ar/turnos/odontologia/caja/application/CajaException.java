package ar.turnos.odontologia.caja.application;

/** Rechazo de negocio de la caja con su código estable; {@code cajaId} acompaña a {@code CAJA_YA_ABIERTA}. */
public class CajaException extends RuntimeException {

    private final CodigoCaja codigo;
    private final Long cajaId;

    public CajaException(CodigoCaja codigo, String mensaje) {
        this(codigo, mensaje, null);
    }

    public CajaException(CodigoCaja codigo, String mensaje, Long cajaId) {
        super(mensaje);
        this.codigo = codigo;
        this.cajaId = cajaId;
    }

    public CodigoCaja codigo() {
        return codigo;
    }

    public Long cajaId() {
        return cajaId;
    }
}
