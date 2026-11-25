package ar.turnos.odontologia.clinica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import ar.turnos.odontologia.shared.security.Navegador;
import ar.turnos.odontologia.shared.security.UsuarioAutenticado;
import ar.turnos.odontologia.usuarios.domain.Rol;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.MethodMode;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Consentimientos firmados de punta a punta (C-27, tareas 5.3 y 5.4): PostgreSQL real, sesión y CSRF de verdad. Cada
 * escenario de los requisitos de firma, evidencia, copia del texto, bloqueo, verificación, listado, revocación, acceso y
 * privacidad de {@code specs/consentimientos-informados/spec.md} tiene su test con el mismo nombre. Es una firma
 * electrónica, no una firma digital. Datos ficticios: "Paciente Uno", DNI 99000001, IP 10.0.0.15 y "Navegador de
 * prueba 1.0". El reloj arranca el 2026-12-01 a las 08:00 de Buenos Aires.
 */
class ConsentimientoIntegrationTest extends IntegracionClinicaBase {

    private static final String PLANTILLAS = PlantillaConsentimientoIntegrationTest.PLANTILLAS;
    private static final String TEXTO_V1 = "Texto ficticio de la versión 1.\nSegunda línea del consentimiento.";
    private static final String NAVEGADOR = "Navegador de prueba 1.0";

    private long extraccion;

    /** {@code user(...)} deja el principal en el contexto de test, y MockMvc lo aplicaría a los pedidos siguientes. */
    @AfterEach
    void limpiarPrincipalInyectado() {
        TestSecurityContextHolder.clearContext();
    }

    @BeforeEach
    void plantillaDeExtraccion() throws Exception {
        extraccion = plantilla(como("admin"), "Extracción dental", TEXTO_V1);
    }

    // ---------------------------------------------------------------- Firmar un consentimiento en el consultorio

    @Nested
    class FirmarUnConsentimientoEnElConsultorio {

        @Test
        @DisplayName("Firmar un consentimiento")
        void firmarUnConsentimiento() throws Exception {
            MvcResult resultado = firmar(como("ana.ejemplo"), pacienteUno, firma(extraccion, 1, "Paciente Uno",
                    DNI_PACIENTE_UNO, true));

            assertThat(status(resultado)).as(texto(resultado)).isEqualTo(201);
            JsonNode c = cuerpo(resultado);
            assertThat(c.path("titulo").asText()).isEqualTo("Extracción dental");
            assertThat(c.path("texto").asText()).isEqualTo(TEXTO_V1);
            assertThat(c.path("versionNumero").asInt()).isEqualTo(1);
            assertThat(c.path("plantillaId").asLong()).isEqualTo(extraccion);
            assertThat(c.path("firmante").path("nombre").asText()).isEqualTo("Paciente Uno");
            assertThat(c.path("firmante").path("dni").asText()).isEqualTo(DNI_PACIENTE_UNO);
            assertThat(c.path("profesionalId").asLong()).isEqualTo(draA);
            assertThat(c.path("profesional").asText()).isEqualTo("Ana Ejemplo");
            assertThat(c.path("firmadoEn").asText()).isEqualTo(iso(diciembre(1, 8, 0)));
            assertThat(c.path("tipoFirma").asText()).isEqualTo("ELECTRONICA");
            assertThat(c.path("hash").path("algoritmo").asText()).isEqualTo("SHA-256");
            assertThat(c.path("hash").path("formato").asInt()).isEqualTo(1);
            assertThat(c.path("hash").path("valor").asText()).matches("[0-9a-f]{64}");
            assertThat(c.path("estado").asText()).isEqualTo("VIGENTE");
            assertThat(c.path("revocacion").isNull()).isTrue();
            assertThat(texto(resultado).toLowerCase()).doesNotContain("digital");
            JsonNode lista = cuerpo(como("ana.ejemplo").get(url(pacienteUno)).andReturn());
            assertThat(lista).hasSize(1);
            assertThat(lista.get(0).path("estado").asText()).isEqualTo("VIGENTE");
        }

        @Test
        @DisplayName("Sin aceptación")
        void sinAceptacion() throws Exception {
            MvcResult sinAceptar = firmar(como("ana.ejemplo"), pacienteUno,
                    firma(extraccion, 1, "Paciente Uno", DNI_PACIENTE_UNO, false));
            MvcResult sinCampo = firmar(como("ana.ejemplo"), pacienteUno,
                    firma(extraccion, 1, "Paciente Uno", DNI_PACIENTE_UNO, null));

            assertThat(status(sinAceptar)).isEqualTo(422);
            assertThat(codigo(sinAceptar)).isEqualTo("DATOS_INVALIDOS");
            assertThat(status(sinCampo)).isEqualTo(422);
            assertThat(consentimientos(pacienteUno)).isZero();
        }

