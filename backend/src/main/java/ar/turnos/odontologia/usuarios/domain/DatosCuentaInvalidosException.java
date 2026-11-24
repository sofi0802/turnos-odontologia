package ar.turnos.odontologia.usuarios.domain;

/**
 * Un dato del pedido sobre una cuenta del personal es inválido por una regla que no expresa Bean Validation
 * (el rol permitido, el vínculo con el profesional, la contraseña nueva). Se responde como 422 DATOS_INVALIDOS
 * con el campo. El mensaje es fijo y nunca repite el valor recibido.
 */
public class DatosCuentaInvalidosException extends RuntimeException {

    private final String campo;

    public DatosCuentaInvalidosException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public String campo() {
        return campo;
    }
}
