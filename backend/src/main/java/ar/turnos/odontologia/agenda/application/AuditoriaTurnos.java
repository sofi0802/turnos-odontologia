package ar.turnos.odontologia.agenda.application;

/**
 * Puerto de salida: registro de las operaciones sobre turnos (RN-GL-03). Solo recibe ids, la acción y el
 * actor: no hay dónde guardar un DNI, un número de reserva ni datos de salud.
 */
public interface AuditoriaTurnos {

    void registrar(Long turnoId, Accion accion, String actor, Long turnoRelacionadoId);

    enum Accion { CREAR, CANCELAR, REPROGRAMAR, SOBRETURNO, EXTENDER, CONFIRMAR, AUSENTE }
}
