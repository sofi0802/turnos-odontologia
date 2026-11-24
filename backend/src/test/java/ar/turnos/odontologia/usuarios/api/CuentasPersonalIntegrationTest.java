package ar.turnos.odontologia.usuarios.api;

import ar.turnos.odontologia.shared.security.IntegracionSeguridadBase;
import ar.turnos.odontologia.shared.security.Navegador;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec cuentas-personal (C-41): cuentas del personal administradas por el ADMIN. PostgreSQL real y datos
 * ficticios: admin, recepcion, Ana Ejemplo (sin cuenta), Bruno Prueba (cuenta bruno.prueba), Carla Inactiva y
 * la cuenta de paciente paciente.uno@example.com. El cierre de sesiones está en CierreDeSesionesIntegrationTest.
 */
@Tag("integration")
@ExtendWith(OutputCaptureExtension.class)
class CuentasPersonalIntegrationTest extends IntegracionSeguridadBase {

    static final String USUARIOS = "/api/v1/usuarios";

    String claveAdmin;
    String claveRecepcion;
    String claveBruno;
    String clavePaciente;
    long adminId;
    long recepcionId;
    long brunoId;
    long pacienteUsuarioId;
    long anaProfesionalId;
    long brunoProfesionalId;
    long carlaProfesionalId;

