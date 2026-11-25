package ar.turnos.odontologia.reserva.application;

import ar.turnos.odontologia.agenda.application.CrearTurnoCommand;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.reserva.application.AsignadorDeReserva.Solicitud;
import ar.turnos.odontologia.reserva.application.ReservasPaciente.TurnoDelPaciente;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Caso de uso: el paciente con sesión reserva un turno online (US-021, US-023). Asigna profesional y sillón, y escribe
 * solo por la agenda ({@link AgendaDeTurnos}), así valen las dos garantías contra solapamientos (design D5). No abre
 * una transacción propia: cada intento es una transacción de la agenda, para no anular su reintento por deadlock.
 */
public class ReservarTurno {

    private final CatalogoReserva catalogo;
    private final AsignadorDeReserva asignador;
    private final AgendaDeTurnos agenda;
    private final AvisoReservaOnline aviso;
    private final SenasDeReserva senas;

    public ReservarTurno(CatalogoReserva catalogo, BuscadorDeHuecos buscador, AgendaDeTurnos agenda,
                         AvisoReservaOnline aviso, Clock reloj, ZoneId zona, int maximoAlternativas) {
        this(catalogo, buscador, agenda, aviso, SenasDeReserva.NINGUNA, reloj, zona, maximoAlternativas);
    }

    /** C-23: con las señas del módulo de pagos (design D3). */
    public ReservarTurno(CatalogoReserva catalogo, BuscadorDeHuecos buscador, AgendaDeTurnos agenda,
                         AvisoReservaOnline aviso, SenasDeReserva senas, Clock reloj, ZoneId zona,
                         int maximoAlternativas) {
        this.senas = Objects.requireNonNull(senas, "senas");
        this.catalogo = Objects.requireNonNull(catalogo, "catalogo");
        this.asignador = new AsignadorDeReserva(catalogo, buscador, reloj, zona, maximoAlternativas);
        this.agenda = Objects.requireNonNull(agenda, "agenda");
        this.aviso = new AvisoSeguro(Objects.requireNonNull(aviso, "aviso"));
    }

    /**
     * @param pacienteId el paciente de la sesión: nunca sale del pedido
     * @param usuarioId la cuenta de la sesión, para el actor de la auditoría
     * @param profesionalId el profesional elegido, o {@code null} para "sin preferencia"
     */
    public TurnoDelPaciente ejecutar(Long pacienteId, Long usuarioId, Long prestacionId, Long profesionalId,
                                     Instant inicio) {
        Solicitud solicitud = asignador.preparar(prestacionId, profesionalId, inicio, null);
        String actor = ActorOnline.de(usuarioId);
        Turno turno = asignador.asignar(solicitud, combinacion -> agenda.crear(new CrearTurnoCommand(pacienteId,
                combinacion.profesionalId(), combinacion.sillonId(), prestacionId, inicio), actor));
        // C-23: la seña va fuera de la transacción de la agenda; si no se puede guardar, pagos cancela el turno.
        senas.crearPara(turno.id(), pacienteId);
        aviso.reservado(turno.id());
        return VistaDelTurno.de(turno, solicitud.prestacion().nombre(), catalogo);
    }
}
