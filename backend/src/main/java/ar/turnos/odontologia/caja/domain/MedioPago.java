package ar.turnos.odontologia.caja.domain;

/** Medios de pago de un cobro (RN-CA-04, PA-34). Solo el efectivo se arquea (RN-CA-06). */
public enum MedioPago {
    EFECTIVO,
    DEBITO,
    CREDITO,
    TRANSFERENCIA,
    /** C-23: la seña imputada al cargo (RN-SE-08). No pasa por ninguna caja ni se registra a mano. */
    MERCADO_PAGO;

    /** Los medios que se cobran en la caja: todos menos {@code MERCADO_PAGO}. */
    public boolean deCaja() {
        return this != MERCADO_PAGO;
    }

    /** El medio por su nombre; uno desconocido lanza {@link IllegalArgumentException}. */
    public static MedioPago desde(String nombre) {
        return MedioPago.valueOf(nombre);
    }
}
