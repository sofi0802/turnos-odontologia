package ar.turnos.odontologia.coberturas.api;

import ar.turnos.odontologia.coberturas.api.LiquidacionesDtos.CodigoSolicitud;
import ar.turnos.odontologia.coberturas.api.LiquidacionesDtos.ConciliacionSolicitud;
import ar.turnos.odontologia.coberturas.api.LiquidacionesDtos.GeneracionSolicitud;
import ar.turnos.odontologia.coberturas.application.OperacionesLiquidaciones.ArchivoCsv;
import ar.turnos.odontologia.coberturas.application.ServicioLiquidaciones;
import ar.turnos.odontologia.coberturas.application.VistasLiquidacion.LiquidacionVista;
import ar.turnos.odontologia.coberturas.application.VistasLiquidacion.ResumenVista;
import ar.turnos.odontologia.coberturas.domain.EstadoLiquidacion;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Liquidaciones a las obras sociales y prepagas (C-20, design D9). Todas las operaciones son solo del {@code ADMIN}
 * (RN-LQ-02: es dinero); la clase entera lo exige, así un endpoint nuevo no queda abierto por olvido. El actor sale de
 * la sesión, nunca del pedido. Sin {@code PUT} ni {@code DELETE} sobre la liquidación: Spring responde {@code 405}.
 * {@code SecurityConfig} no cambia.
 */
@RestController
@RequestMapping("/api/v1/liquidaciones")
@PreAuthorize("hasRole('ADMIN')")
class LiquidacionesController {

    private final ServicioLiquidaciones servicio;

    LiquidacionesController(ServicioLiquidaciones servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    ResponseEntity<LiquidacionVista> generar(Authentication autenticacion,
                                             @Valid @RequestBody GeneracionSolicitud solicitud) {
        LiquidacionVista generada = servicio.generar(solicitud.obraSocialId(), solicitud.periodo(),
                autenticacion.getName());
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(generada.id()).toUri();
        return ResponseEntity.created(ubicacion).body(generada);
    }

    @GetMapping
    List<ResumenVista> listar(@RequestParam(required = false) Long obraSocialId,
                              @RequestParam(required = false) EstadoLiquidacion estado) {
        return servicio.listar(obraSocialId, estado);
    }

    @GetMapping("/{id}")
    LiquidacionVista obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    /**
     * Descarga el CSV de una liquidación presentada o cerrada: adjunto, sin caché y con el nombre sin datos personales
     * (design D10). Spring Security ya agrega {@code X-Content-Type-Options: nosniff}.
     */
    @GetMapping("/{id}/csv")
    ResponseEntity<byte[]> csv(@PathVariable Long id) {
        ArchivoCsv archivo = servicio.csv(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo.nombre() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(archivo.contenido());
    }

    @PostMapping("/{id}/presentar")
    LiquidacionVista presentar(Authentication autenticacion, @PathVariable Long id) {
        return servicio.presentar(id, autenticacion.getName());
    }

    @PostMapping("/{id}/regenerar")
    LiquidacionVista regenerar(Authentication autenticacion, @PathVariable Long id) {
        return servicio.regenerar(id, autenticacion.getName());
    }

    /** Registra o corrige lo cobrado de una línea de una liquidación presentada; solo el ADMIN concilia (RN-LQ-06). */
    @PutMapping("/{id}/lineas/{lineaId}/conciliacion")
    LiquidacionVista conciliar(Authentication autenticacion, @PathVariable Long id, @PathVariable Long lineaId,
                               @RequestBody ConciliacionSolicitud solicitud) {
        return servicio.conciliar(id, lineaId, solicitud.montoCobrado(), solicitud.motivo(), autenticacion.getName());
    }

    @PostMapping("/{id}/cerrar")
    LiquidacionVista cerrar(Authentication autenticacion, @PathVariable Long id) {
        return servicio.cerrar(id, autenticacion.getName());
    }

    /** Quita una línea de un borrador; el turno queda libre para otra liquidación. */
    @DeleteMapping("/{id}/lineas/{lineaId}")
    ResponseEntity<Void> quitarLinea(@PathVariable Long id, @PathVariable Long lineaId) {
        servicio.quitarLinea(id, lineaId);
        return ResponseEntity.noContent().build();
    }

    /** Elige el código de una línea sin código (o cambia el que se eligió) en una liquidación en borrador. */
    @PutMapping("/{id}/lineas/{lineaId}/codigo")
    LiquidacionVista elegirCodigo(@PathVariable Long id, @PathVariable Long lineaId,
                                  @Valid @RequestBody CodigoSolicitud solicitud) {
        return servicio.elegirCodigo(id, lineaId, solicitud.nomencladorId());
    }
}
