package ar.turnos.odontologia.agenda.application;

import java.util.Objects;

/**
 * Datos para marcar la ausencia del paciente (C-13 y C-43). Desde la ficha la marca el odontólogo del turno:
 * {@code pacienteId} es el de la ficha y {@code profesionalId} el de la sesión, nunca del pedido. Desde la agenda la
 * marca la recepción ({@code desdeRecepcion}), sin paciente ni profesional que comprobar.
 */
public record MarcarAusenciaCommand(Long turnoId, Long pacienteId, Long profesionalId, boolean desdeRecepcion) {

    public MarcarAusenciaCommand {
        Objects.requireNonNull(turnoId, "turnoId");
        if (!desdeRecepcion) {
            Objects.requireNonNull(pacienteId, "pacienteId");
            Objects.requireNonNull(profesionalId, "profesionalId");
        }
    }

    /** El odontólogo del turno, desde la ficha clínica (firma de C-13). */
    public MarcarAusenciaCommand(Long turnoId, Long pacienteId, Long profesionalId) {
        this(turnoId, pacienteId, profesionalId, false);
    }

    /** C-43: la recepción, desde la agenda. */
    public static MarcarAusenciaCommand deRecepcion(Long turnoId) {
        return new MarcarAusenciaCommand(turnoId, null, null, true);
    }
}
