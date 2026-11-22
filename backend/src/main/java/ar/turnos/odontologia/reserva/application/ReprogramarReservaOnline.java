package ar.turnos.odontologia.reserva.application;

import ar.turnos.odontologia.agenda.application.ReprogramarTurnoCommand;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.reserva.application.AsignadorDeReserva.Solicitud;
import ar.turnos.odontologia.reserva.application.ReservasPaciente.TurnoDelPaciente;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Caso de uso: el paciente reprograma online su propio turno con el número de reserva, hasta 24 horas antes (US-024).
 * Reutiliza la asignación de la reserva ignorando el turno original como conflicto y en el conteo del día, y compone
 * {@code ReprogramarTurno} de la agenda: si el turno nuevo se rechaza, el original no cambia (design D6).
 */
public class ReprogramarReservaOnline {

    private final CatalogoReserva catalogo;
    private final ReservasPaciente reservas;
    private final AsignadorDeReserva asignador;
    private final AgendaDeTurnos agenda;
    private final AvisoReservaOnline aviso;
    private final Clock reloj;

    public ReprogramarReservaOnline(CatalogoReserva catalogo, ReservasPaciente reservas, BuscadorDeHuecos buscador,
                                    AgendaDeTurnos agenda, AvisoReservaOnline aviso, Clock reloj, ZoneId zona,
                                    int maximoAlternativas) {
        this.catalogo = Objects.requireNonNull(catalogo, "catalogo");
        this.reservas = Objects.requireNonNull(reservas, "reservas");
        this.asignador = new AsignadorDeReserva(catalogo, buscador, reloj, zona, maximoAlternativas);
        this.agenda = Objects.requireNonNull(agenda, "agenda");
        this.aviso = new AvisoSeguro(Objects.requireNonNull(aviso, "aviso"));
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    /**
     * @param pacienteId el paciente de la sesión
     * @param usuarioId la cuenta de la sesión, para el actor de la auditoría
     * @param profesionalId el profesional elegido, o {@code null} para "sin preferencia"
     */
    public TurnoDelPaciente ejecutar(Long pacienteId, Long usuarioId, String numeroReserva, Instant inicio,
                                     Long profesionalId) {
        TurnoDelPaciente original = ReservaPropia.verificar(reservas, reloj, pacienteId, numeroReserva);
        Solicitud solicitud = asignador.preparar(original.prestacionId(), profesionalId, inicio, original.turnoId());
        String dni = ReservaPropia.dniDe(reservas, pacienteId);
        String actor = ActorOnline.de(usuarioId);

        Turno nuevo = asignador.asignar(solicitud, combinacion -> agenda.reprogramar(new ReprogramarTurnoCommand(
                original.turnoId(), dni, original.numeroReserva(), inicio, combinacion.profesionalId(),
                combinacion.sillonId()), actor));
        aviso.reprogramado(nuevo.id());
        return VistaDelTurno.de(nuevo, original.prestacionNombre(), catalogo);
    }
}
