package ar.turnos.odontologia.agenda.disponibilidad.api;

import ar.turnos.odontologia.agenda.disponibilidad.api.DisponibilidadDtos.BloqueoSolicitud;
import ar.turnos.odontologia.agenda.disponibilidad.api.DisponibilidadDtos.PeriodoRegistradoRespuesta;
import ar.turnos.odontologia.agenda.disponibilidad.api.DisponibilidadDtos.PeriodoRespuesta;
import ar.turnos.odontologia.agenda.disponibilidad.api.DisponibilidadDtos.ReasignacionSolicitud;
import ar.turnos.odontologia.agenda.disponibilidad.api.DisponibilidadDtos.TurnoReasignado;
import ar.turnos.odontologia.agenda.disponibilidad.application.RegistroDisponibilidad.Clase;
import ar.turnos.odontologia.agenda.disponibilidad.application.ServicioPeriodos;
import ar.turnos.odontologia.agenda.disponibilidad.application.ServicioReasignacion;
import ar.turnos.odontologia.agenda.domain.reglas.PeriodoSinAtencion;
import ar.turnos.odontologia.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bloqueos de agenda (RN-AG-10, US-015). ADMIN opera sobre cualquier profesional y un odontólogo solo sobre el
 * suyo. La recepción consulta los bloqueos y reasigna los turnos que afectan (RN-AG-19, design D16), pero no los
 * registra ni los borra (design D8). Un {@code @PreAuthorize} por método.
 */
@RestController
@RequestMapping("/api/v1/profesionales/{id}/bloqueos")
class BloqueosController {

    private static final String LECTURA = "hasAnyRole('ADMIN', 'RECEPCIONISTA', 'ODONTOLOGO')";
    private static final String ESCRITURA = "hasAnyRole('ADMIN', 'ODONTOLOGO')";
    private static final String GESTION = "hasAnyRole('ADMIN', 'RECEPCIONISTA')";

    private final ServicioPeriodos servicio;
    private final ServicioReasignacion reasignacion;
    private final ZoneId zona;

    BloqueosController(ServicioPeriodos servicio, ServicioReasignacion reasignacion, ZoneId zona) {
        this.servicio = servicio;
        this.reasignacion = reasignacion;
        this.zona = zona;
    }

    @PreAuthorize(ESCRITURA)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PeriodoRegistradoRespuesta registrar(@PathVariable Long id, @Valid @RequestBody BloqueoSolicitud solicitud,
                                         @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return PeriodoRegistradoRespuesta.de(servicio.registrar(id, Clase.BLOQUEO, PeriodoSinAtencion.Tipo.BLOQUEO,
                solicitud.inicio().toInstant(), solicitud.fin().toInstant(), solicitud.motivo(),
                DisponibilidadDtos.solicitante(usuario)), zona);
    }

    @PreAuthorize(LECTURA)
    @GetMapping
    List<PeriodoRespuesta> consultar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return servicio.consultar(id, Clase.BLOQUEO, DisponibilidadDtos.solicitante(usuario)).stream()
                .map(p -> PeriodoRespuesta.de(p, zona))
                .toList();
    }

    @PreAuthorize(ESCRITURA)
    @DeleteMapping("/{bloqueoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void borrar(@PathVariable Long id, @PathVariable Long bloqueoId,
                @AuthenticationPrincipal UsuarioAutenticado usuario) {
        servicio.borrar(id, Clase.BLOQUEO, bloqueoId, DisponibilidadDtos.solicitante(usuario));
    }

    @PreAuthorize(GESTION)
    @PostMapping("/{bloqueoId}/turnos/{turnoId}/reasignar")
    TurnoReasignado reasignar(@PathVariable Long id, @PathVariable Long bloqueoId, @PathVariable Long turnoId,
                              @RequestBody ReasignacionSolicitud solicitud,
                              @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return TurnoReasignado.de(reasignacion.reasignar(id, Clase.BLOQUEO, bloqueoId, turnoId,
                solicitud.profesionalId(), solicitud.inicio() == null ? null : solicitud.inicio().toInstant(),
                solicitud.sillonId(), usuario.getUsername()), zona);
    }
}
