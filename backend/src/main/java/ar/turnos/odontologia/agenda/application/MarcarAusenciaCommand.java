package ar.turnos.odontologia.agenda.application;

import java.util.Objects;

/**
 * Datos para marcar la ausencia del paciente (C-13). {@code profesionalId} es el de la sesión, nunca del pedido.
 */
public record MarcarAusenciaCommand(Long turnoId, Long pacienteId, Long profesionalId) {

    public MarcarAusenciaCommand {
        Objects.requireNonNull(turnoId, "turnoId");
        Objects.requireNonNull(pacienteId, "pacienteId");
        Objects.requireNonNull(profesionalId, "profesionalId");
    }
}
