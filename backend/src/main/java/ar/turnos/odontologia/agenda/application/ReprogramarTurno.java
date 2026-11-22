package ar.turnos.odontologia.agenda.application;

import ar.turnos.odontologia.agenda.domain.Turno;
import java.util.Objects;

/**
 * Caso de uso: el personal reprograma un turno activo (RN-AG-07, RN-AG-08). Cancela el original y crea
 * uno nuevo con las reglas de {@link CrearTurno} y un número de reserva nuevo.
 *
 * <p>El turno nuevo se verifica antes de tocar el original, ignorándolo como conflicto. Así un rechazo
 * deja el original intacto. Después se guarda el original cancelado y recién entonces el nuevo, para que
 * la restricción de la base no los vea superpuestos. La atomicidad ante fallas de la base la da la
 * transacción que abre la infraestructura.
 */
public class ReprogramarTurno {

    private final TurnoRepository turnos;
    private final CrearTurno crearTurno;

    public ReprogramarTurno(TurnoRepository turnos, CrearTurno crearTurno) {
        this.turnos = Objects.requireNonNull(turnos, "turnos");
        this.crearTurno = Objects.requireNonNull(crearTurno, "crearTurno");
    }

    public Reprogramacion ejecutar(ReprogramarTurnoCommand comando) {
        Turno original = IdentificacionReserva.turnoActivo(turnos, comando.turnoId(), comando.dni(),
                comando.numeroReserva());

        Long profesional = comando.profesionalId() != null ? comando.profesionalId() : original.profesionalId();
        CrearTurnoCommand datosNuevos = new CrearTurnoCommand(
                original.pacienteId(),
                profesional,
                comando.sillonId() != null ? comando.sillonId() : original.sillonId(),
                original.prestacionId(),
                comando.inicio());
        // RN-AG-15 solo al cambiar de profesional: con el mismo, el turno ya estaba dado (design D2).
        boolean cambiaProfesional = !profesional.equals(original.profesionalId());
        Turno nuevo = crearTurno.preparar(datosNuevos, original.id(), cambiaProfesional);

        Turno cancelado = turnos.guardar(original.cancelado());
        return new Reprogramacion(cancelado, turnos.guardar(nuevo));
    }

    /** Resultado de una reprogramación: el turno original ya cancelado y el turno nuevo. */
    public record Reprogramacion(Turno original, Turno nuevo) {}
}
