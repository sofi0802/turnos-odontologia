package ar.turnos.odontologia.agenda.application;

/**
 * Puerto de salida: registro de las operaciones sobre turnos (RN-GL-03). Solo recibe ids, la acción y el
 * actor: no hay dónde guardar un DNI, un número de reserva ni datos de salud.
 */
public interface AuditoriaTurnos {

    void registrar(Long turnoId, Accion accion, String actor, Long turnoRelacionadoId);

    /** C-23: la cancelación con la causa que indicó el personal (RN-SE-06); sin causa, igual que {@code registrar}. */
    default void registrarCancelacion(Long turnoId, String actor, String causa) {
        registrar(turnoId, Accion.CANCELAR, actor, null);
    }

    /** C-43: si el turno tiene algún registro con esa acción. Deshacer la ausencia lo usa para el estado previo. */
    boolean registrada(Long turnoId, Accion accion);

    /** {@code LLEGADA}, {@code ATENDIDO} y {@code DESHACER_AUSENCIA} son de C-43 (V22). */
    enum Accion { CREAR, CANCELAR, REPROGRAMAR, SOBRETURNO, EXTENDER, CONFIRMAR, AUSENTE, LLEGADA, ATENDIDO,
        DESHACER_AUSENCIA }
}
