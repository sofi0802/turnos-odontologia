package ar.turnos.odontologia.reserva.api;

import ar.turnos.odontologia.reserva.application.CancelarReservaOnline;
import ar.turnos.odontologia.reserva.application.ReprogramarReservaOnline;
import ar.turnos.odontologia.reserva.application.ReservarTurno;
import ar.turnos.odontologia.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reserva online del paciente con sesión (design D1). El paciente sale siempre de la sesión: el pedido no tiene campo
 * de paciente ni de sillón, y cualquiera que llegue se ignora.
 */
@RestController
@RequestMapping("/api/v1/paciente")
class ReservasPacienteController {

    private final ReservarTurno reservar;
    private final CancelarReservaOnline cancelar;
    private final ReprogramarReservaOnline reprogramar;
    private final ZoneId zona;

    ReservasPacienteController(ReservarTurno reservar, CancelarReservaOnline cancelar,
                               ReprogramarReservaOnline reprogramar, ZoneId zonaHoraria) {
        this.reservar = reservar;
        this.cancelar = cancelar;
        this.reprogramar = reprogramar;
        this.zona = zonaHoraria;
    }

    record ReservaRequest(
            @NotNull(message = "es obligatorio") Long prestacionId,
            @NotNull(message = "es obligatorio") OffsetDateTime inicio,
            Long profesionalId) {}

    @PostMapping("/reservas")
    ResponseEntity<TurnoPacienteResponse> reservar(@Valid @RequestBody ReservaRequest pedido,
                                                   @AuthenticationPrincipal UsuarioAutenticado usuario) {
        var turno = reservar.ejecutar(usuario.pacienteId(), usuario.usuarioId(), pedido.prestacionId(),
                pedido.profesionalId(), pedido.inicio().toInstant());
        return ResponseEntity.status(HttpStatus.CREATED).body(TurnoPacienteResponse.de(turno, zona, null));
    }

    record ReprogramacionRequest(
            @NotNull(message = "es obligatorio") OffsetDateTime inicio,
            Long profesionalId) {}

    @PostMapping("/reservas/{numeroReserva}/cancelar")
    TurnoPacienteResponse cancelar(@PathVariable String numeroReserva,
                                   @AuthenticationPrincipal UsuarioAutenticado usuario) {
        var turno = cancelar.ejecutar(usuario.pacienteId(), usuario.usuarioId(), numeroReserva);
        return TurnoPacienteResponse.de(turno, zona, null);
    }

    @PostMapping("/reservas/{numeroReserva}/reprogramar")
    TurnoPacienteResponse reprogramar(@PathVariable String numeroReserva,
                                      @Valid @RequestBody ReprogramacionRequest pedido,
                                      @AuthenticationPrincipal UsuarioAutenticado usuario) {
        var turno = reprogramar.ejecutar(usuario.pacienteId(), usuario.usuarioId(), numeroReserva,
                pedido.inicio().toInstant(), pedido.profesionalId());
        return TurnoPacienteResponse.de(turno, zona, null);
    }
}
