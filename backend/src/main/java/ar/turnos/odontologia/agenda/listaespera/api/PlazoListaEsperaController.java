package ar.turnos.odontologia.agenda.listaespera.api;

import ar.turnos.odontologia.agenda.listaespera.infrastructure.ServicioListaEspera;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Plazo para aceptar una oferta (RN-LE-05): la recepción lo consulta y solo el administrador lo cambia. */
@RestController
@RequestMapping("/api/v1/configuracion/lista-espera")
class PlazoListaEsperaController {

    private final ServicioListaEspera servicio;

    PlazoListaEsperaController(ServicioListaEspera servicio) {
        this.servicio = servicio;
    }

    record PlazoDto(@NotNull(message = "es obligatorio") Integer plazoHoras) {}

    @PreAuthorize(ListaEsperaController.PERSONAL)
    @GetMapping
    PlazoDto obtener() {
        return new PlazoDto(servicio.plazo().horas());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping
    PlazoDto cambiar(@Valid @RequestBody PlazoDto pedido) {
        return new PlazoDto(servicio.guardarPlazo(pedido.plazoHoras()).horas());
    }
}
