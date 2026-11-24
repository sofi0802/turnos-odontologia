package ar.turnos.odontologia.caja.domain;

/** Medios de pago de un cobro (RN-CA-04, PA-34). Solo el efectivo se arquea (RN-CA-06). */
public enum MedioPago {
    EFECTIVO,
    DEBITO,
    CREDITO,
    TRANSFERENCIA;

    /** El medio por su nombre; uno desconocido lanza {@link IllegalArgumentException}. */
    public static MedioPago desde(String nombre) {
        return MedioPago.valueOf(nombre);
    }
}
