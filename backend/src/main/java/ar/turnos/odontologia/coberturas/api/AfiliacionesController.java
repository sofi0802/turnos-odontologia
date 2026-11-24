package ar.turnos.odontologia.coberturas.api;

import ar.turnos.odontologia.coberturas.api.CoberturasDtos.AfiliacionSolicitud;
import ar.turnos.odontologia.coberturas.api.CoberturasDtos.CierreSolicitud;
import ar.turnos.odontologia.coberturas.api.CoberturasDtos.CorreccionSolicitud;
import ar.turnos.odontologia.coberturas.application.ServicioAfiliaciones;
import ar.turnos.odontologia.coberturas.application.Vistas.AfiliacionVista;
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
 * Afiliaciones de un paciente a un plan (RN-OS-03 a RN-OS-05). Sin {@code DELETE}: el historial no se borra. El
 * personal consulta; solo ADMIN y RECEPCIONISTA escriben.
 */
@RestController
@RequestMapping("/api/v1/pacientes/{pacienteId}/afiliaciones")
class AfiliacionesController {

    private static final String ESCRITURA = "hasAnyRole('ADMIN', 'RECEPCIONISTA')";

    private final ServicioAfiliaciones servicio;

    AfiliacionesController(ServicioAfiliaciones servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    List<AfiliacionVista> historial(@PathVariable Long pacienteId) {
        return servicio.historial(pacienteId);
    }

    @PreAuthorize(ESCRITURA)
    @PostMapping
    ResponseEntity<AfiliacionVista> afiliar(@PathVariable Long pacienteId,
                                            @Valid @RequestBody AfiliacionSolicitud solicitud) {
        AfiliacionVista creada = servicio.afiliar(pacienteId, solicitud.planId(), solicitud.numeroAfiliado(),
                solicitud.vigenteDesde(), solicitud.vigenteHasta());
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creada.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creada);
    }

    @PreAuthorize(ESCRITURA)
    @PutMapping("/{afiliacionId}")
    AfiliacionVista corregir(@PathVariable Long pacienteId, @PathVariable Long afiliacionId,
                             @Valid @RequestBody CorreccionSolicitud solicitud) {
        return servicio.corregir(pacienteId, afiliacionId, solicitud.planId(), solicitud.numeroAfiliado(),
                solicitud.vigenteDesde());
    }

    @PreAuthorize(ESCRITURA)
    @PostMapping("/{afiliacionId}/cerrar")
    AfiliacionVista cerrar(@PathVariable Long pacienteId, @PathVariable Long afiliacionId,
                           @Valid @RequestBody CierreSolicitud solicitud) {
        return servicio.cerrar(pacienteId, afiliacionId, solicitud.vigenteHasta());
    }
}
