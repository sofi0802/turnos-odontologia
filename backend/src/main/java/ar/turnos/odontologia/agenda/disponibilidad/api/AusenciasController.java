package ar.turnos.odontologia.agenda.disponibilidad.api;

import ar.turnos.odontologia.agenda.disponibilidad.api.DisponibilidadDtos.AusenciaSolicitud;
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
 * Ausencias y licencias de un profesional (RN-AG-16). ADMIN y la recepción las gestionan; un odontólogo solo
 * consulta las suyas (design D8). La respuesta del alta lista los turnos afectados, sin datos del paciente, y
 * ADMIN o la recepción los reasignan (design D10).
 */
@RestController
@RequestMapping("/api/v1/profesionales/{id}/ausencias")
class AusenciasController {

    private static final String GESTION = "hasAnyRole('ADMIN', 'RECEPCIONISTA')";

    private final ServicioPeriodos servicio;
    private final ServicioReasignacion reasignacion;
    private final ZoneId zona;

    AusenciasController(ServicioPeriodos servicio, ServicioReasignacion reasignacion, ZoneId zona) {
        this.servicio = servicio;
        this.reasignacion = reasignacion;
        this.zona = zona;
    }

    @PreAuthorize(GESTION)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PeriodoRegistradoRespuesta registrar(@PathVariable Long id, @Valid @RequestBody AusenciaSolicitud solicitud,
                                         @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return PeriodoRegistradoRespuesta.de(servicio.registrar(id, Clase.AUSENCIA,
                PeriodoSinAtencion.Tipo.valueOf(solicitud.tipo().name()), solicitud.inicio().toInstant(),
                solicitud.fin().toInstant(), solicitud.motivo(), DisponibilidadDtos.solicitante(usuario)), zona);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA', 'ODONTOLOGO')")
    @GetMapping
    List<PeriodoRespuesta> consultar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return servicio.consultar(id, Clase.AUSENCIA, DisponibilidadDtos.solicitante(usuario)).stream()
                .map(p -> PeriodoRespuesta.de(p, zona))
                .toList();
    }

    @PreAuthorize(GESTION)
    @DeleteMapping("/{ausenciaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void borrar(@PathVariable Long id, @PathVariable Long ausenciaId,
                @AuthenticationPrincipal UsuarioAutenticado usuario) {
        servicio.borrar(id, Clase.AUSENCIA, ausenciaId, DisponibilidadDtos.solicitante(usuario));
    }

    @PreAuthorize(GESTION)
    @PostMapping("/{ausenciaId}/turnos/{turnoId}/reasignar")
    TurnoReasignado reasignar(@PathVariable Long id, @PathVariable Long ausenciaId, @PathVariable Long turnoId,
                              @RequestBody ReasignacionSolicitud solicitud,
                              @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return TurnoReasignado.de(reasignacion.reasignar(id, Clase.AUSENCIA, ausenciaId, turnoId, solicitud.profesionalId(),
                solicitud.inicio() == null ? null : solicitud.inicio().toInstant(), solicitud.sillonId(),
                usuario.getUsername()), zona);
    }
}
