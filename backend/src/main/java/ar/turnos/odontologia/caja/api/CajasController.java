package ar.turnos.odontologia.caja.api;

import ar.turnos.odontologia.caja.application.ServicioCaja;
import ar.turnos.odontologia.caja.application.Vistas.CajaVista;
import ar.turnos.odontologia.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Caja diaria (RN-CA-01, RN-CA-02, RN-CA-06): solo la recepción abre y cierra; la administración consulta. */
@RestController
@RequestMapping("/api/v1/cajas")
class CajasController {

    private static final String RECEPCION = "hasRole('RECEPCIONISTA')";
    private static final String CONSULTA = "hasAnyRole('ADMIN','RECEPCIONISTA')";

    private final ServicioCaja servicio;

    CajasController(ServicioCaja servicio) {
        this.servicio = servicio;
    }

    @PreAuthorize(RECEPCION)
    @PostMapping
    ResponseEntity<CajaVista> abrir(@Valid @RequestBody Pedidos.AbrirCaja pedido,
                                    @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.abrir(pedido.montoInicial(), usuario.getUsername()));
    }

    @PreAuthorize(CONSULTA)
    @GetMapping("/abierta")
    ResponseEntity<CajaVista> abierta() {
        return servicio.abierta().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PreAuthorize(RECEPCION)
    @PostMapping("/{id}/cierre")
    CajaVista cerrar(@PathVariable long id, @Valid @RequestBody Pedidos.CerrarCaja pedido,
                     @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return servicio.cerrar(id, pedido.efectivoContado(), pedido.motivoDiferencia(), usuario.getUsername());
    }

    @PreAuthorize(CONSULTA)
    @GetMapping
    List<CajaVista> historial(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.historial(desde, hasta);
    }
}
