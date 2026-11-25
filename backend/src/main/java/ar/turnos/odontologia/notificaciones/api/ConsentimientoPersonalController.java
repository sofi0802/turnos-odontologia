package ar.turnos.odontologia.notificaciones.api;

import ar.turnos.odontologia.notificaciones.domain.CambioConsentimiento;
import ar.turnos.odontologia.notificaciones.domain.EstadoConsentimiento;
import ar.turnos.odontologia.notificaciones.domain.EventoConsentimiento;
import ar.turnos.odontologia.notificaciones.domain.MedioConsentimiento;
import ar.turnos.odontologia.notificaciones.domain.OrigenConsentimiento;
import ar.turnos.odontologia.notificaciones.infrastructure.ServicioConsentimiento;
import ar.turnos.odontologia.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La recepción y el administrador consultan y registran el consentimiento de un paciente, con o sin cuenta (C-17).
 * El cambio queda con el usuario de la sesión y el medio. La respuesta no trae datos de contacto del paciente.
 */
@RestController
@RequestMapping("/api/v1/pacientes/{pacienteId}/consentimiento-comunicaciones")
@PreAuthorize("hasAnyRole('ADMIN','RECEPCIONISTA')")
class ConsentimientoPersonalController {

    private final ServicioConsentimiento servicio;

    ConsentimientoPersonalController(ServicioConsentimiento servicio) {
        this.servicio = servicio;
    }

    record Pedido(@NotNull Boolean otorgado, @NotNull MedioConsentimiento medio) {}

    record Evento(boolean otorgado, Instant registradoEn, OrigenConsentimiento origen, MedioConsentimiento medio,
                  String registradoPor) {

        static Evento de(EventoConsentimiento evento) {
            return new Evento(evento.otorgado(), evento.registradoEn(), evento.origen(), evento.medio(),
                    evento.registradoPor());
        }
    }

    record Respuesta(boolean otorgado, Instant actualizadoEn, List<Evento> historial) {

        static Respuesta de(EstadoConsentimiento estado) {
            return new Respuesta(estado.otorgado(), estado.actualizadoEn(),
                    estado.historial().stream().map(Evento::de).toList());
        }
    }

    @GetMapping
    Respuesta consultar(@PathVariable long pacienteId) {
        return Respuesta.de(servicio.consultar(pacienteId));
    }

    @PutMapping
    Respuesta cambiar(@PathVariable long pacienteId, @AuthenticationPrincipal UsuarioAutenticado usuario,
                      @Valid @RequestBody Pedido pedido) {
        return Respuesta.de(servicio.cambiar(pacienteId,
                CambioConsentimiento.deLaRecepcion(pedido.otorgado(), pedido.medio(), usuario.usuarioId())));
    }
}
