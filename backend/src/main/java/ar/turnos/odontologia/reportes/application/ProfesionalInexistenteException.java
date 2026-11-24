package ar.turnos.odontologia.reportes.application;

/** El {@code profesionalId} del filtro no existe (spec {@code reportes}, "Período válido"). */
public class ProfesionalInexistenteException extends RuntimeException {

    public ProfesionalInexistenteException() {
        super("El profesional no existe.");
    }
}
