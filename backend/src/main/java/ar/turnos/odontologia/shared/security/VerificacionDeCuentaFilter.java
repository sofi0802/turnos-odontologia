package ar.turnos.odontologia.shared.security;

import ar.turnos.odontologia.usuarios.application.UsuarioRepository;
import ar.turnos.odontologia.usuarios.domain.Usuario;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * Verifica en cada pedido con sesión que la cuenta siga como cuando se autenticó (C-41 design D3 y D4):
 * <ul>
 *   <li>Si la cuenta ya no existe, está inactiva o su contraseña cambió (otra huella), invalida la sesión y el
 *       pedido sigue como anónimo, así una ruta protegida responde 401.</li>
 *   <li>Si la cuenta tiene una contraseña temporal pendiente, solo deja pasar las rutas públicas, el logout y el
 *       cambio de contraseña; el resto de {@code /api/v1/**} responde 403 CAMBIO_DE_CONTRASENA_PENDIENTE.</li>
 * </ul>
 * Una consulta por clave primaria por pedido autenticado: despreciable para un consultorio chico.
 */
class VerificacionDeCuentaFilter extends OncePerRequestFilter {

    static final String CAMBIO_DE_CONTRASENA = "/api/v1/cuenta/contrasena";

    private final UsuarioRepository usuarios;
    private final SecurityContextRepository contextRepository;
    private final ProblemDetailWriter escritor;
    private final SecurityContextHolderStrategy holderStrategy = SecurityContextHolder.getContextHolderStrategy();

    VerificacionDeCuentaFilter(UsuarioRepository usuarios, SecurityContextRepository contextRepository,
                               ProblemDetailWriter escritor) {
        this.usuarios = usuarios;
        this.contextRepository = contextRepository;
        this.escritor = escritor;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication autenticacion = holderStrategy.getContext().getAuthentication();
        if (autenticacion == null || !(autenticacion.getPrincipal() instanceof UsuarioAutenticado principal)) {
            chain.doFilter(request, response);
            return;
        }
        Optional<Usuario> cuenta = usuarios.buscarPorId(principal.usuarioId());
        if (cuenta.isEmpty() || !cuenta.get().activo()
                || !Objects.equals(UsuarioAutenticado.huella(cuenta.get().passwordHash()), principal.huellaCredencial())) {
            cerrarSesion(request);
            chain.doFilter(request, response);
            return;
        }
        UsuarioAutenticado vigente = principal;
        if (cuenta.get().debeCambiarContrasena() != principal.debeCambiarContrasena()) {
            vigente = actualizarPrincipal(cuenta.get(), request, response);
        }
        if (vigente.debeCambiarContrasena() && !permitidaConContrasenaPendiente(ruta(request))) {
            escritor.escribir(response, HttpStatus.FORBIDDEN, "CAMBIO_DE_CONTRASENA_PENDIENTE",
                    "Antes de seguir, elegí una contraseña nueva");
            return;
        }
        chain.doFilter(request, response);
    }

    private void cerrarSesion(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion != null) {
            sesion.invalidate();
        }
        holderStrategy.clearContext();
    }

    private UsuarioAutenticado actualizarPrincipal(Usuario cuenta, HttpServletRequest request,
                                                   HttpServletResponse response) {
        UsuarioAutenticado nuevo = UsuarioAutenticado.de(cuenta);
        nuevo.eraseCredentials();
        SecurityContext contexto = holderStrategy.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(nuevo, null, nuevo.getAuthorities()));
        holderStrategy.setContext(contexto);
        contextRepository.saveContext(contexto, request, response);
        return nuevo;
    }

    private static String ruta(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contexto = request.getContextPath();
        return contexto != null && !contexto.isEmpty() && uri.startsWith(contexto) ? uri.substring(contexto.length()) : uri;
    }

    static boolean permitidaConContrasenaPendiente(String ruta) {
        return !ruta.startsWith("/api/v1/")
                || ruta.startsWith("/api/v1/public/")
                || ruta.equals("/api/v1/logout")
                || ruta.equals(CAMBIO_DE_CONTRASENA);
    }
}