        @Test
        @DisplayName("Nombre del firmante vacío")
        void nombreDelFirmanteVacio() throws Exception {
            MvcResult resultado = firmar(como("ana.ejemplo"), pacienteUno,
                    firma(extraccion, 1, "   ", DNI_PACIENTE_UNO, true));

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("DATOS_INVALIDOS");
            assertThat(consentimientos(pacienteUno)).isZero();
        }

        @Test
        @DisplayName("DNI distinto del paciente")
        void dniDistintoDelPaciente() throws Exception {
            MvcResult resultado = firmar(como("ana.ejemplo"), pacienteUno,
                    firma(extraccion, 1, "Paciente Uno", "99000002", true));

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("FIRMANTE_NO_COINCIDE");
            assertThat(consentimientos(pacienteUno)).isZero();
        }

        @Test
        @DisplayName("DNI con puntos")
        void dniConPuntos() throws Exception {
            MvcResult resultado = firmar(como("ana.ejemplo"), pacienteUno,
                    firma(extraccion, 1, "Paciente Uno", "99.000.001", true));

            assertThat(status(resultado)).isEqualTo(201);
            assertThat(cuerpo(resultado).path("firmante").path("dni").asText()).isEqualTo("99000001");
        }

        @Test
        @DisplayName("Versión desactualizada")
        void versionDesactualizada() throws Exception {
            editar(como("admin"), extraccion, 1, "Texto ficticio de la versión 2.");

            MvcResult resultado = firmar(como("ana.ejemplo"), pacienteUno,
                    firma(extraccion, 1, "Paciente Uno", DNI_PACIENTE_UNO, true));

            assertThat(status(resultado)).isEqualTo(409);
            assertThat(codigo(resultado)).isEqualTo("VERSION_DESACTUALIZADA");
            assertThat(consentimientos(pacienteUno)).isZero();
        }

        @Test
        @DisplayName("Plantilla inactiva")
        void plantillaInactiva() throws Exception {
            Navegador admin = como("admin");
            long blanqueamiento = plantilla(admin, "Blanqueamiento", "Texto ficticio del blanqueamiento.");
            put(admin, PLANTILLAS + "/" + blanqueamiento + "/estado", Map.of("activa", false));

            MvcResult resultado = firmar(como("ana.ejemplo"), pacienteUno,
                    firma(blanqueamiento, 1, "Paciente Uno", DNI_PACIENTE_UNO, true));

            assertThat(status(resultado)).isEqualTo(409);
            assertThat(codigo(resultado)).isEqualTo("PLANTILLA_INACTIVA");
            assertThat(consentimientos(pacienteUno)).isZero();
        }

        @Test
        @DisplayName("Paciente inexistente")
        void pacienteInexistente() throws Exception {
            MvcResult resultado = firmar(como("ana.ejemplo"), 999_999L,
                    firma(extraccion, 1, "Paciente Uno", DNI_PACIENTE_UNO, true));

            assertThat(status(resultado)).isEqualTo(404);
            assertThat(codigo(resultado)).isEqualTo("RECURSO_NO_ENCONTRADO");
        }

        @Test
        @DisplayName("Odontólogo sin profesional vinculado")
        // La sesión de MockMvc puede quedar con este principal: se descarta el contexto para los tests siguientes.
        @DirtiesContext(methodMode = MethodMode.AFTER_METHOD)
        void odontologoSinProfesionalVinculado() throws Exception {
            // La base no admite un ODONTOLOGO sin profesional (CHECK de V3): el principal apunta a la cuenta real de
            // ana.ejemplo, pero sin su profesional, como en ImagenClinicaIntegrationTest.
            long usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'ana.ejemplo'", Long.class);
            String hash = jdbc.queryForObject("SELECT password_hash FROM usuario WHERE id = ?", String.class, usuarioId);
            UsuarioAutenticado suelto = new UsuarioAutenticado(usuarioId, "ana.ejemplo", hash, Rol.ODONTOLOGO,
                    null, null, true);

            MvcResult resultado = mvc.perform(MockMvcRequestBuilders.post(url(pacienteUno))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(firma(extraccion, 1, "Paciente Uno", DNI_PACIENTE_UNO, true)))
                    .with(user(suelto)).with(csrf())).andReturn();

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(consentimientos(pacienteUno)).isZero();
        }

