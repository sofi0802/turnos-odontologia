package ar.turnos.odontologia.notificaciones.api;

import ar.turnos.odontologia.notificaciones.domain.CambioConsentimiento;
import ar.turnos.odontologia.notificaciones.infrastructure.ServicioConsentimiento;
import ar.turnos.odontologia.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El paciente consulta y cambia su consentimiento de comunicaciones (C-17). Se identifica solo por la sesión: este
 * controller no recibe ningún id de paciente. {@code /api/v1/paciente/**} es solo para el rol {@code PACIENTE}.
 */
@RestController
@RequestMapping("/api/v1/paciente/consentimiento-comunicaciones")
class ConsentimientoPacienteController {

    private final ServicioConsentimiento servicio;

    ConsentimientoPacienteController(ServicioConsentimiento servicio) {
        this.servicio = servicio;
    }

    record Pedido(@NotNull Boolean otorgado) {}

    record Respuesta(boolean otorgado, Instant actualizadoEn) {}

    @GetMapping
    Respuesta consultar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        var estado = servicio.consultar(usuario.pacienteId());
        return new Respuesta(estado.otorgado(), estado.actualizadoEn());
    }

    @PutMapping
    Respuesta cambiar(@AuthenticationPrincipal UsuarioAutenticado usuario, @Valid @RequestBody Pedido pedido) {
        var estado = servicio.cambiar(usuario.pacienteId(), CambioConsentimiento.delPaciente(pedido.otorgado()));
        return new Respuesta(estado.otorgado(), estado.actualizadoEn());
    }
}
