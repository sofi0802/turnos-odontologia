package ar.turnos.odontologia.usuarios.api;

import ar.turnos.odontologia.shared.security.UsuarioAutenticado;
import ar.turnos.odontologia.usuarios.domain.NombreUsuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login con JSON y estado de la sesión. No usa formLogin: el frontend habla JSON, sin redirecciones. */
@RestController
@RequestMapping("/api/v1/public")
class AutenticacionController {

    private static final Logger log = LoggerFactory.getLogger(AutenticacionController.class);

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final SecurityContextHolderStrategy holderStrategy = SecurityContextHolder.getContextHolderStrategy();
    private final SessionAuthenticationStrategy estrategiaDeSesion = new ChangeSessionIdAuthenticationStrategy();

    AutenticacionController(AuthenticationManager authenticationManager, SecurityContextRepository contextRepository,
                            CsrfTokenRepository csrfTokenRepository) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @PostMapping("/login")
    SesionResponse login(@Valid @RequestBody LoginRequest pedido, HttpServletRequest request,
                         HttpServletResponse response) {
        NombreUsuario nombre = NombreUsuario.de(pedido.username());
        Authentication autenticacion;
        try {
            autenticacion = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(nombre.valor(), pedido.password()));
        } catch (AuthenticationException e) {
            // solo el usuario normalizado, sin caracteres de control; nunca la contraseña
            log.warn("Login fallido para el usuario '{}'", nombre.valor().replaceAll("\\p{Cntrl}", "_"));
            throw e;
        }

        estrategiaDeSesion.onAuthentication(autenticacion, request, response);
        SecurityContext contexto = holderStrategy.createEmptyContext();
        contexto.setAuthentication(autenticacion);
        holderStrategy.setContext(contexto);
        contextRepository.saveContext(contexto, request, response);
        // El token CSRF se renueva con cada login: la cookie nueva viaja en esta misma respuesta.
        csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response);

        UsuarioAutenticado usuario = (UsuarioAutenticado) autenticacion.getPrincipal();
        return SesionResponse.de(usuario.getUsername(), usuario.rol(), usuario.profesionalId());
    }

    @GetMapping("/sesion")
    SesionResponse sesion() {
        Authentication autenticacion = holderStrategy.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
            return SesionResponse.de(usuario.getUsername(), usuario.rol(), usuario.profesionalId());
        }
        return SesionResponse.sinSesion();
    }
}
