package ar.turnos.odontologia.caja.domain;

/** Un importe que no es un monto válido en pesos (RN-CA-04). */
public class ImporteInvalidoException extends RuntimeException {

    public ImporteInvalidoException(String mensaje) {
        super(mensaje);
    }
}
