package ar.turnos.odontologia.agenda.application;

import ar.turnos.odontologia.agenda.application.AuditoriaTurnos.Accion;
import ar.turnos.odontologia.agenda.domain.Agenda;
import ar.turnos.odontologia.agenda.domain.EstadoTurno;
import ar.turnos.odontologia.agenda.domain.Margen;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.agenda.domain.TurnoInexistenteException;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Caso de uso (C-43, design D5 y D6): la recepción deshace la ausencia de un turno antes de su fin. El turno vuelve
 * al estado que tenía ({@code CONFIRMADO} si la auditoría tiene su confirmación, si no {@code RESERVADO}) y vuelve a
 * ocupar la agenda, así que se verifican otra vez la superposición y el margen con los turnos activos (RN-AG-02 a
 * RN-AG-05). No se aplican las reglas de creación (turno en el pasado, horario de trabajo, ausencias y bloqueos del
 * profesional): el turno ya era válido y se está restaurando. Si otra operación toma el horario a la vez, la
 * restricción de la base rechaza el cambio y el repositorio lo traduce al mismo código.
 */
public class DeshacerAusencia {

    private final TurnoRepository turnos;
    private final AuditoriaTurnos auditoria;
    private final Clock reloj;
    private final Margen margen;
    private final Agenda agenda;

    public DeshacerAusencia(TurnoRepository turnos, AuditoriaTurnos auditoria, Clock reloj, Margen margen) {
        this.turnos = Objects.requireNonNull(turnos, "turnos");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
        this.margen = Objects.requireNonNull(margen, "margen");
        this.agenda = new Agenda(margen);
    }

    public Turno ejecutar(Long turnoId) {
        Turno turno = turnos.buscarParaModificar(turnoId)
                .map(TurnoRepository.TurnoModificable::turno)
                .orElseThrow(() -> new TurnoInexistenteException(turnoId));
        EstadoTurno previo = auditoria.registrada(turnoId, Accion.CONFIRMAR)
                ? EstadoTurno.CONFIRMADO : EstadoTurno.RESERVADO;
        Turno restaurado = turno.sinAusencia(previo, reloj.instant());

        List<Turno> cercanos = turnos.buscarQueIntersecan(restaurado.profesionalId(), restaurado.sillonId(),
                        restaurado.intervalo().ampliadoPor(margen)).stream()
                .filter(t -> !turnoId.equals(t.id()))
                .toList();
        agenda.verificarRestauracion(restaurado, cercanos);
        return turnos.guardar(restaurado);
    }
}
