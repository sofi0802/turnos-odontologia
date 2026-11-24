package ar.turnos.odontologia.coberturas.api;

import ar.turnos.odontologia.coberturas.api.CoberturasDtos.PorcentajeSolicitud;
import ar.turnos.odontologia.coberturas.application.ServicioCoberturaPlan;
import ar.turnos.odontologia.coberturas.application.Vistas.CoberturaCodigoVista;
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
 * Cobertura de un plan por código del nomenclador (RN-PR-02). El plan se busca junto con la obra social de la ruta
 * ({@code 404} si no coinciden). El personal consulta; solo ADMIN carga con un {@code PUT} que reemplaza. Sin
 * {@code DELETE}: dejar de cubrir un código es cargar 0 %.
 */
@RestController
@RequestMapping("/api/v1/obras-sociales/{obraSocialId}/planes/{planId}/coberturas")
class CoberturaPlanController {

    private final ServicioCoberturaPlan servicio;

    CoberturaPlanController(ServicioCoberturaPlan servicio) {
        this.servicio = servicio;
    }

    @PreAuthorize(ArancelesController.PERSONAL)
    @GetMapping
    List<CoberturaCodigoVista> listar(@PathVariable Long obraSocialId, @PathVariable Long planId) {
        return servicio.listar(obraSocialId, planId);
    }

    @PreAuthorize(ArancelesController.SOLO_ADMIN)
    @PutMapping("/{nomencladorId}")
    CoberturaCodigoVista cargar(@PathVariable Long obraSocialId, @PathVariable Long planId,
                                @PathVariable Long nomencladorId, @Valid @RequestBody PorcentajeSolicitud solicitud) {
        return servicio.cargar(obraSocialId, planId, nomencladorId, solicitud.porcentaje());
    }
}
