package ar.turnos.odontologia.pagos.domain;

/** Un dato de entrada inválido de un campo: se responde {@code 422 DATOS_INVALIDOS} con ese campo. */
public class DatoInvalidoException extends RuntimeException {

    private final String campo;

    public DatoInvalidoException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public String campo() {
        return campo;
    }
}
