package ar.turnos.odontologia.usuarios.api;

import ar.turnos.odontologia.shared.security.UsuarioAutenticado;
import ar.turnos.odontologia.usuarios.application.CuentasPersonal;
import ar.turnos.odontologia.usuarios.domain.CodigoCuenta;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;

/** Cuentas del personal, solo para el ADMIN (C-41 design D1). */
@RestController
@RequestMapping("/api/v1/usuarios")
@PreAuthorize("hasRole('ADMIN')")
class UsuariosController {

    private final CuentasPersonal cuentas;

    UsuariosController(CuentasPersonal cuentas) {
        this.cuentas = cuentas;
    }

    @GetMapping
    List<CuentaPersonalResponse> listar() {
        return cuentas.listar().stream().map(CuentaPersonalResponse::de).toList();
    }

    @PostMapping
    ResponseEntity<CuentaCreadaResponse> crear(@Valid @RequestBody CrearCuentaRequest pedido,
                                               @AuthenticationPrincipal UsuarioAutenticado actor) {
        var creada = cuentas.crear(pedido.username(), pedido.rol(), pedido.profesionalId(), actor.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(CuentaCreadaResponse.de(creada));
    }

    @PostMapping("/{id}/desactivar")
    CuentaPersonalResponse desactivar(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return CuentaPersonalResponse.de(cuentas.desactivar(id, actor.usuarioId(), actor.getUsername()));
    }

    @PostMapping("/{id}/activar")
    CuentaPersonalResponse activar(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return CuentaPersonalResponse.de(cuentas.activar(id, actor.getUsername()));
    }

    @PostMapping("/{id}/restablecer-contrasena")
    ResponseEntity<CuentaCreadaResponse> restablecer(@PathVariable long id,
                                                     @AuthenticationPrincipal UsuarioAutenticado actor) {
        var restablecida = cuentas.restablecer(id, actor.usuarioId(), actor.getUsername());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(CuentaCreadaResponse.de(restablecida));
    }

    /**
     * Dos altas simultáneas llegan como violación de unicidad: se traducen por el nombre de la restricción
     * (design D6). Este manejador local gana sobre el del paquete, que traduce usuario_username_unico al código
     * del registro de pacientes.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail carreraDeAltas(DataIntegrityViolationException e) {
        String restriccion = UsuariosExceptionHandler.nombreDeRestriccion(e);
        if (restriccion.contains("usuario_username_unico")) {
            return UsuariosExceptionHandler.cuentaRechazada(CodigoCuenta.USUARIO_EXISTENTE);
        }
        if (restriccion.contains("usuario_profesional_unico")) {
            return UsuariosExceptionHandler.cuentaRechazada(CodigoCuenta.PROFESIONAL_CON_CUENTA);
        }
        throw e;
    }
}
