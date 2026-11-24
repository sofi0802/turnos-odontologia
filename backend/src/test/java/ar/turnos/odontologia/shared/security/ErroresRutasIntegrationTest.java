package ar.turnos.odontologia.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Escenarios del spec seguridad-autorizacion sobre rutas inexistentes y errores internos. Usan servidor real:
 * MockMvc no hace el despacho interno de error ({@code DispatcherType.ERROR}) donde apareció el 403 disfrazado.
 */
@Tag("integration")
class ErroresRutasIntegrationTest extends IntegracionServidorBase {

    private record Respuesta(int status, String cuerpo) { }

    /** Inicia sesión y devuelve el encabezado Cookie para los pedidos siguientes. */
    private String sesion(String username, String clave) throws Exception {
        ResponseEntity<String> login = iniciarSesion(username, clave);
        List<String> cookies = login.getHeaders().get(HttpHeaders.SET_COOKIE);
        return "JSESSIONID=" + valorDeCookie(cookies, "JSESSIONID");
    }

    private Respuesta pedir(String metodo, String ruta, String cookie) {
        return cliente().method(org.springframework.http.HttpMethod.valueOf(metodo)).uri(ruta)
                .headers(h -> {
                    boolean escritura = !"GET".equals(metodo);
                    if (cookie != null || escritura) {
                        // CSRF activo: en una escritura la cookie XSRF-TOKEN y el encabezado llevan el mismo valor
                        h.add("Cookie", (cookie == null ? "" : cookie + "; ") + (escritura ? "XSRF-TOKEN=csrf-ficticio" : ""));
                    }
                    if (escritura) h.add("X-XSRF-TOKEN", "csrf-ficticio");
                })
                .exchange((req, res) -> new Respuesta(res.getStatusCode().value(),
                        new String(res.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
    }

    private Respuesta get(String ruta, String cookie) {
        return pedir("GET", ruta, cookie);
    }

    @Test
    @DisplayName("Ruta inexistente con sesión del personal")
    void rutaInexistenteConSesionDelPersonal() throws Exception {
        String clave = UUID.randomUUID().toString();
        crearPersonal("recepcion", "RECEPCIONISTA", clave);

        Respuesta r = get("/api/v1/no-existe", sesion("recepcion", clave));

        assertThat(r.status()).isEqualTo(404);
        assertThat(r.cuerpo()).contains("\"codigo\":\"RECURSO_INEXISTENTE\"");
    }

    @Test
    @DisplayName("Ruta inexistente con sesión del administrador")
    void rutaInexistenteConSesionDelAdministrador() throws Exception {
        String clave = UUID.randomUUID().toString();
        crearPersonal("admin", "ADMIN", clave);

        Respuesta r = get("/api/v1/no-existe", sesion("admin", clave));

        assertThat(r.status()).isEqualTo(404);
        assertThat(r.cuerpo()).contains("\"codigo\":\"RECURSO_INEXISTENTE\"");
    }

    @Test
    @DisplayName("Ruta inexistente sin sesión")
    void rutaInexistenteSinSesion() {
        Respuesta r = get("/api/v1/no-existe", null);

        assertThat(r.status()).isEqualTo(401);
        assertThat(r.cuerpo()).contains("\"codigo\":\"NO_AUTENTICADO\"");
    }

    @Test
    @DisplayName("Ruta inexistente del personal pedida por un paciente")
    void rutaInexistenteDelPersonalPedidaPorUnPaciente() throws Exception {
        String clave = UUID.randomUUID().toString();
        long pacienteId = jdbc.queryForObject(
                "INSERT INTO paciente(nombre, apellido, dni, email) VALUES ('Paciente','Uno','99000001','paciente.uno@example.com') RETURNING id",
                Long.class);
        jdbc.update("INSERT INTO usuario(username, password_hash, rol, paciente_id) VALUES (?,?,'PACIENTE',?)",
                "paciente.uno@example.com", passwordEncoder.encode(clave), pacienteId);

        Respuesta r = get("/api/v1/no-existe", sesion("paciente.uno@example.com", clave));

        assertThat(r.status()).isEqualTo(403);
        assertThat(r.cuerpo()).contains("\"codigo\":\"ACCESO_DENEGADO\"");
    }

    @Test
    @DisplayName("Ruta interna de errores pedida directamente")
    void rutaInternaDeErroresPedidaDirectamente() throws Exception {
        String clave = UUID.randomUUID().toString();
        crearPersonal("admin", "ADMIN", clave);

        Respuesta r = get("/error", sesion("admin", clave));

        assertThat(r.status()).isEqualTo(403);
        assertThat(r.cuerpo()).contains("\"codigo\":\"ACCESO_DENEGADO\"");
    }

    @Test
    @DisplayName("Error no controlado en una operación del personal")
    void errorNoControladoEnUnaOperacionDelPersonal() throws Exception {
        String clave = UUID.randomUUID().toString();
        crearPersonal("admin", "ADMIN", clave);

        Respuesta r = get("/api/v1/prueba-seguridad/falla", sesion("admin", clave));

        assertThat(r.status()).isEqualTo(500);
        assertThat(r.cuerpo()).contains("\"codigo\":\"ERROR_INTERNO\"")
                .doesNotContain("detalle-interno-ficticio").doesNotContain("IllegalStateException")
                .doesNotContain("at ar.turnos");
    }

    // Regresión de D1: el manejador global no convierte en 500 los 4xx propios de Spring MVC ni el 403 de @PreAuthorize.

    @Test
    @DisplayName("Un método no soportado sigue siendo 405 y no 500")
    void metodoNoSoportadoSigueSiendo405() throws Exception {
        String clave = UUID.randomUUID().toString();
        crearPersonal("admin", "ADMIN", clave);
        String cookie = sesion("admin", clave);

        Respuesta r = pedir("PUT", "/api/v1/prueba-seguridad/solo-admin", cookie);

        assertThat(r.status()).isEqualTo(405);
    }

    @Test
    @DisplayName("@PreAuthorize sigue respondiendo 403 y no 500")
    void preAuthorizeSigueRespondiendo403() throws Exception {
        String clave = UUID.randomUUID().toString();
        crearPersonal("recepcion", "RECEPCIONISTA", clave);

        Respuesta r = get("/api/v1/prueba-seguridad/solo-admin", sesion("recepcion", clave));

        assertThat(r.status()).isEqualTo(403);
        assertThat(r.cuerpo()).contains("\"codigo\":\"ACCESO_DENEGADO\"");
    }
}
