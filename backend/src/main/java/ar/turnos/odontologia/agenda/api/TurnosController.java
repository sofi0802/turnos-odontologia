package ar.turnos.odontologia.agenda.api;

import ar.turnos.odontologia.agenda.domain.TurnoRechazadoException;
import ar.turnos.odontologia.agenda.domain.CodigoRechazo;
import ar.turnos.odontologia.agenda.application.SenasDeAgenda;
import ar.turnos.odontologia.agenda.application.CancelarTurnoCommand;
import ar.turnos.odontologia.agenda.application.CrearSobreturnoCommand;
import ar.turnos.odontologia.agenda.application.CrearTurnoCommand;
import ar.turnos.odontologia.agenda.application.ExtenderTurnoCommand;
import ar.turnos.odontologia.agenda.application.MarcarAusenciaCommand;
import ar.turnos.odontologia.agenda.application.ReprogramarTurnoCommand;
import ar.turnos.odontologia.agenda.infrastructure.ServicioAgenda;
import ar.turnos.odontologia.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API de turnos para el personal del consultorio (US-011, US-012). */
@RestController
@RequestMapping("/api/v1/turnos")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA', 'ODONTOLOGO')")
class TurnosController {

    private final ServicioAgenda agenda;
    private final ZoneId zona;

    TurnosController(ServicioAgenda agenda, ZoneId zona) {
        this.agenda = agenda;
        this.zona = zona;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TurnoResponse crear(@Valid @RequestBody CrearTurnoRequest pedido,
                        @AuthenticationPrincipal UsuarioAutenticado usuario) {
        CrearTurnoCommand comando = new CrearTurnoCommand(pedido.pacienteId(), pedido.profesionalId(),
                pedido.sillonId(), pedido.prestacionId(), pedido.inicio().toInstant());
        return TurnoResponse.de(agenda.crear(comando, usuario.getUsername()), zona);
    }

    @PostMapping("/sobreturno")
    @ResponseStatus(HttpStatus.CREATED)
    TurnoResponse crearSobreturno(@Valid @RequestBody CrearSobreturnoRequest pedido,
                                  @AuthenticationPrincipal UsuarioAutenticado usuario) {
        CrearSobreturnoCommand comando = new CrearSobreturnoCommand(pedido.pacienteId(), pedido.profesionalId(),
                pedido.prestacionId(), pedido.inicio().toInstant(), pedido.motivo());
        return TurnoResponse.de(agenda.crearSobreturno(comando, usuario.getUsername()), zona);
    }

    @GetMapping
    List<TurnoAgendaResponse> agenda(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @RequestParam(required = false) Long profesionalId,
            @RequestParam(required = false) Long sillonId,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        if (!hasta.isAfter(desde)) {
            throw new PeriodoInvalidoException();
        }
        AlcanceAgenda alcance = AlcanceAgenda.para(usuario.rol(), usuario.profesionalId(), profesionalId);
        if (alcance.sinTurnos()) {
            return List.of();
        }
        return agenda.agenda(desde.toInstant(), hasta.toInstant(), alcance.profesionalId(), sillonId).stream()
                .map(t -> TurnoAgendaResponse.de(t, zona))
                .toList();
    }

    /** C-23: las señas del módulo de pagos, si existe; sin él ningún turno tiene seña (design D2). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void usarSenas(SenasDeAgenda senas) {
        this.senas = senas;
    }

    private SenasDeAgenda senas = SenasDeAgenda.NINGUNA;

    @PostMapping("/{id}/cancelar")
    TurnoResponse cancelar(@PathVariable Long id, @Valid @RequestBody CancelarTurnoRequest pedido,
                           @AuthenticationPrincipal UsuarioAutenticado usuario) {
        // C-23 (RN-SE-06): con la seña pagada, el personal indica si la causa es del paciente o del consultorio.
        // La cancelación online del paciente no pasa por acá: siempre reintegra.
        if (pedido.causa() == null && senas.tieneSenaPagada(id)) {
            throw new TurnoRechazadoException(CodigoRechazo.DATOS_INVALIDOS,
                    "Indicá la causa: el paciente pagó una seña.");
        }
        CancelarTurnoCommand comando = new CancelarTurnoCommand(id, pedido.dni(), pedido.numeroReserva(), pedido.causa());
        return TurnoResponse.de(agenda.cancelar(comando, usuario.getUsername()), zona);
    }

    @PostMapping("/{id}/reprogramar")
    TurnoResponse reprogramar(@PathVariable Long id, @Valid @RequestBody ReprogramarTurnoRequest pedido,
                              @AuthenticationPrincipal UsuarioAutenticado usuario) {
        ReprogramarTurnoCommand comando = new ReprogramarTurnoCommand(id, pedido.dni(), pedido.numeroReserva(),
                pedido.inicio().toInstant(), pedido.profesionalId(), pedido.sillonId());
        return TurnoResponse.de(agenda.reprogramar(comando, usuario.getUsername()), zona);
    }

    @PostMapping("/{id}/extender")
    TurnoResponse extender(@PathVariable Long id, @Valid @RequestBody ExtenderTurnoRequest pedido,
                           @AuthenticationPrincipal UsuarioAutenticado usuario) {
        ExtenderTurnoCommand comando = new ExtenderTurnoCommand(id, pedido.fin().toInstant(), pedido.motivo());
        return TurnoResponse.de(agenda.extender(comando, usuario.getUsername()), zona);
    }

    /** C-43: la recepción registra la llegada del paciente (RN-AG-20). */
    @PostMapping("/{id}/llegada")
    @PreAuthorize("hasRole('RECEPCIONISTA')")
    TurnoResponse registrarLlegada(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return TurnoResponse.de(agenda.registrarLlegada(id, usuario.getUsername()), zona);
    }

    /** C-43: la recepción marca la ausencia desde la agenda, con la tolerancia de 15 minutos (RN-AG-17). */
    @PostMapping("/{id}/ausencia")
    @PreAuthorize("hasRole('RECEPCIONISTA')")
    TurnoResponse marcarAusencia(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return TurnoResponse.de(agenda.marcarAusencia(MarcarAusenciaCommand.deRecepcion(id), usuario.getUsername()),
                zona);
    }

    /** C-43: la recepción deshace una ausencia antes del fin, si el horario sigue libre. */
    @PostMapping("/{id}/deshacer-ausencia")
    @PreAuthorize("hasRole('RECEPCIONISTA')")
    TurnoResponse deshacerAusencia(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return TurnoResponse.de(agenda.deshacerAusencia(id, usuario.getUsername()), zona);
    }

    /** {@code hasta} no es posterior a {@code desde}. */
    static class PeriodoInvalidoException extends RuntimeException {
        PeriodoInvalidoException() {
            super("El fin del período debe ser posterior al inicio");
        }
    }
}
