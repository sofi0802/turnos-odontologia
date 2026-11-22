package ar.turnos.odontologia.agenda.application;

import ar.turnos.odontologia.agenda.domain.AusenciaNoPermitidaException;
import ar.turnos.odontologia.agenda.domain.CodigoRechazo;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.agenda.domain.TurnoInexistenteException;
import ar.turnos.odontologia.agenda.domain.TurnoRechazadoException;
import java.time.Clock;
import java.util.Objects;

/**
 * Caso de uso (C-13, design D6): el odontólogo del turno marca que el paciente no se presentó (RN-AG-17). El turno
 * pasa a {@code AUSENTE} y deja de ocupar la agenda; lo que queda del horario se puede volver a dar desde el momento
 * actual, porque RN-AG-09 rechaza turnos en el pasado.
 *
 * <p>El orden de verificación no revela datos a quien no corresponde: primero que el turno exista y sea del
 * paciente, después el profesional, después que esté activo y por último que ya haya empezado.
 */
public class MarcarAusencia {

    private final TurnoRepository turnos;
    private final Clock reloj;

    public MarcarAusencia(TurnoRepository turnos, Clock reloj) {
        this.turnos = Objects.requireNonNull(turnos, "turnos");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    public Turno ejecutar(MarcarAusenciaCommand comando) {
        Turno turno = turnos.buscarParaModificar(comando.turnoId())
                .map(TurnoRepository.TurnoModificable::turno)
                .filter(t -> t.pacienteId().equals(comando.pacienteId()))
                .orElseThrow(() -> new TurnoInexistenteException(comando.turnoId()));
        if (!turno.profesionalId().equals(comando.profesionalId())) {
            throw new AusenciaNoPermitidaException();
        }
        if (!turno.esActivo()) {
            throw new TurnoRechazadoException(CodigoRechazo.TURNO_NO_ACTIVO, "El turno no está activo");
        }
        if (turno.intervalo().inicio().isAfter(reloj.instant())) {
            throw new TurnoRechazadoException(CodigoRechazo.TURNO_NO_INICIADO,
                    "La ausencia se marca después del inicio del turno");
        }
        return turnos.guardar(turno.ausente());
    }
}
