package ar.turnos.odontologia.caja.application;

/** El período del historial de cajas es inválido; el campo a corregir es {@code hasta}. */
public class PeriodoInvalidoException extends RuntimeException {

    public PeriodoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
