package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.shared.security.Navegador;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * El paciente consulta su propia cobertura en "Mi cuenta" (C-18, spec afiliaciones-pacientes, tarea 5.2): se
 * identifica solo por la sesión, sin ids en el pedido.
 */
@Tag("integration")
class CoberturaPropiaIntegrationTest extends IntegracionCoberturasBase {

    private long planBase;

    @BeforeEach
    void catalogo() {
        long ose = obraSocial("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL");
        planBase = plan(ose, "Plan Base");
    }

    @Test
    @DisplayName("Paciente con cobertura")
    void pacienteConCobertura() throws Exception {
        // GIVEN "Paciente Uno" tiene "Plan Base" vigente con el número OSE-000001
        afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);

        // WHEN paciente.uno consulta su cobertura
        MvcResult r = get(como("paciente.uno"), COBERTURA_PROPIA);

        // THEN la respuesta es 200 con "Obra Social Ejemplo", "Plan Base", OSE-000001 y la vigencia
        assertThat(status(r)).isEqualTo(200);
        JsonNode c = cuerpo(r);
        assertThat(c.path("particular").asBoolean()).isFalse();
        assertThat(c.path("afiliacion").path("obraSocial").path("nombre").asText()).isEqualTo("Obra Social Ejemplo");
        assertThat(c.path("afiliacion").path("plan").path("nombre").asText()).isEqualTo("Plan Base");
        assertThat(c.path("afiliacion").path("numeroAfiliado").asText()).isEqualTo("OSE-000001");
        assertThat(c.path("afiliacion").path("vigenteDesde").asText()).isEqualTo("2026-01-01");
        assertThat(c.path("afiliacion").path("vigenteHasta").isNull()).isTrue();
    }

    @Test
    @DisplayName("Paciente particular")
    void pacienteParticular() throws Exception {
        // WHEN paciente.dos, sin afiliaciones, consulta su cobertura
        MvcResult r = get(como("paciente.dos"), COBERTURA_PROPIA);

        // THEN la respuesta es 200 e indica que se atiende como particular
        assertThat(status(r)).isEqualTo(200);
        assertThat(cuerpo(r).path("particular").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("El paciente no ve la cobertura de otro")
    void elPacienteNoVeLaCoberturaDeOtro() throws Exception {
        // GIVEN "Paciente Uno" tiene "Plan Base" vigente con el número OSE-000001
        afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);
        Navegador dos = como("paciente.dos");

        // WHEN paciente.dos consulta su cobertura agregando el id de "Paciente Uno" al pedido
        MvcResult porParametro = get(dos, COBERTURA_PROPIA + "?pacienteId=" + pacienteUno + "&id=" + pacienteUno);
        MvcResult porRuta = get(dos, COBERTURA_PROPIA + "/" + pacienteUno);

        // THEN la respuesta indica que se atiende como particular y no contiene OSE-000001
        assertThat(status(porParametro)).isEqualTo(200);
        assertThat(cuerpo(porParametro).path("particular").asBoolean()).isTrue();
        assertThat(texto(porParametro)).doesNotContain("OSE-000001");
        // AND un id en la ruta no es una ruta del paciente
        assertThat(status(porRuta)).isGreaterThanOrEqualTo(400);
        assertThat(texto(porRuta)).doesNotContain("OSE-000001");
    }

    @Test
    @DisplayName("El paciente no usa las rutas del personal")
    void elPacienteNoUsaLasRutasDelPersonal() throws Exception {
        // GIVEN "Paciente Uno" tiene "Plan Base" vigente
        afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);
        Navegador uno = como("paciente.uno");

        // WHEN paciente.uno lista las afiliaciones de "Paciente Uno" por la ruta del personal
        MvcResult afiliaciones = get(uno, afiliaciones(pacienteUno));
        MvcResult cobertura = get(uno, cobertura(pacienteUno));

        // THEN la respuesta es 403 con el código ACCESO_DENEGADO
        assertThat(status(afiliaciones)).isEqualTo(403);
        assertThat(codigoDeError(afiliaciones)).isEqualTo("ACCESO_DENEGADO");
        assertThat(status(cobertura)).isEqualTo(403);
        assertThat(texto(cobertura)).doesNotContain("OSE-000001");
    }

    @Test
    @DisplayName("El paciente no modifica su cobertura")
    void elPacienteNoModificaSuCobertura() throws Exception {
        long id = afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);
        Navegador uno = como("paciente.uno");

        MvcResult post = post(uno, COBERTURA_PROPIA, afiliacionSolicitud(planBase, "OSE-000002", "2026-01-01", null));
        MvcResult put = put(uno, COBERTURA_PROPIA, Map.of("numeroAfiliado", "OSE-000002"));
        MvcResult delete = delete(uno, COBERTURA_PROPIA);

        assertThat(status(post)).isGreaterThanOrEqualTo(400);
        assertThat(status(put)).isGreaterThanOrEqualTo(400);
        assertThat(status(delete)).isGreaterThanOrEqualTo(400);
        assertThat(cantidad("afiliacion_paciente")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT numero_afiliado FROM afiliacion_paciente WHERE id = ?", String.class, id))
                .isEqualTo("OSE-000001");
    }

    @Test
    @DisplayName("El personal no usa la ruta del paciente")
    void elPersonalNoUsaLaRutaDelPaciente() throws Exception {
        for (String usuario : new String[] {"admin", "recepcion", "ana.ejemplo"}) {
            MvcResult r = get(como(usuario), COBERTURA_PROPIA);
            assertThat(status(r)).as(usuario).isEqualTo(403);
        }
    }

    @Test
    @DisplayName("Sin sesión no hay cobertura propia")
    void sinSesion() throws Exception {
        MvcResult r = get(navegador(), COBERTURA_PROPIA);
        assertThat(status(r)).isEqualTo(401);
    }

    @Test
    @DisplayName("La cobertura propia solo muestra la vigente hoy, sin historial")
    void soloLaVigenteHoy() throws Exception {
        afiliacion(pacienteUno, planBase, "OSE-000001", "2025-01-01", "2025-12-31");
        afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);
        MvcResult r = get(como("paciente.uno"), COBERTURA_PROPIA);
        assertThat(cuerpo(r).path("afiliacion").path("vigenteDesde").asText()).isEqualTo("2026-01-01");
        assertThat(cuerpo(r).has("historial")).isFalse();
    }
}
