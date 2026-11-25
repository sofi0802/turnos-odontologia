package ar.turnos.odontologia.notificaciones.application;

/** El paciente del consentimiento no existe: {@code 404 RECURSO_NO_ENCONTRADO} (C-17). */
public class PacienteNoEncontradoException extends RuntimeException {

    public PacienteNoEncontradoException() {
        super("No existe el paciente");
    }
}
