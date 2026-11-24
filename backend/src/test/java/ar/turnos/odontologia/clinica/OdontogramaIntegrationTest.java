package ar.turnos.odontologia.clinica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import ar.turnos.odontologia.shared.security.UsuarioAutenticado;
import ar.turnos.odontologia.usuarios.domain.Rol;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.MethodMode;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Un test por escenario del odontograma del spec {@code ficha-clinica} (C-13). */
class OdontogramaIntegrationTest extends IntegracionClinicaBase {

    /** {@code user(...)} deja el principal en el contexto de test, y MockMvc lo aplicaría a los pedidos siguientes. */
    @AfterEach
    void limpiarPrincipalInyectado() {
        TestSecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Registrar una caries")
    void registrarUnaCaries() throws Exception {
        MvcResult resultado = registrarOdontograma(como("ana.ejemplo"), pacienteUno, cambio(16, "O", "CARIES"));

        assertThat(status(resultado)).isEqualTo(200);
        JsonNode vigente = vigente();
        assertThat(vigente).hasSize(1);
        assertThat(entrada(vigente.get(0))).isEqualTo("16 O CARIES");
        assertThat(vigente.get(0).path("profesionalId").asLong()).isEqualTo(draA);
        assertThat(vigente.get(0).path("profesional").asText()).isEqualTo("Ana Ejemplo");
    }

    @Test
    @DisplayName("Un cambio posterior no sobrescribe el historial")
    void unCambioPosteriorNoSobrescribeElHistorial() throws Exception {
        registrarOdontograma(como("ana.ejemplo"), pacienteUno, cambio(16, "O", "CARIES"));
        reloj.ahoraEs(diciembre(1, 9, 0));

        registrarOdontograma(como("bruno.prueba"), pacienteUno, cambio(16, "O", "OBTURADO"));

        assertThat(entradas(vigente())).containsExactly("16 O OBTURADO");
        JsonNode historial = cuerpo(como("ana.ejemplo").get(odontograma(pacienteUno) + "/historial").andReturn());
        assertThat(entradas(historial)).containsExactly("16 O OBTURADO", "16 O CARIES");
        assertThat(historial.get(0).path("profesional").asText()).isEqualTo("Bruno Prueba");
        assertThat(historial.get(1).path("profesional").asText()).isEqualTo("Ana Ejemplo");
    }

    @Test
    @DisplayName("Estado de la pieza completa")
    void estadoDeLaPiezaCompleta() throws Exception {
        registrarOdontograma(como("ana.ejemplo"), pacienteUno, cambio(36, null, "AUSENTE"));

        JsonNode vigente = vigente();
        assertThat(vigente).hasSize(1);
        assertThat(vigente.get(0).path("pieza").asInt()).isEqualTo(36);
        assertThat(vigente.get(0).path("cara").isNull()).isTrue();
        assertThat(vigente.get(0).path("estado").asText()).isEqualTo("AUSENTE");
    }

    @Test
    @DisplayName("Pedido con un cambio inválido no guarda ninguno")
    void pedidoConUnCambioInvalidoNoGuardaNinguno() throws Exception {
        MvcResult resultado = registrarOdontograma(como("ana.ejemplo"), pacienteUno,
                cambio(16, "O", "CARIES"), cambio(19, "O", "CARIES"));

        assertThat(status(resultado)).isEqualTo(422);
        assertThat(codigo(resultado)).isEqualTo("PIEZA_INVALIDA");
        assertThat(registros()).isZero();
    }

    @Test
    @DisplayName("Odontólogo sin profesional vinculado")
    // csrf() de spring-security-test reemplaza el repositorio CSRF del filtro, que es un bean compartido: el contexto
    // se descarta para que los logins de los tests siguientes no lo hereden.
    @DirtiesContext(methodMode = MethodMode.AFTER_METHOD)
    void odontologoSinProfesionalVinculado() throws Exception {
        // La base no admite un ODONTOLOGO sin profesional (CHECK de V3): el principal se inyecta en el pedido para
        // probar la defensa del servicio (design D5). Desde C-41 la seguridad verifica en cada pedido que la cuenta
        // de la sesión exista y tenga la misma contraseña (design D3 de cuentas-del-personal), así que el principal
        // apunta a la cuenta real de ana.ejemplo, pero sin su profesional.
        long usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'ana.ejemplo'", Long.class);
        String hash = jdbc.queryForObject("SELECT password_hash FROM usuario WHERE id = ?", String.class, usuarioId);
        UsuarioAutenticado suelto = new UsuarioAutenticado(usuarioId, "ana.ejemplo", hash, Rol.ODONTOLOGO,
                null, null, true);

        MvcResult resultado = mvc.perform(MockMvcRequestBuilders.put(odontograma(pacienteUno))
                .with(user(suelto)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("cambios", List.of(cambio(16, "O", "CARIES"))))))
                .andReturn();

        assertThat(status(resultado)).isEqualTo(403);
        assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
        assertThat(registros()).isZero();
    }

