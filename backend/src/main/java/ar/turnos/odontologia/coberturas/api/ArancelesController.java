package ar.turnos.odontologia.coberturas.api;

import ar.turnos.odontologia.coberturas.api.CoberturasDtos.ArancelSolicitud;
import ar.turnos.odontologia.coberturas.application.ServicioAranceles;
import ar.turnos.odontologia.coberturas.application.Vistas.ArancelVista;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Precio particular de cada prestación (RN-PR-01). El personal consulta; solo ADMIN carga o cambia el precio con un
 * {@code PUT} que reemplaza el anterior. Sin {@code DELETE}.
 */
@RestController
@RequestMapping("/api/v1/aranceles")
class ArancelesController {

    static final String PERSONAL = "hasAnyRole('ADMIN','RECEPCIONISTA','ODONTOLOGO')";
    static final String SOLO_ADMIN = "hasRole('ADMIN')";

    private final ServicioAranceles servicio;

    ArancelesController(ServicioAranceles servicio) {
        this.servicio = servicio;
    }

    @PreAuthorize(PERSONAL)
    @GetMapping
    List<ArancelVista> listar() {
        return servicio.listar();
    }

    @PreAuthorize(SOLO_ADMIN)
    @PutMapping("/{prestacionId}")
    ArancelVista cargar(@PathVariable Long prestacionId, @Valid @RequestBody ArancelSolicitud solicitud) {
        return servicio.cargar(prestacionId, solicitud.precio());
    }
}
