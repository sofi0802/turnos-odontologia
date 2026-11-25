package ar.turnos.odontologia.shared.security;

import ar.turnos.odontologia.usuarios.application.UsuarioRepository;
import ar.turnos.odontologia.usuarios.domain.Rol;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * Seguridad de la API: sesión del servidor con cookie HttpOnly (sin tokens firmados), CSRF activo con cookie legible y
 * encabezado, y acceso denegado por defecto.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SeguridadProperties.class)
public class SecurityConfig {

    /** C-23: ruta de la notificación de Mercado Pago, la única fuera de CSRF (design D7). */
    static final String NOTIFICACION_MERCADO_PAGO = "/api/v1/public/pagos/mercadopago/notificaciones";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** CORS con credenciales solo desde los orígenes configurados (app.seguridad.cors-origenes). */
    @Bean
    CorsConfigurationSource corsConfigurationSource(SeguridadProperties propiedades) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(propiedades.corsOrigenes());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        cors.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", cors);
        return fuente;
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }

    @Bean
    AuthenticationManager authenticationManager(UsuarioDetailsService usuarios, PasswordEncoder encoder) {
        DaoAuthenticationProvider proveedor = new DaoAuthenticationProvider();
        proveedor.setUserDetailsService(usuarios);
        proveedor.setPasswordEncoder(encoder);
        return new ProviderManager(proveedor);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, CsrfTokenRepository csrfTokenRepository,
                                    SecurityContextRepository contextRepository,
                                    ProblemDetailAuthenticationEntryPoint entryPoint,
                                    ProblemDetailAccessDeniedHandler accessDeniedHandler,
                                    UsuarioRepository usuarios, ProblemDetailWriter escritor) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        // C-23 (PA-64, aceptada por la líder): la notificación de Mercado Pago no puede llevar el token.
                        // Es la única ruta exceptuada, con coincidencia exacta; la autentican la firma y la consulta del
                        // pago (design D7). El resto de la API sigue con CSRF (regla dura 8).
                        .ignoringRequestMatchers(PathPatternRequestMatcher.withDefaults()
                                .matcher(HttpMethod.POST, NOTIFICACION_MERCADO_PAGO)))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                // C-41: cierra sesiones de cuentas desactivadas o restablecidas y frena la contraseña temporal pendiente
                .addFilterAfter(new VerificacionDeCuentaFilter(usuarios, contextRepository, escritor),
                        SecurityContextHolderFilter.class)
                .securityContext(contexto -> contexto.securityContextRepository(contextRepository))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .authorizeHttpRequests(reglas -> reglas
                        // Despacho interno de error: sin esto un 404 o 500 real termina en denyAll como 403 (fix de C-05)
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/v1/public/**").permitAll()
                        // /api/v1/logout lo atiende LogoutFilter, antes de llegar acá, para cualquier rol
                        .requestMatchers("/api/v1/paciente/**").hasRole(Rol.PACIENTE.name())
                        .requestMatchers("/api/v1/**").hasAnyRole(
                                Rol.ADMIN.name(), Rol.RECEPCIONISTA.name(), Rol.ODONTOLOGO.name())
                        .anyRequest().denyAll());
        return http.build();
    }
}
