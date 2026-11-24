package ar.turnos.odontologia.caja.api;

import ar.turnos.odontologia.caja.application.ServicioCobros;
import ar.turnos.odontologia.caja.application.Vistas.CobroRegistrado;
import ar.turnos.odontologia.caja.application.Vistas.CobroVista;
import ar.turnos.odontologia.caja.application.Vistas.CuentaCorrienteVista;
import ar.turnos.odontologia.caja.application.Vistas.PendientesVista;
import ar.turnos.odontologia.caja.domain.MedioPago;
import ar.turnos.odontologia.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Cargos, cobros y cuenta corriente (RN-CA-03 a RN-CA-05, RN-CA-07). */
@RestController
class CobrosController {

    private static final String CONSULTA = "hasAnyRole('ADMIN','RECEPCIONISTA')";

    private final ServicioCobros servicio;

    CobrosController(ServicioCobros servicio) {
        this.servicio = servicio;
    }

    @PreAuthorize(CONSULTA)
    @GetMapping("/api/v1/cargos/pendientes")
    PendientesVista pendientes() {
        return servicio.pendientes();
    }

    @PreAuthorize(CONSULTA)
    @GetMapping("/api/v1/pacientes/{id}/cuenta-corriente")
    CuentaCorrienteVista cuentaCorriente(@PathVariable long id) {
        return servicio.cuentaCorriente(id);
    }

    @PreAuthorize("hasRole('RECEPCIONISTA')")
    @PostMapping("/api/v1/cargos/{id}/cobros")
    ResponseEntity<CobroRegistrado> cobrar(@PathVariable long id, @Valid @RequestBody Pedidos.Cobrar pedido,
                                           @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.cobrar(id, pedido.importe(),
                MedioPago.desde(pedido.medio()), usuario.getUsername()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/v1/cobros/{id}/anulacion")
    CobroVista anular(@PathVariable long id, @Valid @RequestBody Pedidos.Anular pedido,
                      @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return servicio.anular(id, pedido.motivo(), usuario.getUsername());
    }
}
