package ar.turnos.odontologia.coberturas.api;

import ar.turnos.odontologia.coberturas.api.CoberturasDtos.PresupuestoSolicitud;
import ar.turnos.odontologia.coberturas.application.ServicioPresupuestos;
import ar.turnos.odontologia.coberturas.application.ServicioPresupuestos.ItemPedido;
import ar.turnos.odontologia.coberturas.application.SugerenciasPresupuesto;
import ar.turnos.odontologia.coberturas.application.Vistas.SugerenciaVista;
import ar.turnos.odontologia.coberturas.application.Vistas.PresupuestoVista;
import ar.turnos.odontologia.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Presupuestos de un paciente para el personal (C-19, design D7). Solo el {@code ODONTOLOGO} emite, acepta, rechaza y
 * pide sugerencias; el resto del personal consulta. El profesional sale de la sesión, nunca del pedido. Sin
 * {@code PUT} ni {@code DELETE}: un presupuesto emitido no se edita ni se borra. {@code SecurityConfig} no cambia.
 */
@RestController
@RequestMapping("/api/v1/pacientes/{pacienteId}/presupuestos")
class PresupuestosController {

    static final String PERSONAL = "hasAnyRole('ADMIN','RECEPCIONISTA','ODONTOLOGO')";
    static final String SOLO_ODONTOLOGO = "hasRole('ODONTOLOGO')";

    private final ServicioPresupuestos servicio;
    private final SugerenciasPresupuesto sugerencias;

    PresupuestosController(ServicioPresupuestos servicio, SugerenciasPresupuesto sugerencias) {
        this.servicio = servicio;
        this.sugerencias = sugerencias;
    }

    /** Piezas y caras del odontograma a tratar; el literal gana sobre {@code /{presupuestoId}}. */
    @PreAuthorize(SOLO_ODONTOLOGO)
    @GetMapping("/sugerencias")
    List<SugerenciaVista> sugerencias(@PathVariable Long pacienteId) {
        return sugerencias.de(pacienteId);
    }

    @PreAuthorize(PERSONAL)
    @GetMapping("/{presupuestoId}")
    Object obtener(@PathVariable Long pacienteId, @PathVariable Long presupuestoId, Authentication autenticacion) {
        return PresupuestosRespuestas.paraElRol(servicio.obtener(pacienteId, presupuestoId), autenticacion);
    }

    @PreAuthorize(PERSONAL)
    @GetMapping
    List<?> listar(@PathVariable Long pacienteId, Authentication autenticacion) {
        return PresupuestosRespuestas.paraElRol(servicio.listar(pacienteId), autenticacion);
    }

    @PreAuthorize(SOLO_ODONTOLOGO)
    @PostMapping
    ResponseEntity<PresupuestoVista> emitir(@PathVariable Long pacienteId,
                                            @AuthenticationPrincipal UsuarioAutenticado usuario,
                                            @Valid @RequestBody PresupuestoSolicitud solicitud) {
        PresupuestoVista emitido = servicio.emitir(pacienteId, usuario.profesionalId(), solicitud.items().stream()
                .map(i -> new ItemPedido(i.prestacionId(), i.nomencladorId(), i.pieza(), i.cara(), i.cantidad()))
                .toList());
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(emitido.id()).toUri();
        return ResponseEntity.created(ubicacion).body(emitido);
    }

    @PreAuthorize(SOLO_ODONTOLOGO)
    @PostMapping("/{presupuestoId}/aceptar")
    PresupuestoVista aceptar(@PathVariable Long pacienteId, @PathVariable Long presupuestoId) {
        return servicio.aceptar(pacienteId, presupuestoId);
    }

    @PreAuthorize(SOLO_ODONTOLOGO)
    @PostMapping("/{presupuestoId}/rechazar")
    PresupuestoVista rechazar(@PathVariable Long pacienteId, @PathVariable Long presupuestoId) {
        return servicio.rechazar(pacienteId, presupuestoId);
    }
}
