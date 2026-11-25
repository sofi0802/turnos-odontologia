package ar.turnos.odontologia.pagos.application;

/** La pasarela de pagos no respondió o respondió con error ({@code 503 PASARELA_NO_DISPONIBLE}). */
public class PasarelaNoDisponibleException extends RuntimeException {

    public PasarelaNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }

    public PasarelaNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
