package ar.turnos.odontologia.reserva.application;

import ar.turnos.odontologia.agenda.application.CancelarTurnoCommand;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.reserva.application.ReservasPaciente.TurnoDelPaciente;
import java.time.Clock;
import java.util.Objects;

/**
 * Caso de uso: el paciente cancela online su propio turno con el número de reserva, hasta 24 horas antes (US-024,
 * RN-RO-08). Compone {@code CancelarTurno} de la agenda a través de {@link AgendaDeTurnos}, con el DNI leído del
 * paciente de la sesión (design D6).
 */
public class CancelarReservaOnline {

    private final ReservasPaciente reservas;
    private final AgendaDeTurnos agenda;
    private final AvisoReservaOnline aviso;
    private final Clock reloj;

    public CancelarReservaOnline(ReservasPaciente reservas, AgendaDeTurnos agenda, AvisoReservaOnline aviso,
                                 Clock reloj) {
        this.reservas = Objects.requireNonNull(reservas, "reservas");
        this.agenda = Objects.requireNonNull(agenda, "agenda");
        this.aviso = new AvisoSeguro(Objects.requireNonNull(aviso, "aviso"));
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    /**
     * @param pacienteId el paciente de la sesión
     * @param usuarioId la cuenta de la sesión, para el actor de la auditoría
     */
    public TurnoDelPaciente ejecutar(Long pacienteId, Long usuarioId, String numeroReserva) {
        TurnoDelPaciente propio = ReservaPropia.verificar(reservas, reloj, pacienteId, numeroReserva);
        Turno cancelado = agenda.cancelar(new CancelarTurnoCommand(propio.turnoId(),
                ReservaPropia.dniDe(reservas, pacienteId), propio.numeroReserva()), ActorOnline.de(usuarioId));
        aviso.cancelado(cancelado.id());
        return VistaDelTurno.de(cancelado, propio);
    }
}
