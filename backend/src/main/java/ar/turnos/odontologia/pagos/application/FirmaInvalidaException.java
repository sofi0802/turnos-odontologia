package ar.turnos.odontologia.pagos.application;

/** Notificación de Mercado Pago sin firma o con firma inválida ({@code 401 FIRMA_INVALIDA}, RN-SE-05). */
public class FirmaInvalidaException extends RuntimeException {

    public FirmaInvalidaException() {
        super("La firma de la notificación no es válida.");
    }
}
