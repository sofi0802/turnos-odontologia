package ar.turnos.odontologia.usuarios.api;

import ar.turnos.odontologia.shared.security.IntegracionSeguridadBase;
import ar.turnos.odontologia.shared.security.Navegador;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Escenarios del spec busqueda-pacientes. Datos ficticios. */
@Tag("integration")
@ExtendWith(OutputCaptureExtension.class)
class BusquedaPacientesIntegrationTest extends IntegracionSeguridadBase {

    private static final String RUTA = "/api/v1/pacientes";
    private static final String CLAVE = "clave-ficticia-" + System.nanoTime();

    @BeforeEach
    void datos() {
        crearPacienteConCuenta("Paciente", "Uno", "99000001", "paciente.uno@example.com", CLAVE);
        crearPacienteSinCuenta("Paciente", "Dos", "99000002", "paciente.dos@example.com");
        crearPacienteSinCuenta("Carla", "Ficticia", "99000003", "carla.ficticia@example.com");
        crearPacienteSinCuenta("Julián", "Ejemplar", "99000004", "julian.ejemplar@example.com");
        crearPersonal("recepcion", "RECEPCIONISTA", CLAVE);
        crearPersonal("admin", "ADMIN", CLAVE);
        crearPersonal("ana.ejemplo", "ODONTOLOGO", CLAVE);
    }

    private Navegador recepcion() throws Exception {
        return iniciarSesion("recepcion", CLAVE);
    }

    private String buscar(Navegador navegador, String pedido) throws Exception {
        return navegador.get(RUTA + pedido).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
    }

    private List<String> nombres(String cuerpo) throws Exception {
        List<String> nombres = new ArrayList<>();
        for (JsonNode paciente : json.readTree(cuerpo)) {
            nombres.add(paciente.get("nombre").asText() + " " + paciente.get("apellido").asText());
        }
        return nombres;
    }

    // --- DNI ---

