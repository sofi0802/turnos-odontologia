package ar.turnos.odontologia.reserva.application;

import ar.turnos.odontologia.reserva.application.ReservasPaciente.TurnoDelPaciente;
import ar.turnos.odontologia.reserva.domain.PlazoModificacion;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Caso de uso: los turnos del paciente de la sesión, con la indicación de si todavía puede cancelarlos o
 * reprogramarlos online (US-022).
 */
public class ConsultarMisTurnos {

    /** Un turno y si faltan 24 horas o más y está activo. */
    public record MiTurno(TurnoDelPaciente turno, boolean modificableOnline) {}

    private final ReservasPaciente reservas;
    private final Clock reloj;

    public ConsultarMisTurnos(ReservasPaciente reservas, Clock reloj) {
        this.reservas = Objects.requireNonNull(reservas, "reservas");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    /** @param pacienteId el paciente de la sesión: nunca sale del pedido */
    public List<MiTurno> ejecutar(Long pacienteId) {
        return reservas.turnosDe(pacienteId).stream()
                .map(turno -> new MiTurno(turno, turno.estado().esActivo()
                        && PlazoModificacion.permitido(reloj.instant(), turno.inicio())))
                .toList();
    }
}
