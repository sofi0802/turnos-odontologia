package ar.turnos.odontologia.agenda.domain;

/**
 * C-13: solo el odontólogo del profesional del turno marca la ausencia del paciente. La API lo responde como
 * {@code 403 ACCESO_DENEGADO}, igual que el resto de los permisos.
 */
public class AusenciaNoPermitidaException extends RuntimeException {

    public AusenciaNoPermitidaException() {
        super("Solo el profesional del turno puede marcar la ausencia");
    }
}
