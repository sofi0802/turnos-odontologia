package ar.turnos.odontologia.coberturas.api;

import ar.turnos.odontologia.coberturas.api.CoberturasDtos.CodigoSolicitud;
import ar.turnos.odontologia.coberturas.application.ServicioNomenclador;
import ar.turnos.odontologia.coberturas.application.Vistas.CodigoVista;
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

/** Nomenclador de prácticas (RN-OS-06). Sin {@code DELETE}: se desactiva. Solo ADMIN escribe. */
@RestController
@RequestMapping("/api/v1/nomenclador")
class NomencladorController {

    private static final String SOLO_ADMIN = "hasRole('ADMIN')";

    private final ServicioNomenclador servicio;

    NomencladorController(ServicioNomenclador servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    List<CodigoVista> listar() {
        return servicio.listar();
    }

    @GetMapping("/{id}")
    CodigoVista obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @PreAuthorize(SOLO_ADMIN)
    @PostMapping
    ResponseEntity<CodigoVista> crear(@Valid @RequestBody CodigoSolicitud solicitud) {
        CodigoVista creado = servicio.crear(solicitud.codigo(), solicitud.descripcion(), solicitud.prestacionId());
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @PreAuthorize(SOLO_ADMIN)
    @PutMapping("/{id}")
    CodigoVista actualizar(@PathVariable Long id, @Valid @RequestBody CodigoSolicitud solicitud) {
        return servicio.actualizar(id, solicitud.codigo(), solicitud.descripcion(), solicitud.prestacionId());
    }

    @PreAuthorize(SOLO_ADMIN)
    @PostMapping("/{id}/activar")
    CodigoVista activar(@PathVariable Long id) {
        return servicio.activar(id);
    }

    @PreAuthorize(SOLO_ADMIN)
    @PostMapping("/{id}/desactivar")
    CodigoVista desactivar(@PathVariable Long id) {
        return servicio.desactivar(id);
    }
}
