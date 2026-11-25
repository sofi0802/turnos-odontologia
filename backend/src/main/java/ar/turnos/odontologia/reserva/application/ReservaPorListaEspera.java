package ar.turnos.odontologia.reserva.application;

import ar.turnos.odontologia.agenda.application.CrearTurnoCommand;
import ar.turnos.odontologia.agenda.domain.ReferenciaInexistenteException;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.reserva.application.AsignadorDeReserva.Solicitud;
import ar.turnos.odontologia.reserva.application.ReservasPaciente.TurnoDelPaciente;
import ar.turnos.odontologia.reserva.domain.ReservaRechazadaException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * C-15: la lista de espera usa las mismas reglas y la misma asignación de sillón que la reserva online, sin duplicarlas
 * (design D3 y D4 de {@code lista-de-espera-automatica}). Es la única puerta pública al asignador de este módulo.
 */
public class ReservaPorListaEspera {

    private final CatalogoReserva catalogo;
    private final BuscadorDeHuecos buscador;
    private final AsignadorDeReserva asignador;
    private final AgendaDeTurnos agenda;
    private final AvisoReservaOnline aviso;
    private final Clock reloj;

    public ReservaPorListaEspera(CatalogoReserva catalogo, BuscadorDeHuecos buscador, AgendaDeTurnos agenda,
                                 AvisoReservaOnline aviso, Clock reloj, ZoneId zona) {
        this.catalogo = Objects.requireNonNull(catalogo, "catalogo");
        this.buscador = Objects.requireNonNull(buscador, "buscador");
        this.asignador = new AsignadorDeReserva(catalogo, buscador, reloj, zona, 0);
        this.agenda = Objects.requireNonNull(agenda, "agenda");
        this.aviso = new AvisoSeguro(Objects.requireNonNull(aviso, "aviso"));
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    /**
     * Valida la prestación y el profesional elegido ({@code null} = "sin preferencia") como la reserva online.
     *
     * @throws ReferenciaInexistenteException si alguno no existe
     * @throws ReservaRechazadaException {@code RECURSO_INACTIVO} o {@code PROFESIONAL_NO_HABILITADO}
     */
    public void validarPreferencia(Long prestacionId, Long profesionalId) {
        // Solo importa la prestación y el profesional: un inicio de mañana nunca cae "en el pasado" aunque el reloj
        // avance entre esta lectura y la del asignador.
        asignador.preparar(prestacionId, profesionalId, reloj.instant().plus(Duration.ofDays(1)), null);
    }

    /** Si existe una combinación de sillón libre para ese profesional, prestación e inicio (RN-RO-02, RN-RO-07). */
    public boolean hayLugar(Long prestacionId, Long profesionalId, Instant inicio) {
        try {
            Solicitud solicitud = asignador.preparar(prestacionId, Objects.requireNonNull(profesionalId),
                    inicio, null);
            return !buscador.combinaciones(inicio, solicitud.prestacion(), solicitud.candidatos(), null).isEmpty();
        } catch (ReservaRechazadaException | ReferenciaInexistenteException e) {
            return false;
        }
    }

    /**
     * Crea el turno con el profesional y el inicio dados y el sillón que asignaría la reserva online, y avisa como una
     * reserva online.
     *
     * @param actor el actor de la auditoría, sin datos personales
     * @throws ReservaRechazadaException si el horario ya no cumple las reglas
     */
    public TurnoDelPaciente reservar(Long pacienteId, Long prestacionId, Long profesionalId, Instant inicio,
                                     String actor) {
        Solicitud solicitud = asignador.preparar(prestacionId, Objects.requireNonNull(profesionalId), inicio, null);
        Turno turno = asignador.asignar(solicitud, combinacion -> agenda.crear(new CrearTurnoCommand(pacienteId,
                combinacion.profesionalId(), combinacion.sillonId(), prestacionId, inicio), actor));
        aviso.reservado(turno.id());
        return VistaDelTurno.de(turno, solicitud.prestacion().nombre(), catalogo);
    }
}