    @Test
    @DisplayName("Búsqueda por DNI exacto")
    void dniExacto() throws Exception {
        JsonNode lista = json.readTree(buscar(recepcion(), "?q=99000003"));

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("id").asLong()).isEqualTo(
                jdbc.queryForObject("SELECT id FROM paciente WHERE dni = '99000003'", Long.class));
        assertThat(lista.get(0).get("nombre").asText()).isEqualTo("Carla");
        assertThat(lista.get(0).get("apellido").asText()).isEqualTo("Ficticia");
        assertThat(lista.get(0).get("dni").asText()).isEqualTo("99000003");
    }

    @Test
    @DisplayName("Búsqueda por el comienzo del DNI")
    void comienzoDelDni() throws Exception {
        assertThat(json.readTree(buscar(recepcion(), "?q=9900000"))).hasSize(4);
    }

    @Test
    @DisplayName("DNI sin coincidencias")
    void dniSinCoincidencias() throws Exception {
        assertThat(json.readTree(buscar(recepcion(), "?q=12345678"))).isEmpty();
    }

    // --- Nombre y apellido ---

    @Test
    @DisplayName("Búsqueda por parte del apellido")
    void parteDelApellido() throws Exception {
        assertThat(nombres(buscar(recepcion(), "?q=fict"))).containsExactly("Carla Ficticia");
    }

    @Test
    @DisplayName("Búsqueda por nombre y apellido juntos")
    void nombreYApellido() throws Exception {
        assertThat(nombres(buscar(recepcion(), "?q=paciente dos"))).containsExactly("Paciente Dos");
    }

    @Test
    @DisplayName("Sin distinguir mayúsculas ni tildes")
    void sinMayusculasNiTildes() throws Exception {
        assertThat(nombres(buscar(recepcion(), "?q=JULIAN"))).containsExactly("Julián Ejemplar");
    }

    @Test
    @DisplayName("Nombre sin coincidencias")
    void nombreSinCoincidencias() throws Exception {
        assertThat(json.readTree(buscar(recepcion(), "?q=zzz"))).isEmpty();
    }

    @Test
    @DisplayName("Los comodines se buscan como texto")
    void comodines() throws Exception {
        assertThat(json.readTree(buscar(recepcion(), "?q=%%"))).isEmpty();
    }

    // --- Datos mínimos, límite y orden ---

    @Test
    @DisplayName("La respuesta no trae otros datos")
    void datosMinimos() throws Exception {
        String cuerpo = buscar(recepcion(), "?q=99000001");

        JsonNode lista = json.readTree(cuerpo);
        assertThat(lista).hasSize(1);
        List<String> propiedades = new ArrayList<>();
        lista.get(0).fieldNames().forEachRemaining(propiedades::add);
        assertThat(propiedades).containsExactlyInAnyOrder("id", "nombre", "apellido", "dni");
        assertThat(cuerpo).doesNotContain("paciente.uno@example.com");
    }

    @Test
    @DisplayName("Más coincidencias que el límite")
    void masQueElLimite() throws Exception {
        for (int i = 1; i <= 12; i++) {
            crearPacienteSinCuenta("Nombre" + i, "Prueba", "99100" + (i < 10 ? "00" + i : "0" + i),
                    "p" + i + "@example.com");
        }

        assertThat(json.readTree(buscar(recepcion(), "?q=prueba"))).hasSize(10);
    }

    @Test
    @DisplayName("Orden por apellido y nombre")
    void orden() throws Exception {
        assertThat(nombres(buscar(recepcion(), "?q=paciente"))).containsExactly("Paciente Dos", "Paciente Uno");
    }

    // --- Consulta inválida ---

    private void assertInvalida(String pedido) throws Exception {
        recepcion().get(RUTA + pedido)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.errores[0].campo").value("q"))
                .andExpect(jsonPath("$.errores[0].mensaje").isNotEmpty());
    }

    @Test
    @DisplayName("Falta el término")
    void faltaElTermino() throws Exception {
        assertInvalida("");
    }

    @Test
    @DisplayName("Término de una sola letra")
    void unaLetra() throws Exception {
        assertInvalida("?q=f");
    }

    @Test
    @DisplayName("DNI demasiado corto")
    void dniCorto() throws Exception {
        assertInvalida("?q=99");
    }

    @Test
    @DisplayName("Término demasiado largo")
    void demasiadoLargo() throws Exception {
        String largo = "x".repeat(51);

        String cuerpo = recepcion().get(RUTA + "?q=" + largo)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.errores[0].campo").value("q"))
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain(largo);
    }

    // --- Roles ---

    @Test
    @DisplayName("El odontólogo busca un paciente")
    void odontologo() throws Exception {
        assertThat(nombres(buscar(iniciarSesion("ana.ejemplo", CLAVE), "?q=99000003")))
                .containsExactly("Carla Ficticia");
    }

    @Test
    @DisplayName("El administrador busca un paciente")
    void administrador() throws Exception {
        assertThat(nombres(buscar(iniciarSesion("admin", CLAVE), "?q=fict"))).containsExactly("Carla Ficticia");
    }

    @Test
    @DisplayName("Un paciente intenta buscar pacientes")
    void pacienteNoBusca() throws Exception {
        Navegador paciente = iniciarSesion("paciente.uno@example.com", CLAVE);

        String cuerpo = paciente.get(RUTA + "?q=99000002")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"))
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain("99000002").doesNotContain("Dos");
    }

    @Test
    @DisplayName("Búsqueda sin sesión")
    void sinSesion() throws Exception {
        navegador().get(RUTA + "?q=99000001")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    // --- Dato personal ---

    @Test
    @DisplayName("La búsqueda no deja el DNI en los logs")
    void sinDniEnLosLogs(CapturedOutput salida) throws Exception {
        Navegador navegador = recepcion();
        navegador.get(RUTA + "?q=99000003").andExpect(status().isOk());
        navegador.get(RUTA + "?q=9").andExpect(status().isUnprocessableEntity());

        assertThat(salida.getAll()).doesNotContain("99000003").doesNotContain("Ficticia");
    }

    @Test
    @DisplayName("La respuesta no se guarda en caché")
    void sinCache() throws Exception {
        String cache = recepcion().get(RUTA + "?q=fict").andExpect(status().isOk()).andReturn().getResponse()
                .getHeader("Cache-Control");

        assertThat(cache).contains("no-store");
    }
}
