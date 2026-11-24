package ar.turnos.odontologia.coberturas.api;

import ar.turnos.odontologia.coberturas.api.CoberturasDtos.ObraSocialSolicitud;
import ar.turnos.odontologia.coberturas.application.ServicioObrasSociales;
import ar.turnos.odontologia.coberturas.application.Vistas.ObraSocialVista;
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

/** Obras sociales y prepagas (RN-OS-01). Sin {@code DELETE}: se desactivan. Solo ADMIN escribe. */
@RestController
@RequestMapping("/api/v1/obras-sociales")
class ObrasSocialesController {

    private static final String SOLO_ADMIN = "hasRole('ADMIN')";

    private final ServicioObrasSociales servicio;

    ObrasSocialesController(ServicioObrasSociales servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    List<ObraSocialVista> listar() {
        return servicio.listar();
    }

    @GetMapping("/{id}")
    ObraSocialVista obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @PreAuthorize(SOLO_ADMIN)
    @PostMapping
    ResponseEntity<ObraSocialVista> crear(@Valid @RequestBody ObraSocialSolicitud solicitud) {
        ObraSocialVista creada = servicio.crear(solicitud.nombre(), solicitud.sigla(), solicitud.tipo());
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creada.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creada);
    }

    @PreAuthorize(SOLO_ADMIN)
    @PutMapping("/{id}")
    ObraSocialVista actualizar(@PathVariable Long id, @Valid @RequestBody ObraSocialSolicitud solicitud) {
        return servicio.actualizar(id, solicitud.nombre(), solicitud.sigla(), solicitud.tipo());
    }

    @PreAuthorize(SOLO_ADMIN)
    @PostMapping("/{id}/activar")
    ObraSocialVista activar(@PathVariable Long id) {
        return servicio.activar(id);
    }

    @PreAuthorize(SOLO_ADMIN)
    @PostMapping("/{id}/desactivar")
    ObraSocialVista desactivar(@PathVariable Long id) {
        return servicio.desactivar(id);
    }
}
