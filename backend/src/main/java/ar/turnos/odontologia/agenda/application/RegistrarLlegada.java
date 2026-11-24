package ar.turnos.odontologia.agenda.application;

import ar.turnos.odontologia.agenda.domain.Margen;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.agenda.domain.TurnoInexistenteException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Caso de uso (C-43, RN-AG-20): la recepción registra que el paciente llegó. El turno sigue activo y ocupando el
 * profesional y el sillón; pasa a {@code ATENDIDO} cuando termina su margen (design D2). Si la llegada se registra
 * cuando el fin con margen ya pasó (la corrección de un turno sin registro), pasa a {@code ATENDIDO} en la misma
 * operación.
 */
public class RegistrarLlegada {

    private final TurnoRepository turnos;
    private final Clock reloj;
    private final ZoneId zona;
    private final Margen margen;

    public RegistrarLlegada(TurnoRepository turnos, Clock reloj, ZoneId zona, Margen margen) {
        this.turnos = Objects.requireNonNull(turnos, "turnos");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
        this.zona = Objects.requireNonNull(zona, "zona");
        this.margen = Objects.requireNonNull(margen, "margen");
    }

    public Turno ejecutar(Long turnoId) {
        Turno turno = turnos.buscarParaModificar(turnoId)
                .map(TurnoRepository.TurnoModificable::turno)
                .orElseThrow(() -> new TurnoInexistenteException(turnoId));
        Instant ahora = reloj.instant();
        Turno conLlegada = turno.conLlegada(ahora, zona);
        Instant finConMargen = conLlegada.intervalo().fin().plus(margen.valor());
        return turnos.guardar(ahora.isBefore(finConMargen) ? conLlegada : conLlegada.atendido());
    }
}
