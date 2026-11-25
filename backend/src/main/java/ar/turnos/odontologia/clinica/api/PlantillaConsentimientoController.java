package ar.turnos.odontologia.clinica.api;

import ar.turnos.odontologia.clinica.api.ConsentimientoDtos.EdicionSolicitud;
import ar.turnos.odontologia.clinica.api.ConsentimientoDtos.EstadoSolicitud;
import ar.turnos.odontologia.clinica.api.ConsentimientoDtos.PlantillaResumen;
import ar.turnos.odontologia.clinica.api.ConsentimientoDtos.PlantillaSolicitud;
import ar.turnos.odontologia.clinica.api.ConsentimientoDtos.PlantillaVista;
import ar.turnos.odontologia.clinica.api.ConsentimientoDtos.VersionVista;
import ar.turnos.odontologia.clinica.application.ServicioPlantillas;
import ar.turnos.odontologia.clinica.application.ServicioPlantillas.Edicion;
import ar.turnos.odontologia.shared.security.UsuarioAutenticado;
import ar.turnos.odontologia.usuarios.domain.Rol;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plantillas de consentimiento (C-27, RN-CL-09, design D2 y D8). No son datos de un paciente: el {@code ADMIN} las
 * crea, edita y desactiva; el odontólogo ve las activas para elegir cuál firmar. La recepción y el paciente quedan
 * afuera por la anotación. No hay {@code DELETE}: una plantilla se desactiva.
 */
@RestController
@RequestMapping("/api/v1/consentimientos/plantillas")
class PlantillaConsentimientoController {

    private static final String ADMIN = "hasRole('ADMIN')";
    private static final String LECTURA = "hasAnyRole('ADMIN', 'ODONTOLOGO')";

    private final ServicioPlantillas plantillas;
    private final ZoneId zona;

    PlantillaConsentimientoController(ServicioPlantillas plantillas, ZoneId zona) {
        this.plantillas = plantillas;
        this.zona = zona;
    }

    @PreAuthorize(ADMIN)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PlantillaVista crear(@RequestBody(required = false) PlantillaSolicitud solicitud,
                         @AuthenticationPrincipal UsuarioAutenticado usuario) {
        PlantillaSolicitud pedido = solicitud == null ? new PlantillaSolicitud(null, null) : solicitud;
        return PlantillaVista.de(plantillas.crear(usuario.usuarioId(), pedido.titulo(), pedido.texto()), zona);
    }

    /** Para el odontólogo, {@code incluirInactivas} se ignora: solo ve las activas. */
    @PreAuthorize(LECTURA)
    @GetMapping
    List<PlantillaResumen> listar(@RequestParam(defaultValue = "false") boolean incluirInactivas,
                                  @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return PlantillaResumen.de(plantillas.listar(incluirInactivas && esAdmin(usuario)), zona);
    }

    /** Una plantilla inactiva da {@code 404} al odontólogo. */
    @PreAuthorize(LECTURA)
    @GetMapping("/{plantillaId}")
    PlantillaVista ver(@PathVariable long plantillaId, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return PlantillaVista.de(plantillas.ver(plantillaId, !esAdmin(usuario)), zona);
    }

    /** {@code 201} con la versión nueva, o {@code 200} con la actual si no hubo cambios. */
    @PreAuthorize(ADMIN)
    @PostMapping("/{plantillaId}/versiones")
    ResponseEntity<PlantillaVista> editar(@PathVariable long plantillaId,
                                          @RequestBody(required = false) EdicionSolicitud solicitud,
                                          @AuthenticationPrincipal UsuarioAutenticado usuario) {
        EdicionSolicitud pedido = solicitud == null ? new EdicionSolicitud(null, null, null) : solicitud;
        Edicion edicion = plantillas.editar(usuario.usuarioId(), plantillaId, pedido.versionBase(), pedido.titulo(),
                pedido.texto());
        return ResponseEntity.status(edicion.creada() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(PlantillaVista.de(edicion.plantilla(), zona));
    }

    @PreAuthorize(ADMIN)
    @GetMapping("/{plantillaId}/versiones")
    List<VersionVista> versiones(@PathVariable long plantillaId) {
        return VersionVista.de(plantillas.versiones(plantillaId), zona);
    }

    @PreAuthorize(LECTURA)
    @GetMapping("/{plantillaId}/versiones/{numero}")
    VersionVista version(@PathVariable long plantillaId, @PathVariable int numero) {
        return VersionVista.de(plantillas.version(plantillaId, numero), zona);
    }

    @PreAuthorize(ADMIN)
    @PutMapping("/{plantillaId}/estado")
    PlantillaVista cambiarEstado(@PathVariable long plantillaId,
                                 @RequestBody(required = false) EstadoSolicitud solicitud) {
        return PlantillaVista.de(plantillas.cambiarEstado(plantillaId, solicitud == null ? null : solicitud.activa()),
                zona);
    }

    private static boolean esAdmin(UsuarioAutenticado usuario) {
        return usuario.rol() == Rol.ADMIN;
    }
}