    @BeforeEach
    void datos() {
        claveAdmin = contrasenaFicticia();
        claveRecepcion = contrasenaFicticia();
        claveBruno = contrasenaFicticia();
        clavePaciente = contrasenaFicticia();
        adminId = crearPersonal("admin", "ADMIN", claveAdmin);
        recepcionId = crearPersonal("recepcion", "RECEPCIONISTA", claveRecepcion);
        anaProfesionalId = profesional("Ana", "Ejemplo", "MP-0001", true);
        brunoProfesionalId = profesional("Bruno", "Prueba", "MP-0002", true);
        carlaProfesionalId = profesional("Carla", "Inactiva", "MP-0003", false);
        brunoId = jdbc.queryForObject("INSERT INTO usuario(username, password_hash, rol, profesional_id) "
                + "VALUES ('bruno.prueba', ?, 'ODONTOLOGO', ?) RETURNING id", Long.class,
                passwordEncoder.encode(claveBruno), brunoProfesionalId);
        crearPacienteConCuenta("Paciente", "Uno", "99000001", "paciente.uno@example.com", clavePaciente);
        pacienteUsuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'paciente.uno@example.com'",
                Long.class);
    }

    long profesional(String nombre, String apellido, String matricula, boolean activo) {
        return jdbc.queryForObject("INSERT INTO profesional(nombre, apellido, matricula, activo) VALUES (?,?,?,?) RETURNING id",
                Long.class, nombre, apellido, matricula, activo);
    }

    String alta(String username, String rol, Long profesionalId) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("username", username);
        cuerpo.put("rol", rol);
        cuerpo.put("profesionalId", profesionalId);
        return json.writeValueAsString(cuerpo);
    }

    JsonNode cuerpo(MvcResult resultado) throws Exception {
        return json.readTree(resultado.getResponse().getContentAsString());
    }

    Navegador admin() throws Exception {
        return iniciarSesion("admin", claveAdmin);
    }

    long cuentas() {
        return jdbc.queryForObject("SELECT count(*) FROM usuario", Long.class);
    }

    @Nested
    @DisplayName("Operaciones de cuentas solo para el administrador")
    class SoloAdministrador {

        @Test
        @DisplayName("Recepción intenta crear una cuenta")
        void recepcionCrea() throws Exception {
            long antes = cuentas();

            iniciarSesion("recepcion", claveRecepcion).post(USUARIOS, alta("recepcion2", "RECEPCIONISTA", null))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));

            assertThat(cuentas()).isEqualTo(antes);
        }

        @Test
        @DisplayName("Un odontólogo intenta listar las cuentas")
        void odontologoLista() throws Exception {
            iniciarSesion("bruno.prueba", claveBruno).get(USUARIOS)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        }

        @Test
        @DisplayName("Pedido sin sesión")
        void sinSesion() throws Exception {
            navegador().get(USUARIOS)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        }
    }

    @Nested
    @DisplayName("Lista de las cuentas del personal")
    class Lista {

        @Test
        @DisplayName("Listar las cuentas")
        void listar() throws Exception {
            MvcResult resultado = admin().get(USUARIOS).andExpect(status().isOk()).andReturn();
            String texto = resultado.getResponse().getContentAsString();
            JsonNode lista = cuerpo(resultado);

            assertThat(lista).extracting(n -> n.get("username").asText())
                    .containsExactlyInAnyOrder("admin", "recepcion", "bruno.prueba");
            JsonNode bruno = buscar(lista, "bruno.prueba");
            assertThat(bruno.get("profesionalId").asLong()).isEqualTo(brunoProfesionalId);
            assertThat(bruno.get("rol").asText()).isEqualTo("ODONTOLOGO");
            assertThat(bruno.get("activo").asBoolean()).isTrue();
            assertThat(bruno.get("debeCambiarContrasena").asBoolean()).isFalse();
            assertThat(texto).doesNotContain("paciente.uno@example.com", "password", "$2a$");
        }
    }

    @Nested
    @DisplayName("Alta de una cuenta con contraseña temporal")
    class Alta {

        @Test
        @DisplayName("Alta de una recepcionista")
        void altaRecepcionista() throws Exception {
            MvcResult resultado = admin().post(USUARIOS, alta("recepcion2", "RECEPCIONISTA", null))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.username").value("recepcion2"))
                    .andExpect(jsonPath("$.rol").value("RECEPCIONISTA"))
                    .andExpect(jsonPath("$.activo").value(true))
                    .andExpect(jsonPath("$.debeCambiarContrasena").value(true))
                    .andReturn();
            String temporal = cuerpo(resultado).get("contrasenaTemporal").asText();

            assertThat(temporal.length()).isBetween(8, 72);
            iniciarSesion("recepcion2", temporal).get(SESION)
                    .andExpect(jsonPath("$.autenticado").value(true))
                    .andExpect(jsonPath("$.username").value("recepcion2"));
            assertThat(jdbc.queryForObject("SELECT password_hash FROM usuario WHERE username = 'recepcion2'",
                    String.class)).isNotEqualTo(temporal).startsWith("$2");
        }

        @Test
        @DisplayName("La contraseña temporal no se vuelve a mostrar")
        void noSeVuelveAMostrar() throws Exception {
            Navegador admin = admin();
            admin.post(USUARIOS, alta("recepcion2", "RECEPCIONISTA", null)).andExpect(status().isCreated());

            MvcResult lista = admin.get(USUARIOS).andExpect(status().isOk()).andReturn();

            assertThat(buscar(cuerpo(lista), "recepcion2").has("contrasenaTemporal")).isFalse();
        }

        @Test
        @DisplayName("Usuario normalizado")
        void normalizado() throws Exception {
            admin().post(USUARIOS, alta(" Recepcion2 ", "RECEPCIONISTA", null))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value("recepcion2"));
        }
    }

    @Nested
    @DisplayName("Vínculo de la cuenta con un profesional")
    class Vinculo {

        @Test
        @DisplayName("Alta de un odontólogo vinculado")
        void odontologoVinculado() throws Exception {
            MvcResult resultado = admin().post(USUARIOS, alta("ana.ejemplo", "ODONTOLOGO", anaProfesionalId))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.profesionalId").value(anaProfesionalId))
                    .andReturn();
            String temporal = cuerpo(resultado).get("contrasenaTemporal").asText();

            iniciarSesion("ana.ejemplo", temporal).get(SESION)
                    .andExpect(jsonPath("$.profesionalId").value(anaProfesionalId));
        }

        @Test
        @DisplayName("Odontólogo sin profesional")
        void odontologoSinProfesional() throws Exception {
            admin().post(USUARIOS, alta("ana.ejemplo", "ODONTOLOGO", null))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                    .andExpect(jsonPath("$.errores[0].campo").value("profesionalId"));
        }

        @Test
        @DisplayName("Recepcionista con profesional")
        void recepcionistaConProfesional() throws Exception {
            admin().post(USUARIOS, alta("recepcion2", "RECEPCIONISTA", anaProfesionalId))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                    .andExpect(jsonPath("$.errores[0].campo").value("profesionalId"));
        }

        @Test
        @DisplayName("Profesional que ya tiene cuenta")
        void profesionalConCuenta() throws Exception {
            admin().post(USUARIOS, alta("bruno2", "ODONTOLOGO", brunoProfesionalId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("PROFESIONAL_CON_CUENTA"));
        }

        @Test
        @DisplayName("Profesional inactivo")
        void profesionalInactivo() throws Exception {
            admin().post(USUARIOS, alta("carla", "ODONTOLOGO", carlaProfesionalId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("PROFESIONAL_NO_DISPONIBLE"));
        }

        @Test
        @DisplayName("Profesional inexistente")
        void profesionalInexistente() throws Exception {
            admin().post(USUARIOS, alta("nadie", "ODONTOLOGO", 999_999L))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("PROFESIONAL_NO_DISPONIBLE"));
        }

        @Test
        @DisplayName("Administrador que también atiende")
        void adminQueAtiende() throws Exception {
            admin().post(USUARIOS, alta("dueno", "ADMIN", anaProfesionalId))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.profesionalId").value(anaProfesionalId));
        }
    }

    @Nested
    @DisplayName("Validación del alta")
    class Validacion {

        @Test
        @DisplayName("Rol de paciente")
        void rolPaciente() throws Exception {
            long antes = cuentas();

            admin().post(USUARIOS, alta("nuevo.paciente", "PACIENTE", null))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                    .andExpect(jsonPath("$.errores[0].campo").value("rol"));

            assertThat(cuentas()).isEqualTo(antes);
        }

        @Test
        @DisplayName("Rol desconocido")
        void rolDesconocido() throws Exception {
            admin().post(USUARIOS, alta("alguien", "DIRECTOR", null))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("rol"));
        }

        @Test
        @DisplayName("Usuario repetido")
        void usuarioRepetido() throws Exception {
            admin().post(USUARIOS, alta("Recepcion", "RECEPCIONISTA", null))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("USUARIO_EXISTENTE"));
        }

        @Test
        @DisplayName("Usuario demasiado corto")
        void usuarioCorto() throws Exception {
            admin().post(USUARIOS, alta("ab", "RECEPCIONISTA", null))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                    .andExpect(jsonPath("$.errores[0].campo").value("username"));
        }
    }

    @Nested
    @DisplayName("Desactivar y reactivar una cuenta")
    class DesactivarReactivar {

        @Test
        @DisplayName("Desactivar una cuenta")
        void desactivar() throws Exception {
            admin().post(USUARIOS + "/" + recepcionId + "/desactivar", null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activo").value(false));

            Navegador recepcion = navegador();
            recepcion.get(SESION);
            recepcion.post(LOGIN, credenciales("recepcion", claveRecepcion))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
        }

        @Test
        @DisplayName("Reactivar una cuenta")
        void reactivar() throws Exception {
            jdbc.update("UPDATE usuario SET activo = false WHERE id = ?", recepcionId);

            admin().post(USUARIOS + "/" + recepcionId + "/activar", null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activo").value(true));

            iniciarSesion("recepcion", claveRecepcion).get(SESION)
                    .andExpect(jsonPath("$.autenticado").value(true));
        }

        @Test
        @DisplayName("Cuenta de un paciente")
        void cuentaDePaciente() throws Exception {
            admin().post(USUARIOS + "/" + pacienteUsuarioId + "/desactivar", null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("USUARIO_INEXISTENTE"));
        }

        @Test
        @DisplayName("Cuenta inexistente")
        void cuentaInexistente() throws Exception {
            admin().post(USUARIOS + "/999999/activar", null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("USUARIO_INEXISTENTE"));
        }
    }

    @Nested
    @DisplayName("Restablecer la contraseña de una cuenta")
    class Restablecer {

        @Test
        @DisplayName("Restablecer la contraseña de un odontólogo")
        void restablecer() throws Exception {
            MvcResult resultado = admin().post(USUARIOS + "/" + brunoId + "/restablecer-contrasena", null)
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andReturn();
            String temporal = cuerpo(resultado).get("contrasenaTemporal").asText();

            assertThat(temporal).isNotEqualTo(claveBruno);
            Navegador conAnterior = navegador();
            conAnterior.get(SESION);
            conAnterior.post(LOGIN, credenciales("bruno.prueba", claveBruno))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
            Navegador conTemporal = navegador();
            conTemporal.get(SESION);
            conTemporal.post(LOGIN, credenciales("bruno.prueba", temporal))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.debeCambiarContrasena").value(true));
        }
    }

    @Nested
    @DisplayName("Protección de la propia cuenta")
    class CuentaPropia {

        @Test
        @DisplayName("El administrador intenta desactivarse")
        void desactivarse() throws Exception {
            admin().post(USUARIOS + "/" + adminId + "/desactivar", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("OPERACION_SOBRE_CUENTA_PROPIA"));

            assertThat(jdbc.queryForObject("SELECT activo FROM usuario WHERE id = ?", Boolean.class, adminId)).isTrue();
        }

        @Test
        @DisplayName("El administrador intenta restablecer su contraseña")
        void restablecerse() throws Exception {
            admin().post(USUARIOS + "/" + adminId + "/restablecer-contrasena", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("OPERACION_SOBRE_CUENTA_PROPIA"));
        }

        @Test
        @DisplayName("Un administrador desactiva a otro")
        void desactivaAOtro() throws Exception {
            long duenoId = crearPersonal("dueno", "ADMIN", contrasenaFicticia());

            admin().post(USUARIOS + "/" + duenoId + "/desactivar", null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activo").value(false));

            assertThat(jdbc.queryForObject("SELECT activo FROM usuario WHERE id = ?", Boolean.class, adminId)).isTrue();
        }
    }

    @Nested
    @DisplayName("Contraseñas fuera de los logs")
    class Logs {

        @Test
        @DisplayName("Alta registrada sin contraseña")
        void altaSinContrasenaEnLogs(CapturedOutput salida) throws Exception {
            MvcResult resultado = admin().post(USUARIOS, alta("recepcion2", "RECEPCIONISTA", null))
                    .andExpect(status().isCreated()).andReturn();
            String temporal = cuerpo(resultado).get("contrasenaTemporal").asText();
            String hash = jdbc.queryForObject("SELECT password_hash FROM usuario WHERE username = 'recepcion2'",
                    String.class);

            assertThat(salida.getAll()).contains("recepcion2").contains("admin");
            assertThat(salida.getAll()).doesNotContain(temporal).doesNotContain(hash);
        }
    }

    static JsonNode buscar(JsonNode lista, String username) {
        for (JsonNode cuenta : lista) {
            if (username.equals(cuenta.get("username").asText())) {
                return cuenta;
            }
        }
        throw new AssertionError("No está la cuenta " + username);
    }
}