    @Test
    @DisplayName("Pieza temporal válida")
    void piezaTemporalValida() throws Exception {
        assertThat(status(registrarOdontograma(como("ana.ejemplo"), pacienteUno, cambio(54, "O", "CARIES"))))
                .isEqualTo(200);
    }

    @Test
    @DisplayName("Pieza fuera de la notación")
    void piezaFueraDeLaNotacion() throws Exception {
        MvcResult resultado = registrarOdontograma(como("ana.ejemplo"), pacienteUno, cambio(56, "O", "CARIES"));

        assertThat(status(resultado)).isEqualTo(422);
        assertThat(codigo(resultado)).isEqualTo("PIEZA_INVALIDA");
    }

    @Test
    @DisplayName("Cara palatina en una pieza superior")
    void caraPalatinaEnUnaPiezaSuperior() throws Exception {
        assertThat(status(registrarOdontograma(como("ana.ejemplo"), pacienteUno, cambio(21, "P", "OBTURADO"))))
                .isEqualTo(200);
    }

    @Test
    @DisplayName("Cara palatina en una pieza inferior")
    void caraPalatinaEnUnaPiezaInferior() throws Exception {
        MvcResult resultado = registrarOdontograma(como("ana.ejemplo"), pacienteUno, cambio(31, "P", "OBTURADO"));

        assertThat(status(resultado)).isEqualTo(422);
        assertThat(codigo(resultado)).isEqualTo("CARA_INVALIDA");
    }

    @Test
    @DisplayName("Estado desconocido")
    void estadoDesconocido() throws Exception {
        MvcResult resultado = registrarOdontograma(como("ana.ejemplo"), pacienteUno, cambio(16, "O", "ROTO"));

        assertThat(status(resultado)).isEqualTo(422);
        assertThat(codigo(resultado)).isEqualTo("DATOS_INVALIDOS");
        assertThat(registros()).isZero();
    }

    @Test
    @DisplayName("Odontograma vacío")
    void odontogramaVacio() throws Exception {
        MvcResult resultado = como("ana.ejemplo").get(odontograma(pacienteUno)).andReturn();

        assertThat(status(resultado)).isEqualTo(200);
        assertThat(cuerpo(resultado).isArray()).isTrue();
        assertThat(cuerpo(resultado)).isEmpty();
    }

    @Test
    @DisplayName("El estado vigente muestra solo el último registro")
    void elEstadoVigenteMuestraSoloElUltimoRegistro() throws Exception {
        registrarOdontograma(como("ana.ejemplo"), pacienteUno, cambio(16, "O", "CARIES"), cambio(26, "M", "A_TRATAR"));
        reloj.ahoraEs(diciembre(1, 9, 0));
        registrarOdontograma(como("ana.ejemplo"), pacienteUno, cambio(16, "O", "OBTURADO"));

        assertThat(entradas(vigente())).containsExactly("16 O OBTURADO", "26 M A_TRATAR");
    }

    @Test
    @DisplayName("El administrador intenta modificar el odontograma")
    void elAdministradorIntentaModificarElOdontograma() throws Exception {
        MvcResult resultado = registrarOdontograma(como("admin"), pacienteUno, cambio(16, "O", "CARIES"));

        assertThat(status(resultado)).isEqualTo(403);
        assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
        assertThat(registros()).isZero();
        // y sí lo puede leer
        assertThat(status(como("admin").get(odontograma(pacienteUno)).andReturn())).isEqualTo(200);
    }

    @Test
    @DisplayName("La recepción consulta el odontograma")
    void laRecepcionConsultaElOdontograma() throws Exception {
        registrarOdontograma(como("ana.ejemplo"), pacienteUno, cambio(16, "O", "CARIES"));

        MvcResult resultado = como("recepcion").get(odontograma(pacienteUno)).andReturn();

        assertThat(status(resultado)).isEqualTo(403);
        assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
        assertThat(texto(resultado)).doesNotContain("CARIES");
        assertThat(status(como("recepcion").get(odontograma(pacienteUno) + "/historial").andReturn())).isEqualTo(403);
    }

    private JsonNode vigente() throws Exception {
        return cuerpo(como("ana.ejemplo").get(odontograma(pacienteUno)).andReturn());
    }

    private int registros() {
        return jdbc.queryForObject("SELECT count(*) FROM odontograma_registro", Integer.class);
    }

    private static String entrada(JsonNode registro) {
        return registro.path("pieza").asInt() + " " + registro.path("cara").asText() + " "
                + registro.path("estado").asText();
    }

    private static List<String> entradas(JsonNode registros) {
        List<String> lista = new ArrayList<>();
        registros.forEach(r -> lista.add(entrada(r)));
        return lista;
    }
}
