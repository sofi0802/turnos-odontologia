package ar.turnos.odontologia.coberturas.api;

import ar.turnos.odontologia.coberturas.api.CoberturasDtos.PlanSolicitud;
import ar.turnos.odontologia.coberturas.application.ServicioPlanes;
import ar.turnos.odontologia.coberturas.application.Vistas.PlanVista;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Planes de una obra social (RN-OS-02). El plan se busca siempre junto con la obra social de la ruta (design D5):
 * si no coinciden, {@code 404}. Sin {@code DELETE}.
 */
@RestController
@RequestMapping("/api/v1/obras-sociales/{obraSocialId}/planes")
class PlanesController {

    private static final String SOLO_ADMIN = "hasRole('ADMIN')";

    private final ServicioPlanes servicio;

    PlanesController(ServicioPlanes servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    List<PlanVista> listar(@PathVariable Long obraSocialId) {
        return servicio.listar(obraSocialId);
    }

    @GetMapping("/{planId}")
    PlanVista obtener(@PathVariable Long obraSocialId, @PathVariable Long planId) {
        return servicio.obtener(obraSocialId, planId);
    }

    @PreAuthorize(SOLO_ADMIN)
    @PostMapping
    ResponseEntity<PlanVista> crear(@PathVariable Long obraSocialId, @Valid @RequestBody PlanSolicitud solicitud) {
        PlanVista creado = servicio.crear(obraSocialId, solicitud.nombre());
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @PreAuthorize(SOLO_ADMIN)
    @PutMapping("/{planId}")
    PlanVista actualizar(@PathVariable Long obraSocialId, @PathVariable Long planId,
                         @Valid @RequestBody PlanSolicitud solicitud) {
        return servicio.actualizar(obraSocialId, planId, solicitud.nombre());
    }

    @PreAuthorize(SOLO_ADMIN)
    @PostMapping("/{planId}/activar")
    PlanVista activar(@PathVariable Long obraSocialId, @PathVariable Long planId) {
        return servicio.activar(obraSocialId, planId);
    }

    @PreAuthorize(SOLO_ADMIN)
    @PostMapping("/{planId}/desactivar")
    PlanVista desactivar(@PathVariable Long obraSocialId, @PathVariable Long planId) {
        return servicio.desactivar(obraSocialId, planId);
    }
}
