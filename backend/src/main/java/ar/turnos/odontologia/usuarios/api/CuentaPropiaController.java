package ar.turnos.odontologia.usuarios.api;

import ar.turnos.odontologia.shared.security.UsuarioAutenticado;
import ar.turnos.odontologia.usuarios.application.CuentasPersonal;
import ar.turnos.odontologia.usuarios.domain.Usuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * La propia cuenta del personal: cambio de contraseña (spec seguridad-autenticacion, C-41). Es la única operación
 * del personal que se permite con una contraseña temporal pendiente (design D4).
 */
@RestController
@RequestMapping("/api/v1/cuenta")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA', 'ODONTOLOGO')")
class CuentaPropiaController {

    private final CuentasPersonal cuentas;
    private final SecurityContextRepository contextRepository;
    private final SecurityContextHolderStrategy holderStrategy = SecurityContextHolder.getContextHolderStrategy();

    CuentaPropiaController(CuentasPersonal cuentas, SecurityContextRepository contextRepository) {
        this.cuentas = cuentas;
        this.contextRepository = contextRepository;
    }

    /** Cambia la contraseña y actualiza el principal de esta sesión, que sigue abierta (design D3). */
    @PostMapping("/contrasena")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cambiarContrasena(@Valid @RequestBody CambioContrasenaRequest pedido,
                           @AuthenticationPrincipal UsuarioAutenticado usuario,
                           HttpServletRequest request, HttpServletResponse response) {
        Usuario cambiada = cuentas.cambiarPropia(usuario.usuarioId(), pedido.actual(), pedido.nueva());
        UsuarioAutenticado nuevo = UsuarioAutenticado.de(cambiada);
        nuevo.eraseCredentials();
        SecurityContext contexto = holderStrategy.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(nuevo, null, nuevo.getAuthorities()));
        holderStrategy.setContext(contexto);
        contextRepository.saveContext(contexto, request, response);
    }
}
