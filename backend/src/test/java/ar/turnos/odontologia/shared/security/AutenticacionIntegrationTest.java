package ar.turnos.odontologia.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Escenarios del spec seguridad-autenticacion. */
@Tag("integration")
class AutenticacionIntegrationTest extends IntegracionSeguridadBase {

    @Test
    @DisplayName("Sin sesión iniciada")
    void sinSesionIniciada() throws Exception {
        Navegador navegador = navegador();

        navegador.get(SESION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(false))
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    @DisplayName("Sesión de un odontólogo")
    void sesionDeUnOdontologo() throws Exception {
        // GIVEN ana.ejemplo (ODONTOLOGO), vinculada a la Dra. A, con sesión iniciada
        String clave = contrasenaFicticia();
        crearPersonal("ana.ejemplo", "ODONTOLOGO", clave);
        long draA = jdbc.queryForObject("SELECT profesional_id FROM usuario WHERE username = 'ana.ejemplo'", Long.class);
        Navegador navegador = iniciarSesion("ana.ejemplo", clave);

        // WHEN pide GET /api/v1/public/sesion
        // THEN la respuesta es 200 con rol ODONTOLOGO y profesionalId igual al id de la Dra. A
        navegador.get(SESION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ODONTOLOGO"))
                .andExpect(jsonPath("$.profesionalId").value(draA));
    }

    @Test
    @DisplayName("Sesión de recepción sin profesional")
    void sesionDeRecepcionSinProfesional() throws Exception {
        // GIVEN recepcion con sesión iniciada
        String clave = contrasenaFicticia();
        crearPersonal("recepcion", "RECEPCIONISTA", clave);
        Navegador navegador = iniciarSesion("recepcion", clave);

        // WHEN pide GET /api/v1/public/sesion
        // THEN la respuesta es 200 con rol RECEPCIONISTA AND no incluye profesionalId
        navegador.get(SESION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("RECEPCIONISTA"))
                .andExpect(jsonPath("$.profesionalId").doesNotExist());
    }

    @Test
    @DisplayName("Login correcto del personal")
    void loginCorrectoDelPersonal() throws Exception {
        String clave = contrasenaFicticia();
        crearPersonal("recepcion", "RECEPCIONISTA", clave);
        Navegador navegador = navegador();
        navegador.get(SESION);

        String cuerpo = navegador.post(LOGIN, credenciales("recepcion", clave))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("recepcion"))
                .andExpect(jsonPath("$.rol").value("RECEPCIONISTA"))
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain(clave).doesNotContainIgnoringCase("password").doesNotContain("$2");
        navegador.get(SESION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(true))
                .andExpect(jsonPath("$.username").value("recepcion"))
                .andExpect(jsonPath("$.rol").value("RECEPCIONISTA"));
    }

    @Test
    @DisplayName("Login con el usuario en mayúsculas")
    void loginConElUsuarioEnMayusculas() throws Exception {
        String clave = contrasenaFicticia();
        crearPersonal("recepcion", "RECEPCIONISTA", clave);
        Navegador navegador = navegador();
        navegador.get(SESION);

        navegador.post(LOGIN, credenciales(" Recepcion ", clave))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("recepcion"));
    }

