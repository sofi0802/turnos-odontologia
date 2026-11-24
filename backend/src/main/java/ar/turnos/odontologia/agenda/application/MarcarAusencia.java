package ar.turnos.odontologia.agenda.application;

import ar.turnos.odontologia.agenda.domain.AusenciaNoPermitidaException;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.agenda.domain.TurnoInexistenteException;
import java.time.Clock;
import java.util.Objects;

/**
 * Caso de uso (C-13, design D6; C-43, design D4): se marca que el paciente no se presentó (RN-AG-17). Lo hace el
 * odontólogo del turno desde la ficha o la recepción desde la agenda, a partir de los 15 minutos del inicio y nunca
 * con la llegada registrada. El turno pasa a {@code AUSENTE} y deja de ocupar la agenda; lo que queda del horario se
 * puede volver a dar desde el momento actual, porque RN-AG-09 rechaza turnos en el pasado.
 *
 * <p>El orden de verificación no revela datos a quien no corresponde: desde la ficha, primero que el turno exista y
 * sea del paciente, después el profesional; por último el estado, la llegada y la tolerancia, que verifica el dominio.
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
                .filter(t -> comando.desdeRecepcion() || t.pacienteId().equals(comando.pacienteId()))
                .orElseThrow(() -> new TurnoInexistenteException(comando.turnoId()));
        if (!comando.desdeRecepcion() && !turno.profesionalId().equals(comando.profesionalId())) {
            throw new AusenciaNoPermitidaException();
        }
        return turnos.guardar(turno.ausente(reloj.instant()));
    }
}
