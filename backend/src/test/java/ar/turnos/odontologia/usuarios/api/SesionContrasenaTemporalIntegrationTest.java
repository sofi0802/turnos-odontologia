package ar.turnos.odontologia.usuarios.api;

import ar.turnos.odontologia.shared.security.IntegracionSeguridadBase;
import ar.turnos.odontologia.shared.security.Navegador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Specs seguridad-autenticacion (ADDED por C-41: "Sesión con contraseña temporal" y "Cambio de la propia
 * contraseña") y cuentas-personal ("Cierre de las sesiones de una cuenta"). PostgreSQL real, datos ficticios.
 */
@Tag("integration")
class SesionContrasenaTemporalIntegrationTest extends IntegracionSeguridadBase {

    static final String TURNOS = "/api/v1/turnos?desde=2026-12-01T00:00:00-03:00&hasta=2026-12-02T00:00:00-03:00";
    static final String CAMBIO = "/api/v1/cuenta/contrasena";

    String claveAdmin;
    String claveRecepcion;
    String temporalRecepcion2;
    long recepcionId;
    long recepcion2Id;

    @BeforeEach
    void datos() {
        claveAdmin = contrasenaFicticia();
        claveRecepcion = contrasenaFicticia();
        temporalRecepcion2 = contrasenaFicticia();
        crearPersonal("admin", "ADMIN", claveAdmin);
        recepcionId = crearPersonal("recepcion", "RECEPCIONISTA", claveRecepcion);
        recepcion2Id = crearPersonal("recepcion2", "RECEPCIONISTA", temporalRecepcion2);
        jdbc.update("UPDATE usuario SET debe_cambiar_contrasena = true WHERE id = ?", recepcion2Id);
    }

    String cambio(String actual, String nueva) throws Exception {
        return json.writeValueAsString(Map.of("actual", actual, "nueva", nueva));
    }

    @Nested
    @DisplayName("Sesión con contraseña temporal")
    class SesionTemporal {

        @Test
        @DisplayName("Login con contraseña temporal")
        void loginTemporal() throws Exception {
            Navegador navegador = navegador();
            navegador.get(SESION);
            navegador.post(LOGIN, credenciales("recepcion2", temporalRecepcion2))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rol").value("RECEPCIONISTA"))
                    .andExpect(jsonPath("$.debeCambiarContrasena").value(true));

            navegador.get(SESION).andExpect(jsonPath("$.debeCambiarContrasena").value(true));
        }

        @Test
        @DisplayName("Operación bloqueada hasta cambiar la contraseña")
        void operacionBloqueada() throws Exception {
            iniciarSesion("recepcion2", temporalRecepcion2).get(TURNOS)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("CAMBIO_DE_CONTRASENA_PENDIENTE"));
        }

        @Test
        @DisplayName("Salir con la contraseña pendiente")
        void salir() throws Exception {
            Navegador navegador = iniciarSesion("recepcion2", temporalRecepcion2);

            navegador.post(LOGOUT, null).andExpect(status().isNoContent());
            navegador.get(SESION).andExpect(jsonPath("$.autenticado").value(false));
        }

        @Test
        @DisplayName("Cuenta sin contraseña temporal")
        void sinTemporal() throws Exception {
            Navegador navegador = navegador();
            navegador.get(SESION);
            navegador.post(LOGIN, credenciales("recepcion", claveRecepcion))
                    .andExpect(jsonPath("$.debeCambiarContrasena").value(false));

            navegador.get(TURNOS).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Cambio de la propia contraseña")
    class CambioPropio {

        @Test
        @DisplayName("Cambio después del primer ingreso")
        void cambioTrasPrimerIngreso() throws Exception {
            Navegador navegador = iniciarSesion("recepcion2", temporalRecepcion2);
            String nueva = contrasenaFicticia();

            navegador.post(CAMBIO, cambio(temporalRecepcion2, nueva)).andExpect(status().isNoContent());

            navegador.get(SESION).andExpect(jsonPath("$.debeCambiarContrasena").value(false));
            navegador.get(TURNOS).andExpect(status().isOk());
            Navegador conTemporal = navegador();
            conTemporal.get(SESION);
            conTemporal.post(LOGIN, credenciales("recepcion2", temporalRecepcion2))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
            iniciarSesion("recepcion2", nueva).get(SESION).andExpect(jsonPath("$.autenticado").value(true));
        }

        @Test
        @DisplayName("Contraseña actual incorrecta")
        void actualIncorrecta() throws Exception {
            String hashAntes = hash("recepcion");

            iniciarSesion("recepcion", claveRecepcion).post(CAMBIO, cambio(contrasenaFicticia(), contrasenaFicticia()))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("CONTRASENA_ACTUAL_INCORRECTA"));

            assertThat(hash("recepcion")).isEqualTo(hashAntes);
        }

        @Test
        @DisplayName("Contraseña nueva demasiado corta")
        void nuevaCorta() throws Exception {
            iniciarSesion("recepcion", claveRecepcion).post(CAMBIO, cambio(claveRecepcion, "1234567"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                    .andExpect(jsonPath("$.errores[0].campo").value("nueva"));
        }

        @Test
        @DisplayName("Contraseña nueva igual a la actual")
        void nuevaIgual() throws Exception {
            iniciarSesion("recepcion", claveRecepcion).post(CAMBIO, cambio(claveRecepcion, claveRecepcion))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                    .andExpect(jsonPath("$.errores[0].campo").value("nueva"));
        }

        @Test
        @DisplayName("Un paciente no usa este cambio")
        void paciente() throws Exception {
            String clavePaciente = contrasenaFicticia();
            crearPacienteConCuenta("Paciente", "Uno", "99000001", "paciente.uno@example.com", clavePaciente);

            iniciarSesion("paciente.uno@example.com", clavePaciente)
                    .post(CAMBIO, cambio(clavePaciente, contrasenaFicticia()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        }
    }

    @Nested
    @DisplayName("Cierre de las sesiones de una cuenta")
    class CierreDeSesiones {

        @Test
        @DisplayName("Sesión abierta de una cuenta desactivada")
        void desactivada() throws Exception {
            Navegador recepcion = iniciarSesion("recepcion", claveRecepcion);
            recepcion.get(TURNOS).andExpect(status().isOk());

            iniciarSesion("admin", claveAdmin).post("/api/v1/usuarios/" + recepcionId + "/desactivar", null)
                    .andExpect(status().isOk());

            recepcion.get(TURNOS)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        }

        @Test
        @DisplayName("Sesión abierta de una cuenta restablecida")
        void restablecida() throws Exception {
            Navegador recepcion = iniciarSesion("recepcion", claveRecepcion);
            recepcion.get(TURNOS).andExpect(status().isOk());

            iniciarSesion("admin", claveAdmin).post("/api/v1/usuarios/" + recepcionId + "/restablecer-contrasena", null)
                    .andExpect(status().isOk());

            recepcion.get(TURNOS)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        }
    }

    String hash(String username) {
        return jdbc.queryForObject("SELECT password_hash FROM usuario WHERE username = ?", String.class, username);
    }
}