    @Test
    @DisplayName("Login correcto de un paciente")
    void loginCorrectoDeUnPaciente() throws Exception {
        String clave = contrasenaFicticia();
        crearPacienteConCuenta("Paciente", "Uno", "99000001", "paciente.uno@example.com", clave);
        Navegador navegador = navegador();
        navegador.get(SESION);

        navegador.post(LOGIN, credenciales("paciente.uno@example.com", clave))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("PACIENTE"));
    }

    @Test
    @DisplayName("Contraseña incorrecta")
    void contrasenaIncorrecta() throws Exception {
        crearPersonal("recepcion", "RECEPCIONISTA", contrasenaFicticia());
        Navegador navegador = navegador();
        navegador.get(SESION);

        navegador.post(LOGIN, credenciales("recepcion", contrasenaFicticia()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));

        navegador.get(SESION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(false));
    }

    @Test
    @DisplayName("Usuario inexistente")
    void usuarioInexistente() throws Exception {
        crearPersonal("recepcion", "RECEPCIONISTA", contrasenaFicticia());
        Navegador navegador = navegador();
        navegador.get(SESION);
        String detalleConClaveIncorrecta = navegador.post(LOGIN, credenciales("recepcion", contrasenaFicticia()))
                .andReturn().getResponse().getContentAsString();
        String detalleEsperado = json.readTree(detalleConClaveIncorrecta).get("detail").asText();

        navegador.post(LOGIN, credenciales("nadie", contrasenaFicticia()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                .andExpect(jsonPath("$.detail").value(detalleEsperado));
    }

    @Test
    @DisplayName("Usuario inactivo")
    void usuarioInactivo() throws Exception {
        String clave = contrasenaFicticia();
        crearPersonal("recepcion", "RECEPCIONISTA", clave, false);
        Navegador navegador = navegador();
        navegador.get(SESION);

        navegador.post(LOGIN, credenciales("recepcion", clave))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    @DisplayName("Login sin contraseña")
    void loginSinContrasena() throws Exception {
        crearPersonal("recepcion", "RECEPCIONISTA", contrasenaFicticia());
        Navegador navegador = navegador();
        navegador.get(SESION);

        navegador.post(LOGIN, credenciales("recepcion", ""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.errores[?(@.campo == 'password')]").exists());
    }

    @Test
    @DisplayName("Login sin token CSRF")
    void loginSinTokenCsrf() throws Exception {
        String clave = contrasenaFicticia();
        crearPersonal("recepcion", "RECEPCIONISTA", clave);
        Navegador navegador = navegador();
        navegador.get(SESION);

        navegador.postSinEncabezadoCsrf(LOGIN, credenciales("recepcion", clave))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CSRF_INVALIDO"));

        navegador.get(SESION)
                .andExpect(jsonPath("$.autenticado").value(false));
    }

    @Test
    @DisplayName("Token CSRF que no coincide")
    void tokenCsrfQueNoCoincide() throws Exception {
        String clave = contrasenaFicticia();
        crearPersonal("recepcion", "RECEPCIONISTA", clave);
        Navegador navegador = iniciarSesion("recepcion", clave);

        navegador.postConEncabezadoCsrf(LOGOUT, "", "valor-que-no-coincide")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CSRF_INVALIDO"));

        navegador.get(SESION)
                .andExpect(jsonPath("$.autenticado").value(true));
    }

    @Test
    @DisplayName("Token CSRF renovado al iniciar sesión")
    void tokenCsrfRenovadoAlIniciarSesion() throws Exception {
        String clave = contrasenaFicticia();
        crearPersonal("recepcion", "RECEPCIONISTA", clave);
        Navegador navegador = navegador();
        navegador.get(SESION);
        String anterior = navegador.tokenCsrf();

        navegador.post(LOGIN, credenciales("recepcion", clave))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"));

        assertThat(navegador.tokenCsrf()).isNotBlank().isNotEqualTo(anterior);
    }

    @Test
    @DisplayName("Nuevo identificador de sesión al iniciar sesión")
    void nuevoIdentificadorDeSesionAlIniciarSesion() throws Exception {
        String clave = contrasenaFicticia();
        crearPersonal("recepcion", "RECEPCIONISTA", clave);
        MockHttpSession anonima = new MockHttpSession();
        anonima.setAttribute("visita", "anonima");
        String idAnterior = anonima.getId();
        Navegador navegador = navegador();
        navegador.usarSesion(anonima);
        navegador.get(SESION);

        navegador.post(LOGIN, credenciales("recepcion", clave)).andExpect(status().isOk());

        assertThat(navegador.sesion().getId()).isNotEqualTo(idAnterior);
    }

    @Test
    @DisplayName("Logout con sesión iniciada")
    void logoutConSesionIniciada() throws Exception {
        String clave = contrasenaFicticia();
        crearPersonal("recepcion", "RECEPCIONISTA", clave);
        Navegador navegador = iniciarSesion("recepcion", clave);

        navegador.post(LOGOUT, "")
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("JSESSIONID", 0));

        // el navegador sigue mandando la cookie anterior
        navegador.get(SESION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(false));
    }

    @Test
    @DisplayName("Logout de un paciente")
    void logoutDeUnPaciente() throws Exception {
        String clave = contrasenaFicticia();
        crearPacienteConCuenta("Paciente", "Uno", "99000001", "paciente.uno@example.com", clave);
        Navegador navegador = iniciarSesion("paciente.uno@example.com", clave);

        navegador.post(LOGOUT, "").andExpect(status().isNoContent());
    }
}
