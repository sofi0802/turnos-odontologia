package ar.turnos.odontologia.clinica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import ar.turnos.odontologia.shared.security.Navegador;
import ar.turnos.odontologia.shared.security.UsuarioAutenticado;
import ar.turnos.odontologia.usuarios.domain.Rol;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.MethodMode;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Un test por escenario del spec {@code periodontograma} (C-25, tareas 4.2, 5.2 y 5.3), de punta a punta. */
class PeriodontogramaIntegrationTest extends IntegracionClinicaBase {

    private static final List<String> SITIOS = List.of("MV", "V", "DV", "ML", "L", "DL");

    /** {@code user(...)} deja el principal en el contexto de test, y MockMvc lo aplicaría a los pedidos siguientes. */
    @AfterEach
    void limpiarPrincipalInyectado() {
        TestSecurityContextHolder.clearContext();
    }

    @Nested
    class RegistrarUnaVersion {

        @Test
        @DisplayName("Registrar la primera versión")
        void registrarLaPrimeraVersion() throws Exception {
            MvcResult resultado = registrar(como("ana.ejemplo"), pacienteUno, conFurcacion(presente(16), 0));

            assertThat(status(resultado)).isEqualTo(201);
            JsonNode version = cuerpo(resultado);
            assertThat(version.path("profesionalId").asLong()).isEqualTo(draA);
            assertThat(version.path("profesional").asText()).isEqualTo("Ana Ejemplo");
            assertThat(version.path("registradoEn").asText()).isEqualTo(iso(diciembre(1, 8, 0)));
            assertThat(version.path("piezas")).hasSize(1);
            assertThat(version.path("piezas").get(0).path("pieza").asInt()).isEqualTo(16);
            assertThat(version.path("piezas").get(0).path("sitios")).hasSize(6);
            assertThat(versiones(pacienteUno)).isEqualTo(1);
        }

        @Test
        @DisplayName("Registro sin piezas")
        void registroSinPiezas() throws Exception {
            MvcResult resultado = registrar(como("ana.ejemplo"), pacienteUno);

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("DATOS_INVALIDOS");
            assertThat(versiones(pacienteUno)).isZero();
        }

        @Test
        @DisplayName("Pieza repetida en el mismo registro")
        void piezaRepetidaEnElMismoRegistro() throws Exception {
            MvcResult resultado = registrar(como("ana.ejemplo"), pacienteUno, presente(21), presente(21));

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("DATOS_INVALIDOS");
            assertThat(versiones(pacienteUno)).isZero();
        }

        @Test
        @DisplayName("Paciente inexistente")
        void pacienteInexistente() throws Exception {
            MvcResult resultado = registrar(como("ana.ejemplo"), 999_999L, presente(16));

            assertThat(status(resultado)).isEqualTo(404);
            assertThat(codigo(resultado)).isEqualTo("RECURSO_NO_ENCONTRADO");
        }

