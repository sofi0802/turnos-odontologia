package ar.turnos.odontologia.agenda.application;

import ar.turnos.odontologia.agenda.domain.Turno;

/**
 * C-15: la agenda avisa que un turno activo que no empezó se canceló o se reprogramó y su horario quedó libre
 * (RN-AG-08, RN-LE-02). Lo implementa la lista de espera. Se llama después del commit de la cancelación: una falla al
 * ofrecer el horario nunca deshace la cancelación (design D2).
 */
public interface HorariosLiberados {

    /** @param turnoCancelado el turno ya {@code CANCELADO}, con su profesional y su inicio */
    void liberado(Turno turnoCancelado);
}