        @Test
        @DisplayName("El administrador intenta registrar una firma")
        void elAdministradorIntentaRegistrarUnaFirma() throws Exception {
            MvcResult resultado = firmar(como("admin"), pacienteUno,
                    firma(extraccion, 1, "Paciente Uno", DNI_PACIENTE_UNO, true));

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(consentimientos(pacienteUno)).isZero();
        }
    }

    // ---------------------------------------------------------------- Evidencia de la firma

    @Nested
    class EvidenciaDeLaFirma {

        @Test
        @DisplayName("Se guardan la IP y el dispositivo")
        void seGuardanLaIpYElDispositivo() throws Exception {
            MvcResult resultado = firmar(como("ana.ejemplo"), pacienteUno, firmaValida(), "10.0.0.15", NAVEGADOR);

            assertThat(status(resultado)).isEqualTo(201);
            JsonNode evidencia = cuerpo(resultado).path("evidencia");
            assertThat(evidencia.path("ip").asText()).isEqualTo("10.0.0.15");
            assertThat(evidencia.path("dispositivo").asText()).isEqualTo(NAVEGADOR);
            assertThat(jdbc.queryForObject("SELECT ip_origen || '|' || dispositivo FROM consentimiento_firmado",
                    String.class)).isEqualTo("10.0.0.15|" + NAVEGADOR);
        }

        @Test
        @DisplayName("La IP es la de la conexión, no la de X-Forwarded-For")
        void sinXForwardedFor() throws Exception {
            Navegador ana = como("ana.ejemplo");
            MockHttpServletRequestBuilder pedido = pedidoDeFirma(ana, pacienteUno, firmaValida())
                    .header("X-Forwarded-For", "10.0.0.99")
                    .with(remoteAddr("10.0.0.15"));

            MvcResult resultado = ana.enviar(pedido).andReturn();

            assertThat(cuerpo(resultado).path("evidencia").path("ip").asText()).isEqualTo("10.0.0.15");
        }

        @Test
        @DisplayName("Sin User-Agent")
        void sinUserAgent() throws Exception {
            MvcResult resultado = firmar(como("ana.ejemplo"), pacienteUno, firmaValida(), "10.0.0.15", null);

            assertThat(status(resultado)).isEqualTo(201);
            assertThat(cuerpo(resultado).path("evidencia").path("dispositivo").asText()).isEmpty();
        }

        @Test
        @DisplayName("User-Agent demasiado largo")
        void userAgentDemasiadoLargo() throws Exception {
            String largo = "N".repeat(512) + "Z".repeat(88);

            MvcResult resultado = firmar(como("ana.ejemplo"), pacienteUno, firmaValida(), "10.0.0.15", largo);

            assertThat(status(resultado)).isEqualTo(201);
            assertThat(cuerpo(resultado).path("evidencia").path("dispositivo").asText()).isEqualTo("N".repeat(512));
        }

        @Test
        @DisplayName("La fecha es la del servidor")
        void laFechaEsLaDelServidor() throws Exception {
            reloj.ahoraEs(LocalDateTime.of(2026, 11, 24, 10, 30).atZone(BUENOS_AIRES).toInstant());

            MvcResult resultado = firmar(como("ana.ejemplo"), pacienteUno, firmaValida());

            assertThat(cuerpo(resultado).path("firmadoEn").asText()).isEqualTo("2026-11-24T10:30:00-03:00");
        }
    }

    // ---------------------------------------------------------------- El documento firmado guarda su copia del texto

    @Nested
    class ElDocumentoFirmadoGuardaSuPropiaCopiaDelTexto {

        @Test
        @DisplayName("Editar la plantilla después de firmar")
        void editarLaPlantillaDespuesDeFirmar() throws Exception {
            long consentimiento = firmado(pacienteUno);
            String hash = cuerpo(ver(como("ana.ejemplo"), pacienteUno, consentimiento)).path("hash").path("valor").asText();

            editar(como("admin"), extraccion, 1, "Otro texto ficticio de la versión 2.");

            JsonNode c = cuerpo(ver(como("ana.ejemplo"), pacienteUno, consentimiento));
            assertThat(c.path("titulo").asText()).isEqualTo("Extracción dental");
            assertThat(c.path("texto").asText()).isEqualTo(TEXTO_V1);
            assertThat(c.path("versionNumero").asInt()).isEqualTo(1);
            assertThat(c.path("hash").path("valor").asText()).isEqualTo(hash);
            assertThat(cuerpo(verificar(como("ana.ejemplo"), pacienteUno, consentimiento)).path("integro").asBoolean())
                    .isTrue();
        }
    }

    // ---------------------------------------------------------------- Documento firmado bloqueado

    @Nested
    class DocumentoFirmadoBloqueado {

        @Test
        @DisplayName("No hay forma de editar ni borrar un consentimiento")
        void noHayFormaDeEditarNiBorrarUnConsentimiento() throws Exception {
            long consentimiento = firmado(pacienteUno);
            Navegador ana = como("ana.ejemplo");
            String hash = cuerpo(ver(ana, pacienteUno, consentimiento)).path("hash").path("valor").asText();
            String recurso = url(pacienteUno) + "/" + consentimiento;

            MvcResult borrar = ana.enviar(MockMvcRequestBuilders.delete(recurso)
                    .header(Navegador.ENCABEZADO_CSRF, ana.tokenCsrf())).andReturn();
            MvcResult editar = put(ana, recurso, Map.of("texto", "Otro"));
            MvcResult parche = ana.enviar(MockMvcRequestBuilders.patch(recurso).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"texto\":\"Otro\"}").header(Navegador.ENCABEZADO_CSRF, ana.tokenCsrf())).andReturn();

            assertThat(status(borrar)).isGreaterThanOrEqualTo(400);
            assertThat(status(editar)).isGreaterThanOrEqualTo(400);
            assertThat(status(parche)).isGreaterThanOrEqualTo(400);
            JsonNode c = cuerpo(ver(ana, pacienteUno, consentimiento));
            assertThat(c.path("texto").asText()).isEqualTo(TEXTO_V1);
            assertThat(c.path("hash").path("valor").asText()).isEqualTo(hash);
            assertThat(cuerpo(verificar(ana, pacienteUno, consentimiento)).path("integro").asBoolean()).isTrue();
        }
    }

    // ---------------------------------------------------------------- Verificar el hash de un consentimiento

    @Nested
    class VerificarElHashDeUnConsentimiento {

        @Test
        @DisplayName("Consentimiento íntegro")
        void consentimientoIntegro() throws Exception {
            long consentimiento = firmado(pacienteUno);
            reloj.ahoraEs(diciembre(3, 12, 0));

            MvcResult resultado = verificar(como("admin"), pacienteUno, consentimiento);

            assertThat(status(resultado)).isEqualTo(200);
            JsonNode v = cuerpo(resultado);
            assertThat(v.path("consentimientoId").asLong()).isEqualTo(consentimiento);
            assertThat(v.path("integro").asBoolean()).isTrue();
            assertThat(v.path("algoritmo").asText()).isEqualTo("SHA-256");
            assertThat(v.path("hashCalculado").asText()).isEqualTo(v.path("hashGuardado").asText()).hasSize(64);
            assertThat(v.path("verificadoEn").asText()).isEqualTo(iso(diciembre(3, 12, 0)));
        }

        @Test
        @DisplayName("Consentimiento alterado en la base")
        void consentimientoAlteradoEnLaBase() throws Exception {
            long consentimiento = firmado(pacienteUno);
            String hash = jdbc.queryForObject("SELECT hash FROM consentimiento_firmado WHERE id = ?", String.class,
                    consentimiento);
            jdbc.execute("ALTER TABLE consentimiento_firmado DISABLE TRIGGER consentimiento_firmado_bloqueado");
            try {
                jdbc.update("UPDATE consentimiento_firmado SET texto = texto || '.' WHERE id = ?", consentimiento);
            } finally {
                jdbc.execute("ALTER TABLE consentimiento_firmado ENABLE TRIGGER consentimiento_firmado_bloqueado");
            }

            MvcResult resultado = verificar(como("ana.ejemplo"), pacienteUno, consentimiento);

            assertThat(status(resultado)).isEqualTo(200);
            JsonNode v = cuerpo(resultado);
            assertThat(v.path("integro").asBoolean()).isFalse();
            assertThat(v.path("hashGuardado").asText()).isEqualTo(hash);
            assertThat(v.path("hashCalculado").asText()).isNotEqualTo(hash).matches("[0-9a-f]{64}");
        }
    }

    // ---------------------------------------------------------------- Listar y ver los consentimientos del paciente

    @Nested
    class ListarYVerLosConsentimientosDelPaciente {

        @Test
        @DisplayName("Orden del listado")
        void ordenDelListado() throws Exception {
            reloj.ahoraEs(LocalDateTime.of(2026, 10, 1, 9, 0).atZone(BUENOS_AIRES).toInstant());
            long octubre = firmado(pacienteUno);
            reloj.ahoraEs(LocalDateTime.of(2026, 11, 1, 9, 0).atZone(BUENOS_AIRES).toInstant());
            long noviembre = firmado(pacienteUno);

            MvcResult resultado = como("ana.ejemplo").get(url(pacienteUno)).andReturn();

            assertThat(status(resultado)).isEqualTo(200);
            JsonNode lista = cuerpo(resultado);
            assertThat(ids(lista)).containsExactly(noviembre, octubre);
            assertThat(lista.get(0).path("firmadoEn").asText()).isEqualTo("2026-11-01T09:00:00-03:00");
            assertThat(lista.get(0).path("titulo").asText()).isEqualTo("Extracción dental");
            assertThat(lista.get(0).path("versionNumero").asInt()).isEqualTo(1);
            assertThat(lista.get(0).path("profesional").asText()).isEqualTo("Ana Ejemplo");
            assertThat(lista.get(0).path("estado").asText()).isEqualTo("VIGENTE");
            assertThat(lista.get(0).has("texto")).isFalse();
            assertThat(lista.get(0).has("firmante")).isFalse();
            assertThat(texto(resultado)).doesNotContain("Texto ficticio").doesNotContain(DNI_PACIENTE_UNO);
        }

        @Test
        @DisplayName("Ver un consentimiento completo")
        void verUnConsentimientoCompleto() throws Exception {
            long consentimiento = firmado(pacienteUno);

            MvcResult resultado = ver(como("ana.ejemplo"), pacienteUno, consentimiento);

            assertThat(status(resultado)).isEqualTo(200);
            JsonNode c = cuerpo(resultado);
            assertThat(c.path("texto").asText()).isEqualTo(TEXTO_V1);
            assertThat(c.path("firmante").path("nombre").asText()).isEqualTo("Paciente Uno");
            assertThat(c.path("firmante").path("dni").asText()).isEqualTo(DNI_PACIENTE_UNO);
            assertThat(c.path("firmadoEn").asText()).isEqualTo(iso(diciembre(1, 8, 0)));
            assertThat(c.path("evidencia").path("ip").asText()).isEqualTo("10.0.0.15");
            assertThat(c.path("evidencia").path("dispositivo").asText()).isEqualTo(NAVEGADOR);
            assertThat(c.path("hash").path("algoritmo").asText()).isEqualTo("SHA-256");
            assertThat(c.path("hash").path("valor").asText()).hasSize(64);
            assertThat(c.has("pacienteId")).isFalse();
        }

        @Test
        @DisplayName("Consentimiento de otro paciente")
        void consentimientoDeOtroPaciente() throws Exception {
            long consentimiento = firmado(pacienteUno);

            MvcResult resultado = ver(como("ana.ejemplo"), pacienteDos, consentimiento);

            assertThat(status(resultado)).isEqualTo(404);
            assertThat(codigo(resultado)).isEqualTo("RECURSO_NO_ENCONTRADO");
            assertThat(texto(resultado)).doesNotContain("Texto ficticio");
        }

        @Test
        @DisplayName("Paciente sin consentimientos")
        void pacienteSinConsentimientos() throws Exception {
            firmado(pacienteUno);

            MvcResult resultado = como("ana.ejemplo").get(url(pacienteDos)).andReturn();

            assertThat(status(resultado)).isEqualTo(200);
            assertThat(cuerpo(resultado).isArray()).isTrue();
            assertThat(cuerpo(resultado)).isEmpty();
        }
    }

    // ---------------------------------------------------------------- Revocar un consentimiento con registro

    @Nested
    class RevocarUnConsentimientoConRegistro {

        @Test
        @DisplayName("Revocar un consentimiento")
        void revocarUnConsentimiento() throws Exception {
            long consentimiento = firmado(pacienteUno);
            String hash = cuerpo(ver(como("ana.ejemplo"), pacienteUno, consentimiento)).path("hash").path("valor").asText();
            reloj.ahoraEs(diciembre(2, 11, 15));

            MvcResult resultado = revocar(como("bruno.prueba"), pacienteUno, consentimiento,
                    "El paciente decidió no hacer el tratamiento");

            assertThat(status(resultado)).isEqualTo(200);
            JsonNode c = cuerpo(resultado);
            assertThat(c.path("estado").asText()).isEqualTo("REVOCADO");
            assertThat(c.path("revocacion").path("profesionalId").asLong()).isEqualTo(drB);
            assertThat(c.path("revocacion").path("profesional").asText()).isEqualTo("Bruno Prueba");
            assertThat(c.path("revocacion").path("revocadoEn").asText()).isEqualTo(iso(diciembre(2, 11, 15)));
            assertThat(c.path("revocacion").path("motivo").asText()).isEqualTo("El paciente decidió no hacer el tratamiento");
            assertThat(c.path("profesionalId").asLong()).isEqualTo(draA);
            JsonNode verificacion = cuerpo(verificar(como("ana.ejemplo"), pacienteUno, consentimiento));
            assertThat(verificacion.path("integro").asBoolean()).isTrue();
            assertThat(verificacion.path("hashGuardado").asText()).isEqualTo(hash);
            JsonNode lista = cuerpo(como("ana.ejemplo").get(url(pacienteUno)).andReturn());
            assertThat(lista.get(0).path("estado").asText()).isEqualTo("REVOCADO");
        }

        @Test
        @DisplayName("Revocar sin motivo")
        void revocarSinMotivo() throws Exception {
            long consentimiento = firmado(pacienteUno);

            MvcResult resultado = revocar(como("ana.ejemplo"), pacienteUno, consentimiento, "");

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("DATOS_INVALIDOS");
            assertThat(revocaciones()).isZero();
        }

        @Test
        @DisplayName("Revocar dos veces")
        void revocarDosVeces() throws Exception {
            long consentimiento = firmado(pacienteUno);
            revocar(como("ana.ejemplo"), pacienteUno, consentimiento, "Primera revocación");

            MvcResult resultado = revocar(como("bruno.prueba"), pacienteUno, consentimiento, "Segunda revocación");

            assertThat(status(resultado)).isEqualTo(409);
            assertThat(codigo(resultado)).isEqualTo("CONSENTIMIENTO_YA_REVOCADO");
            assertThat(jdbc.queryForObject("SELECT revocado_por FROM consentimiento_revocacion WHERE consentimiento_id = ?",
                    Long.class, consentimiento)).isEqualTo(draA);
        }

        @Test
        @DisplayName("El administrador intenta revocar")
        void elAdministradorIntentaRevocar() throws Exception {
            long consentimiento = firmado(pacienteUno);

            MvcResult resultado = revocar(como("admin"), pacienteUno, consentimiento, "Motivo ficticio");

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(revocaciones()).isZero();
        }
    }

    // ---------------------------------------------------------------- La recepción y el paciente no acceden

    @Nested
    class LaRecepcionYElPacienteNoAccedenALosConsentimientosFirmados {

        @Test
        @DisplayName("La recepción lista los consentimientos")
        void laRecepcionListaLosConsentimientos() throws Exception {
            long consentimiento = firmado(pacienteUno);
            Navegador recepcion = como("recepcion");

            List<MvcResult> resultados = List.of(
                    recepcion.get(url(pacienteUno)).andReturn(),
                    ver(recepcion, pacienteUno, consentimiento),
                    verificar(recepcion, pacienteUno, consentimiento),
                    firmar(recepcion, pacienteUno, firmaValida()),
                    revocar(recepcion, pacienteUno, consentimiento, "Motivo ficticio"));

            for (MvcResult resultado : resultados) {
                assertThat(status(resultado)).isEqualTo(403);
                assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
                assertThat(texto(resultado)).doesNotContain("Extracción").doesNotContain("Texto ficticio")
                        .doesNotContain(DNI_PACIENTE_UNO).doesNotContain("10.0.0.15").doesNotContain("hash");
            }
            assertThat(consentimientos(pacienteUno)).isEqualTo(1);
            assertThat(revocaciones()).isZero();
        }

        @Test
        @DisplayName("El paciente pide su propio consentimiento")
        void elPacientePideSuPropioConsentimiento() throws Exception {
            long consentimiento = firmado(pacienteUno);
            jdbc.update("INSERT INTO usuario(username, password_hash, rol, paciente_id) VALUES (?,?,'PACIENTE',?)",
                    "paciente.uno@example.com", passwordEncoder.encode(clave), pacienteUno);
            Navegador paciente = como("paciente.uno@example.com");

            MvcResult listado = paciente.get(url(pacienteUno)).andReturn();
            MvcResult uno = ver(paciente, pacienteUno, consentimiento);

            assertThat(status(listado)).isEqualTo(403);
            assertThat(codigo(listado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(status(uno)).isEqualTo(403);
            assertThat(codigo(uno)).isEqualTo("ACCESO_DENEGADO");
            assertThat(texto(uno)).doesNotContain("Texto ficticio");
        }

        @Test
        @DisplayName("Sin sesión")
        void sinSesion() throws Exception {
            firmado(pacienteUno);

            MvcResult resultado = navegador().get(url(pacienteUno)).andReturn();

            assertThat(status(resultado)).isEqualTo(401);
            assertThat(texto(resultado)).doesNotContain("Extracción");
        }

        @Test
        @DisplayName("Firma sin token CSRF")
        void firmaSinTokenCsrf() throws Exception {
            Navegador ana = como("ana.ejemplo");

            MvcResult resultado = ana.postSinEncabezadoCsrf(url(pacienteUno), json.writeValueAsString(firmaValida()))
                    .andReturn();

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(consentimientos(pacienteUno)).isZero();
        }
    }

    // ---------------------------------------------------------------- Datos fuera de los canales externos

    @Nested
    @ExtendWith(OutputCaptureExtension.class)
    class DatosDeLosConsentimientosFueraDeLosCanalesExternos {

        @Test
        @DisplayName("Un error no repite el DNI escrito")
        void unErrorNoRepiteElDniEscrito() throws Exception {
            MvcResult resultado = firmar(como("ana.ejemplo"), pacienteUno,
                    firma(extraccion, 1, "Paciente Uno", "99000002", true));

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("FIRMANTE_NO_COINCIDE");
            assertThat(texto(resultado)).doesNotContain("99000002").doesNotContain(DNI_PACIENTE_UNO)
                    .doesNotContain("Paciente Uno");
        }

        @Test
        @DisplayName("Un error de validación no repite el nombre ni el motivo")
        void unErrorNoRepiteElNombreNiElMotivo() throws Exception {
            String nombre = "Nombre Ficticio Largo" + "x".repeat(200);
            MvcResult firma = firmar(como("ana.ejemplo"), pacienteUno, firma(extraccion, 1, nombre, DNI_PACIENTE_UNO, true));
            long consentimiento = firmado(pacienteUno);
            String motivo = "Motivo Ficticio Largo" + "x".repeat(200);
            MvcResult revocacion = revocar(como("ana.ejemplo"), pacienteUno, consentimiento, motivo);

            assertThat(status(firma)).isEqualTo(422);
            assertThat(texto(firma)).doesNotContain("Nombre Ficticio");
            assertThat(status(revocacion)).isEqualTo(422);
            assertThat(texto(revocacion)).doesNotContain("Motivo Ficticio");
        }

        @Test
        @DisplayName("La firma no escribe datos en el log")
        void laFirmaNoEscribeDatosEnElLog(CapturedOutput salida) throws Exception {
            Navegador ana = como("ana.ejemplo");
            int antes = salida.length();

            MvcResult resultado = firmar(ana, pacienteUno, firmaValida(), "10.0.0.15", NAVEGADOR);
            long consentimiento = cuerpo(resultado).path("id").asLong();
            verificar(ana, pacienteUno, consentimiento);
            revocar(ana, pacienteUno, consentimiento, "Motivo ficticio de la revocación");
            firmar(ana, pacienteUno, firma(extraccion, 1, "Paciente Uno", "99000002", true));

            assertThat(status(resultado)).isEqualTo(201);
            String log = salida.toString().substring(antes);
            assertThat(log).doesNotContain("Paciente Uno").doesNotContain(DNI_PACIENTE_UNO).doesNotContain("99000002")
                    .doesNotContain("Texto ficticio de la versión 1").doesNotContain("Segunda línea")
                    .doesNotContain(NAVEGADOR).doesNotContain("Motivo ficticio");
        }
    }

    // ---------------------------------------------------------------- El hash de la respuesta es reproducible

    @Nested
    class HashDelDocumentoFirmado {

        @Test
        @DisplayName("El hash de la respuesta es el SHA-256 del contenido canónico armado con sus datos")
        void hashReproducible() throws Exception {
            JsonNode c = cuerpo(firmar(como("ana.ejemplo"), pacienteUno, firmaValida(), "10.0.0.15", NAVEGADOR));
            long versionId = jdbc.queryForObject("SELECT plantilla_version_id FROM consentimiento_firmado WHERE id = ?",
                    Long.class, c.path("id").asLong());
            Instant firmadoEn = java.time.OffsetDateTime.parse(c.path("firmadoEn").asText()).toInstant();

            String canonico = "consentimiento-informado/v1\n"
                    + campo("paciente_id", Long.toString(pacienteUno))
                    + campo("plantilla_version_id", Long.toString(versionId))
                    + campo("titulo", c.path("titulo").asText())
                    + campo("texto", c.path("texto").asText())
                    + campo("firmante_nombre", c.path("firmante").path("nombre").asText())
                    + campo("firmante_dni", c.path("firmante").path("dni").asText())
                    + campo("profesional_id", Long.toString(c.path("profesionalId").asLong()))
                    + campo("firmado_en", firmadoEn.toString().replace("Z", ".000000Z"))
                    + campo("ip_origen", c.path("evidencia").path("ip").asText())
                    + campo("dispositivo", c.path("evidencia").path("dispositivo").asText())
                    + campo("tipo_firma", c.path("tipoFirma").asText());
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonico.getBytes(StandardCharsets.UTF_8)));

            assertThat(c.path("hash").path("valor").asText()).isEqualTo(hash);
        }

        private static String campo(String nombre, String valor) {
            return nombre + "=" + valor.getBytes(StandardCharsets.UTF_8).length + ":" + valor + "\n";
        }
    }

    // ---------------------------------------------------------------- Ayudas

    private static String url(long paciente) {
        return "/api/v1/pacientes/" + paciente + "/consentimientos";
    }

    private long plantilla(Navegador admin, String titulo, String texto) throws Exception {
        MvcResult resultado = admin.post(PLANTILLAS, json.writeValueAsString(Map.of("titulo", titulo, "texto", texto)))
                .andReturn();
        assertThat(status(resultado)).as(texto(resultado)).isEqualTo(201);
        return cuerpo(resultado).path("id").asLong();
    }

    private void editar(Navegador admin, long plantilla, int versionBase, String texto) throws Exception {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("versionBase", versionBase);
        pedido.put("titulo", "Extracción dental");
        pedido.put("texto", texto);
        MvcResult resultado = admin.post(PLANTILLAS + "/" + plantilla + "/versiones", json.writeValueAsString(pedido))
                .andReturn();
        assertThat(status(resultado)).as(texto(resultado)).isEqualTo(201);
    }

    private Map<String, Object> firmaValida() {
        return firma(extraccion, 1, "Paciente Uno", DNI_PACIENTE_UNO, true);
    }

    private static Map<String, Object> firma(long plantilla, int version, String nombre, String dni, Boolean aceptado) {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("plantillaId", plantilla);
        pedido.put("versionNumero", version);
        pedido.put("firmanteNombre", nombre);
        pedido.put("firmanteDni", dni);
        pedido.put("aceptado", aceptado);
        return pedido;
    }

    private MockHttpServletRequestBuilder pedidoDeFirma(Navegador navegador, long paciente, Map<String, Object> firma)
            throws Exception {
        return MockMvcRequestBuilders.post(url(paciente))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(firma))
                .header(Navegador.ENCABEZADO_CSRF, navegador.tokenCsrf() == null ? "" : navegador.tokenCsrf());
    }

    private MvcResult firmar(Navegador navegador, long paciente, Map<String, Object> firma) throws Exception {
        return firmar(navegador, paciente, firma, "10.0.0.15", NAVEGADOR);
    }

    private MvcResult firmar(Navegador navegador, long paciente, Map<String, Object> firma, String ip, String userAgent)
            throws Exception {
        MockHttpServletRequestBuilder pedido = pedidoDeFirma(navegador, paciente, firma).with(remoteAddr(ip));
        if (userAgent != null) {
            pedido.header(HttpHeaders.USER_AGENT, userAgent);
        }
        return navegador.enviar(pedido).andReturn();
    }

    /** Firma como {@code ana.ejemplo} desde 10.0.0.15 y devuelve el id del consentimiento. */
    private long firmado(long paciente) throws Exception {
        MvcResult resultado = firmar(como("ana.ejemplo"), paciente, firmaValida());
        assertThat(status(resultado)).as(texto(resultado)).isEqualTo(201);
        return cuerpo(resultado).path("id").asLong();
    }

    private MvcResult ver(Navegador navegador, long paciente, long consentimiento) throws Exception {
        return navegador.get(url(paciente) + "/" + consentimiento).andReturn();
    }

    private MvcResult verificar(Navegador navegador, long paciente, long consentimiento) throws Exception {
        return navegador.get(url(paciente) + "/" + consentimiento + "/verificacion").andReturn();
    }

    private MvcResult revocar(Navegador navegador, long paciente, long consentimiento, String motivo) throws Exception {
        return navegador.post(url(paciente) + "/" + consentimiento + "/revocacion",
                json.writeValueAsString(Map.of("motivo", motivo))).andReturn();
    }

    private int consentimientos(long paciente) {
        return jdbc.queryForObject("SELECT count(*) FROM consentimiento_firmado WHERE paciente_id = ?", Integer.class,
                paciente);
    }

    private int revocaciones() {
        return jdbc.queryForObject("SELECT count(*) FROM consentimiento_revocacion", Integer.class);
    }

    private static List<Long> ids(JsonNode lista) {
        List<Long> ids = new ArrayList<>();
        lista.forEach(c -> ids.add(c.path("id").asLong()));
        return ids;
    }

    /** La IP de origen que ve el servidor ({@code getRemoteAddr()}). */
    private static RequestPostProcessor remoteAddr(String ip) {
        return pedido -> {
            pedido.setRemoteAddr(ip);
            return pedido;
        };
    }
}