        @Test
        @DisplayName("Odontólogo sin profesional vinculado")
        // La sesión de MockMvc puede quedar con este principal: se descarta el contexto para los tests siguientes.
        @DirtiesContext(methodMode = MethodMode.AFTER_METHOD)
        void odontologoSinProfesionalVinculado() throws Exception {
            // La base no admite un ODONTOLOGO sin profesional (CHECK de V3): el principal apunta a la cuenta real de
            // ana.ejemplo, pero sin su profesional, como en OdontogramaIntegrationTest.
            long usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'ana.ejemplo'", Long.class);
            String hash = jdbc.queryForObject("SELECT password_hash FROM usuario WHERE id = ?", String.class, usuarioId);
            UsuarioAutenticado suelto = new UsuarioAutenticado(usuarioId, "ana.ejemplo", hash, Rol.ODONTOLOGO,
                    null, null, true);

            MvcResult resultado = mvc.perform(MockMvcRequestBuilders.post(periodontograma(pacienteUno))
                    .with(user(suelto)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("piezas", List.of(presente(16))))))
                    .andReturn();

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(versiones(pacienteUno)).isZero();
        }
    }

    @Nested
    class VersionesInmutables {

        @Test
        @DisplayName("Una versión nueva no cambia la anterior")
        void unaVersionNuevaNoCambiaLaAnterior() throws Exception {
            long primera = idDe(registrar(como("ana.ejemplo"), pacienteUno,
                    conSitio(presente(11), "V", 3, -3, false)));
            reloj.ahoraEs(diciembre(1, 9, 0));

            registrar(como("bruno.prueba"), pacienteUno, conSitio(presente(11), "V", 5, -3, false));

            assertThat(versiones(pacienteUno)).isEqualTo(2);
            JsonNode anterior = cuerpo(como("ana.ejemplo").get(periodontograma(pacienteUno) + "/" + primera).andReturn());
            assertThat(sitio(anterior.path("piezas").get(0), "V").path("profundidadSondaje").asInt()).isEqualTo(3);
        }
    }

    @Nested
    class SoloPiezasPermanentes {

        @Test
        @DisplayName("Pieza temporal")
        void piezaTemporal() throws Exception {
            MvcResult resultado = registrar(como("ana.ejemplo"), pacienteUno, presente(55));

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("PIEZA_INVALIDA");
            assertThat(versiones(pacienteUno)).isZero();
        }

        @Test
        @DisplayName("Pieza fuera de la notación FDI")
        void piezaFueraDeLaNotacionFdi() throws Exception {
            MvcResult resultado = registrar(como("ana.ejemplo"), pacienteUno, presente(19));

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("PIEZA_INVALIDA");
        }
    }

    @Nested
    class SeisSitiosPorPiezaPresente {

        @Test
        @DisplayName("Falta un sitio")
        void faltaUnSitio() throws Exception {
            Map<String, Object> pieza = presente(36);
            sitios(pieza).removeIf(s -> "DL".equals(s.get("sitio")));

            MvcResult resultado = registrar(como("ana.ejemplo"), pacienteUno, pieza);

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("SITIOS_INCOMPLETOS");
            assertThat(versiones(pacienteUno)).isZero();
        }

        @Test
        @DisplayName("Sitio repetido")
        void sitioRepetido() throws Exception {
            Map<String, Object> pieza = presente(36);
            sitios(pieza).get(0).put("sitio", "V");

            MvcResult resultado = registrar(como("ana.ejemplo"), pacienteUno, pieza);

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("SITIOS_INCOMPLETOS");
        }

        @Test
        @DisplayName("Falta la movilidad")
        void faltaLaMovilidad() throws Exception {
            Map<String, Object> pieza = presente(11);
            pieza.remove("movilidad");

            MvcResult resultado = registrar(como("ana.ejemplo"), pacienteUno, pieza);

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("SITIOS_INCOMPLETOS");
        }
    }

    @Nested
    class RangosDeLosValores {

        @Test
        @DisplayName("Profundidad fuera de rango")
        void profundidadFueraDeRango() throws Exception {
            MvcResult resultado = registrar(como("ana.ejemplo"), pacienteUno,
                    conSitio(presente(11), "MV", 16, 0, false));

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("DATOS_INVALIDOS");
            assertThat(versiones(pacienteUno)).isZero();
        }

        @Test
        @DisplayName("Recesión negativa permitida")
        void recesionNegativaPermitida() throws Exception {
            MvcResult resultado = registrar(como("ana.ejemplo"), pacienteUno,
                    conSitio(presente(11), "V", 2, -2, false));

            assertThat(status(resultado)).isEqualTo(201);
            assertThat(sitio(cuerpo(resultado).path("piezas").get(0), "V").path("recesion").asInt()).isEqualTo(-2);
        }

        @Test
        @DisplayName("Recesión fuera de rango")
        void recesionFueraDeRango() throws Exception {
            MvcResult resultado = registrar(como("ana.ejemplo"), pacienteUno,
                    conSitio(presente(11), "V", 2, -6, false));

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("DATOS_INVALIDOS");
        }

        @Test
        @DisplayName("Movilidad fuera de rango")
        void movilidadFueraDeRango() throws Exception {
            Map<String, Object> pieza = presente(11);
            pieza.put("movilidad", 4);

            MvcResult resultado = registrar(como("ana.ejemplo"), pacienteUno, pieza);

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("DATOS_INVALIDOS");
        }
    }

    @Nested
    class FurcacionSoloEnPiezasMultirradiculares {

        @Test
        @DisplayName("Furcación en un molar")
        void furcacionEnUnMolar() throws Exception {
            MvcResult resultado = registrar(como("ana.ejemplo"), pacienteUno, conFurcacion(presente(46), 2));

            assertThat(status(resultado)).isEqualTo(201);
            assertThat(cuerpo(resultado).path("piezas").get(0).path("furcacion").asInt()).isEqualTo(2);
        }

        @Test
        @DisplayName("Furcación en el primer premolar superior")
        void furcacionEnElPrimerPremolarSuperior() throws Exception {
            MvcResult resultado = registrar(como("ana.ejemplo"), pacienteUno, conFurcacion(presente(14), 1));

            assertThat(status(resultado)).isEqualTo(201);
        }

        @Test
        @DisplayName("Furcación en un incisivo")
        void furcacionEnUnIncisivo() throws Exception {
            MvcResult resultado = registrar(como("ana.ejemplo"), pacienteUno, conFurcacion(presente(11), 1));

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("FURCACION_NO_APLICA");
            assertThat(versiones(pacienteUno)).isZero();
        }

        @Test
        @DisplayName("Molar sin furcación")
        void molarSinFurcacion() throws Exception {
            MvcResult resultado = registrar(como("ana.ejemplo"), pacienteUno, presente(36));

            assertThat(status(resultado)).isEqualTo(201);
            assertThat(cuerpo(resultado).path("piezas").get(0).path("furcacion").isNull()).isTrue();
        }
    }

    @Nested
    class PiezaAusente {

        @Test
        @DisplayName("Registrar una pieza ausente")
        void registrarUnaPiezaAusente() throws Exception {
            MvcResult resultado = registrar(como("ana.ejemplo"), pacienteUno, ausente(18));

            assertThat(status(resultado)).isEqualTo(201);
            JsonNode pieza = cuerpo(resultado).path("piezas").get(0);
            assertThat(pieza.path("ausente").asBoolean()).isTrue();
            assertThat(pieza.path("estado").isNull()).isTrue();
            assertThat(pieza.path("sitios")).isEmpty();
        }

        @Test
        @DisplayName("Pieza ausente con datos")
        void piezaAusenteConDatos() throws Exception {
            Map<String, Object> pieza = ausente(18);
            pieza.put("movilidad", 1);

            MvcResult resultado = registrar(como("ana.ejemplo"), pacienteUno, pieza);

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("DATOS_INVALIDOS");
        }
    }

    @Nested
    class EstadoPeriodontalDeCadaPieza {

        @Test
        @DisplayName("La API devuelve el estado calculado de cada pieza")
        void estadoEnLaRespuesta() throws Exception {
            MvcResult resultado = registrar(como("ana.ejemplo"), pacienteUno,
                    presente(11), conSitio(presente(21), "DL", 7, 1, false), conSitio(presente(31), "L", 4, 0, false));

            assertThat(status(resultado)).isEqualTo(201);
            assertThat(cuerpo(resultado).path("piezas")).extracting(p -> p.path("pieza").asInt() + " "
                    + p.path("estado").asText()).containsExactly("11 SANO", "21 SEVERO", "31 MODERADO");
        }
    }

    @Nested
    class ConsultarLasVersiones {

        @Test
        @DisplayName("Listar las versiones")
        void listarLasVersiones() throws Exception {
            registrar(como("ana.ejemplo"), pacienteUno, presente(11));
            reloj.ahoraEs(diciembre(1, 9, 0));
            registrar(como("bruno.prueba"), pacienteUno, presente(11));

            MvcResult resultado = como("ana.ejemplo").get(periodontograma(pacienteUno)).andReturn();

            assertThat(status(resultado)).isEqualTo(200);
            assertThat(cuerpo(resultado)).extracting(v -> v.path("profesional").asText())
                    .containsExactly("Bruno Prueba", "Ana Ejemplo");
            assertThat(cuerpo(resultado).get(0).has("piezas")).isFalse();
        }

        @Test
        @DisplayName("Paciente sin periodontograma")
        void pacienteSinPeriodontograma() throws Exception {
            MvcResult resultado = como("ana.ejemplo").get(periodontograma(pacienteUno)).andReturn();

            assertThat(status(resultado)).isEqualTo(200);
            assertThat(cuerpo(resultado)).isEmpty();
        }

        @Test
        @DisplayName("Consultar una versión")
        void consultarUnaVersion() throws Exception {
            long version = idDe(registrar(como("ana.ejemplo"), pacienteUno,
                    conFurcacion(presente(46), 2), ausente(18)));

            MvcResult resultado = como("bruno.prueba").get(periodontograma(pacienteUno) + "/" + version).andReturn();

            assertThat(status(resultado)).isEqualTo(200);
            JsonNode piezas = cuerpo(resultado).path("piezas");
            assertThat(piezas).extracting(p -> p.path("pieza").asInt()).containsExactly(18, 46);
            assertThat(piezas.get(0).path("ausente").asBoolean()).isTrue();
            assertThat(piezas.get(1).path("sitios")).extracting(s -> s.path("sitio").asText())
                    .containsExactlyElementsOf(SITIOS);
            assertThat(piezas.get(1).path("furcacion").asInt()).isEqualTo(2);
            assertThat(piezas.get(1).path("estado").asText()).isEqualTo("SANO");
        }

        @Test
        @DisplayName("Versión de otro paciente")
        void versionDeOtroPaciente() throws Exception {
            long deDos = idDe(registrar(como("ana.ejemplo"), pacienteDos, presente(11)));

            MvcResult resultado = como("ana.ejemplo").get(periodontograma(pacienteUno) + "/" + deDos).andReturn();

            assertThat(status(resultado)).isEqualTo(404);
            assertThat(codigo(resultado)).isEqualTo("RECURSO_NO_ENCONTRADO");
        }

        @Test
        @DisplayName("Listar el periodontograma de un paciente inexistente")
        void listarElPeriodontogramaDeUnPacienteInexistente() throws Exception {
            MvcResult resultado = como("ana.ejemplo").get(periodontograma(999_999L)).andReturn();

            assertThat(status(resultado)).isEqualTo(404);
            assertThat(codigo(resultado)).isEqualTo("RECURSO_NO_ENCONTRADO");
        }
    }

    @Nested
    class AccesoDelOdontologoYDelAdministrador {

        @Test
        @DisplayName("El administrador consulta el periodontograma")
        void elAdministradorConsultaElPeriodontograma() throws Exception {
            registrar(como("ana.ejemplo"), pacienteUno, presente(11));

            MvcResult resultado = como("admin").get(periodontograma(pacienteUno)).andReturn();

            assertThat(status(resultado)).isEqualTo(200);
            assertThat(cuerpo(resultado)).hasSize(1);
        }

        @Test
        @DisplayName("El administrador intenta registrar")
        void elAdministradorIntentaRegistrar() throws Exception {
            MvcResult resultado = registrar(como("admin"), pacienteUno, presente(11));

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(versiones(pacienteUno)).isZero();
        }
    }

    @Nested
    class LaRecepcionNoAccede {

        @Test
        @DisplayName("La recepción lista el periodontograma")
        void laRecepcionListaElPeriodontograma() throws Exception {
            registrar(como("ana.ejemplo"), pacienteUno, presente(11));

            MvcResult resultado = como("recepcion").get(periodontograma(pacienteUno)).andReturn();

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(texto(resultado)).doesNotContain("registradoEn", "Ana Ejemplo", "piezas");
        }

        @Test
        @DisplayName("La recepción consulta una versión")
        void laRecepcionConsultaUnaVersion() throws Exception {
            long version = idDe(registrar(como("ana.ejemplo"), pacienteUno, presente(11)));

            MvcResult resultado = como("recepcion").get(periodontograma(pacienteUno) + "/" + version).andReturn();

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(texto(resultado)).doesNotContain("profundidadSondaje", "piezas");
        }

        @Test
        @DisplayName("La recepción intenta registrar")
        void laRecepcionIntentaRegistrar() throws Exception {
            MvcResult resultado = registrar(como("recepcion"), pacienteUno, presente(11));

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(versiones(pacienteUno)).isZero();
        }
    }

    @Nested
    class DatosFueraDeLosCanalesExternos {

        @Test
        @DisplayName("Un error de validación no repite el valor")
        void unErrorDeValidacionNoRepiteElValor() throws Exception {
            MvcResult resultado = registrar(como("ana.ejemplo"), pacienteUno,
                    conSitio(presente(11), "MV", 16, 0, false));

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("DATOS_INVALIDOS");
            assertThat(texto(resultado)).doesNotContain("16");
        }
    }

    // ---- helpers ----

    private static String periodontograma(long paciente) {
        return "/api/v1/pacientes/" + paciente + "/periodontograma";
    }

    @SafeVarargs
    private MvcResult registrar(Navegador navegador, long paciente, Map<String, Object>... piezas) throws Exception {
        return navegador.post(periodontograma(paciente), json.writeValueAsString(Map.of("piezas", List.of(piezas))))
                .andReturn();
    }

    private int versiones(long paciente) {
        return jdbc.queryForObject("SELECT count(*) FROM periodontograma_version WHERE paciente_id = ?",
                Integer.class, paciente);
    }

    private long idDe(MvcResult resultado) throws Exception {
        assertThat(status(resultado)).isEqualTo(201);
        return cuerpo(resultado).path("id").asLong();
    }

    /** Pieza presente con los 6 sitios sanos (profundidad 2, recesión −2) y movilidad 0. */
    private static Map<String, Object> presente(int pieza) {
        List<Map<String, Object>> sitios = new ArrayList<>();
        for (String sitio : SITIOS) {
            sitios.add(medicion(sitio, 2, -2, false));
        }
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("pieza", pieza);
        resultado.put("ausente", false);
        resultado.put("movilidad", 0);
        resultado.put("sitios", sitios);
        return resultado;
    }

    private static Map<String, Object> ausente(int pieza) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("pieza", pieza);
        resultado.put("ausente", true);
        return resultado;
    }

    private static Map<String, Object> conFurcacion(Map<String, Object> pieza, int furcacion) {
        pieza.put("furcacion", furcacion);
        return pieza;
    }

    private static Map<String, Object> conSitio(Map<String, Object> pieza, String sitio, int profundidad,
                                                int recesion, boolean sangrado) {
        sitios(pieza).replaceAll(s -> sitio.equals(s.get("sitio")) ? medicion(sitio, profundidad, recesion, sangrado) : s);
        return pieza;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> sitios(Map<String, Object> pieza) {
        return (List<Map<String, Object>>) pieza.get("sitios");
    }

    private static Map<String, Object> medicion(String sitio, int profundidad, int recesion, boolean sangrado) {
        Map<String, Object> medicion = new LinkedHashMap<>();
        medicion.put("sitio", sitio);
        medicion.put("profundidadSondaje", profundidad);
        medicion.put("recesion", recesion);
        medicion.put("sangrado", sangrado);
        medicion.put("placa", false);
        return medicion;
    }

    private static JsonNode sitio(JsonNode pieza, String sitio) {
        for (JsonNode s : pieza.path("sitios")) {
            if (sitio.equals(s.path("sitio").asText())) {
                return s;
            }
        }
        throw new AssertionError("La pieza no tiene el sitio " + sitio);
    }
}
