package ar.turnos.odontologia.reserva.application;

/**
 * Actor de auditoría de las operaciones online del paciente (design D8): identifica la cuenta sin email, nombre ni DNI
 * y deja trazado el origen online sin una columna nueva.
 */
final class ActorOnline {

    static final String PREFIJO = "paciente-online:";

    private ActorOnline() {}

    static String de(Long usuarioId) {
        return PREFIJO + usuarioId;
    }
}
