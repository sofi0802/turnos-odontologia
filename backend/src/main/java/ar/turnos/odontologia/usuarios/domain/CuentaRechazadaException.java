package ar.turnos.odontologia.usuarios.domain;

/** Una operación sobre una cuenta del personal se rechaza por una regla de negocio (C-41). */
public class CuentaRechazadaException extends RuntimeException {

    private final CodigoCuenta codigo;

    public CuentaRechazadaException(CodigoCuenta codigo) {
        super(codigo.name());
        this.codigo = codigo;
    }

    public CodigoCuenta codigo() {
        return codigo;
    }
}
