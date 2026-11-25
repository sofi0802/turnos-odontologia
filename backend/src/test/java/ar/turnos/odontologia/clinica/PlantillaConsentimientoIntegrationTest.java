package ar.turnos.odontologia.clinica;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.shared.security.Navegador;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Plantillas de consentimiento de punta a punta (C-27, tarea 5.2): PostgreSQL real, sesión y CSRF de verdad. Cada
 * escenario de los requisitos de plantillas de {@code specs/consentimientos-informados/spec.md} tiene su test con el
 * mismo nombre. Textos ficticios. El reloj está en el 2026-12-01 a las 08:00 de Buenos Aires.
 */
class PlantillaConsentimientoIntegrationTest extends IntegracionClinicaBase {

    static final String PLANTILLAS = "/api/v1/consentimientos/plantillas";
    static final String TEXTO_800 = ("Texto ficticio de consentimiento informado para pruebas. ").repeat(15).substring(0, 800);

    // ---------------------------------------------------------------- Crear una plantilla de consentimiento

    @Nested
    class CrearUnaPlantillaDeConsentimiento {

        @Test
        @DisplayName("Crear una plantilla")
        void crearUnaPlantilla() throws Exception {
            MvcResult resultado = crear(como("admin"), "Extracción dental", TEXTO_800);

            assertThat(status(resultado)).isEqualTo(201);
            JsonNode plantilla = cuerpo(resultado);
            assertThat(plantilla.path("activa").asBoolean()).isTrue();
            assertThat(plantilla.path("version").path("numero").asInt()).isEqualTo(1);
            assertThat(plantilla.path("version").path("titulo").asText()).isEqualTo("Extracción dental");
            assertThat(plantilla.path("version").path("texto").asText()).isEqualTo(TEXTO_800).hasSize(800);
            assertThat(plantilla.path("version").path("creadaPor").asText()).isEqualTo("admin");
            assertThat(plantilla.path("version").path("creadaEn").asText()).isEqualTo(iso(diciembre(1, 8, 0)));
        }

        @Test
        @DisplayName("Texto demasiado largo")
        void textoDemasiadoLargo() throws Exception {
            MvcResult resultado = crear(como("admin"), "Extracción dental", "x".repeat(20_001));

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("DATOS_INVALIDOS");
            assertThat(plantillas()).isZero();
        }

        @Test
        @DisplayName("Título vacío")
        void tituloVacio() throws Exception {
            MvcResult resultado = crear(como("admin"), "   ", TEXTO_800);

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("DATOS_INVALIDOS");
            assertThat(plantillas()).isZero();
        }

        @Test
        @DisplayName("Título repetido")
        void tituloRepetido() throws Exception {
            Navegador admin = como("admin");
            crear(admin, "Extracción dental", TEXTO_800);

            MvcResult resultado = crear(admin, "EXTRACCIÓN DENTAL", "Otro texto ficticio");

            assertThat(status(resultado)).isEqualTo(409);
            assertThat(codigo(resultado)).isEqualTo("PLANTILLA_DUPLICADA");
            assertThat(plantillas()).isEqualTo(1);
        }

        @Test
        @DisplayName("El odontólogo intenta crear una plantilla")
        void elOdontologoIntentaCrearUnaPlantilla() throws Exception {
            MvcResult resultado = crear(como("ana.ejemplo"), "Extracción dental", TEXTO_800);

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(plantillas()).isZero();
        }
    }

    // ---------------------------------------------------------------- Editar una plantilla crea una versión nueva

    @Nested
    class EditarUnaPlantillaCreaUnaVersionNueva {

        @Test
        @DisplayName("Editar una plantilla")
        void editarUnaPlantilla() throws Exception {
            Navegador admin = como("admin");
            long plantilla = creada(admin, "Extracción dental", "Texto ficticio de la versión 1.");
            reloj.ahoraEs(diciembre(1, 9, 0));

            MvcResult resultado = editar(admin, plantilla, 1, "Extracción dental", "Texto ficticio de la versión 2.");

            assertThat(status(resultado)).isEqualTo(201);
            assertThat(cuerpo(resultado).path("version").path("numero").asInt()).isEqualTo(2);
            assertThat(cuerpo(resultado).path("version").path("texto").asText()).isEqualTo("Texto ficticio de la versión 2.");
            assertThat(cuerpo(resultado).path("version").path("creadaEn").asText()).isEqualTo(iso(diciembre(1, 9, 0)));
            JsonNode version1 = cuerpo(admin.get(PLANTILLAS + "/" + plantilla + "/versiones/1").andReturn());
            assertThat(version1.path("texto").asText()).isEqualTo("Texto ficticio de la versión 1.");
            assertThat(version1.path("creadaEn").asText()).isEqualTo(iso(diciembre(1, 8, 0)));
        }

        @Test
        @DisplayName("Edición sobre una versión vieja")
        void edicionSobreUnaVersionVieja() throws Exception {
            Navegador admin = como("admin");
            long plantilla = creada(admin, "Extracción dental", "Versión 1");
            editar(admin, plantilla, 1, "Extracción dental", "Versión 2");

            MvcResult resultado = editar(admin, plantilla, 1, "Extracción dental", "Versión 3");

            assertThat(status(resultado)).isEqualTo(409);
            assertThat(codigo(resultado)).isEqualTo("VERSION_DESACTUALIZADA");
            assertThat(versiones(plantilla)).isEqualTo(2);
        }

        @Test
        @DisplayName("Edición sin cambios")
        void edicionSinCambios() throws Exception {
            Navegador admin = como("admin");
            long plantilla = creada(admin, "Extracción dental", "Texto ficticio.");

            MvcResult resultado = editar(admin, plantilla, 1, "Extracción dental", "Texto ficticio.");

            assertThat(status(resultado)).isEqualTo(200);
            assertThat(cuerpo(resultado).path("version").path("numero").asInt()).isEqualTo(1);
            assertThat(versiones(plantilla)).isEqualTo(1);
        }

        @Test
        @DisplayName("El odontólogo intenta editar una plantilla")
        void elOdontologoIntentaEditar() throws Exception {
            long plantilla = creada(como("admin"), "Extracción dental", "Texto ficticio.");

            MvcResult resultado = editar(como("ana.ejemplo"), plantilla, 1, "Extracción dental", "Otro");

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(versiones(plantilla)).isEqualTo(1);
        }
    }

    // ---------------------------------------------------------------- Activar y desactivar una plantilla

    @Nested
    class ActivarYDesactivarUnaPlantilla {

        @Test
        @DisplayName("Desactivar una plantilla")
        void desactivarUnaPlantilla() throws Exception {
            Navegador admin = como("admin");
            long plantilla = creada(admin, "Extracción dental", "Texto ficticio.");

            MvcResult resultado = estado(admin, plantilla, false);

            assertThat(status(resultado)).isEqualTo(200);
            assertThat(cuerpo(resultado).path("activa").asBoolean()).isFalse();
            assertThat(titulos(cuerpo(como("ana.ejemplo").get(PLANTILLAS).andReturn()))).isEmpty();
        }

        @Test
        @DisplayName("Reactivar una plantilla")
        void reactivarUnaPlantilla() throws Exception {
            Navegador admin = como("admin");
            long plantilla = creada(admin, "Extracción dental", "Texto ficticio.");
            estado(admin, plantilla, false);

            MvcResult resultado = estado(admin, plantilla, true);

            assertThat(status(resultado)).isEqualTo(200);
            assertThat(cuerpo(resultado).path("activa").asBoolean()).isTrue();
            assertThat(titulos(cuerpo(como("ana.ejemplo").get(PLANTILLAS).andReturn())))
                    .containsExactly("Extracción dental");
        }

        @Test
        @DisplayName("Los firmados no cambian al desactivar")
        void losFirmadosNoCambianAlDesactivar() throws Exception {
            Navegador admin = como("admin");
            long plantilla = creada(admin, "Extracción dental", "Texto ficticio firmado.");
            Navegador ana = como("ana.ejemplo");
            JsonNode firmado = cuerpo(ana.post("/api/v1/pacientes/" + pacienteUno + "/consentimientos",
                    json.writeValueAsString(firma(plantilla, 1))).andReturn());
            long consentimiento = firmado.path("id").asLong();

            estado(admin, plantilla, false);

            JsonNode visto = cuerpo(ana.get("/api/v1/pacientes/" + pacienteUno + "/consentimientos/" + consentimiento)
                    .andReturn());
            JsonNode verificacion = cuerpo(ana.get("/api/v1/pacientes/" + pacienteUno + "/consentimientos/"
                    + consentimiento + "/verificacion").andReturn());
            assertThat(visto.path("texto").asText()).isEqualTo("Texto ficticio firmado.");
            assertThat(visto.path("hash").path("valor").asText()).isEqualTo(firmado.path("hash").path("valor").asText());
            assertThat(verificacion.path("integro").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("No hay forma de borrar una plantilla")
        void noHayFormaDeBorrarUnaPlantilla() throws Exception {
            Navegador admin = como("admin");
            long plantilla = creada(admin, "Extracción dental", "Texto ficticio.");

            MvcResult borrar = admin.enviar(MockMvcRequestBuilders.delete(PLANTILLAS + "/" + plantilla)
                    .header(Navegador.ENCABEZADO_CSRF, admin.tokenCsrf())).andReturn();

            assertThat(status(borrar)).isGreaterThanOrEqualTo(400);
            assertThat(status(admin.get(PLANTILLAS + "/" + plantilla).andReturn())).isEqualTo(200);
            assertThat(versiones(plantilla)).isEqualTo(1);
        }
    }

    // ---------------------------------------------------------------- Consultar las plantillas

    @Nested
    class ConsultarLasPlantillas {

        @Test
        @DisplayName("El odontólogo lista las plantillas activas")
        void elOdontologoListaLasPlantillasActivas() throws Exception {
            Navegador admin = como("admin");
            long extraccion = creada(admin, "Extracción dental", "Versión 1");
            editar(admin, extraccion, 1, "Extracción dental", "Versión 2");
            long blanqueamiento = creada(admin, "Blanqueamiento", "Texto ficticio.");
            estado(admin, blanqueamiento, false);
            Navegador ana = como("ana.ejemplo");

            MvcResult resultado = ana.get(PLANTILLAS + "?incluirInactivas=true").andReturn();

            assertThat(status(resultado)).isEqualTo(200);
            JsonNode lista = cuerpo(resultado);
            assertThat(titulos(lista)).containsExactly("Extracción dental");
            assertThat(lista.get(0).path("versionActual").asInt()).isEqualTo(2);
            assertThat(lista.get(0).has("texto")).isFalse();
            JsonNode vista = cuerpo(ana.get(PLANTILLAS + "/" + extraccion).andReturn());
            assertThat(vista.path("version").path("texto").asText()).isEqualTo("Versión 2");
            assertThat(status(ana.get(PLANTILLAS + "/" + blanqueamiento).andReturn())).isEqualTo(404);
            assertThat(titulos(cuerpo(admin.get(PLANTILLAS + "?incluirInactivas=true").andReturn())))
                    .containsExactly("Blanqueamiento", "Extracción dental");
        }

        @Test
        @DisplayName("El administrador ve las versiones")
        void elAdministradorVeLasVersiones() throws Exception {
            Navegador admin = como("admin");
            long plantilla = creada(admin, "Extracción dental", "Versión 1");
            reloj.ahoraEs(diciembre(2, 10, 0));
            editar(admin, plantilla, 1, "Extracción dental", "Versión 2");

            MvcResult resultado = admin.get(PLANTILLAS + "/" + plantilla + "/versiones").andReturn();

            assertThat(status(resultado)).isEqualTo(200);
            JsonNode lista = cuerpo(resultado);
            assertThat(lista).hasSize(2);
            assertThat(lista.get(0).path("numero").asInt()).isEqualTo(2);
            assertThat(lista.get(0).path("texto").asText()).isEqualTo("Versión 2");
            assertThat(lista.get(0).path("creadaPor").asText()).isEqualTo("admin");
            assertThat(lista.get(0).path("creadaEn").asText()).isEqualTo(iso(diciembre(2, 10, 0)));
            assertThat(lista.get(1).path("numero").asInt()).isEqualTo(1);
            assertThat(lista.get(1).path("texto").asText()).isEqualTo("Versión 1");
            assertThat(lista.get(1).path("creadaEn").asText()).isEqualTo(iso(diciembre(1, 8, 0)));
            // Ver todas las versiones es del ADMIN; el odontólogo ve una versión concreta.
            Navegador ana = como("ana.ejemplo");
            assertThat(status(ana.get(PLANTILLAS + "/" + plantilla + "/versiones").andReturn())).isEqualTo(403);
            assertThat(cuerpo(ana.get(PLANTILLAS + "/" + plantilla + "/versiones/1").andReturn()).path("texto").asText())
                    .isEqualTo("Versión 1");
        }

        @Test
        @DisplayName("La recepción lista las plantillas")
        void laRecepcionListaLasPlantillas() throws Exception {
            creada(como("admin"), "Extracción dental", "Texto ficticio.");

            MvcResult resultado = como("recepcion").get(PLANTILLAS).andReturn();

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(texto(resultado)).doesNotContain("Extracción");
        }

        @Test
        @DisplayName("Plantilla inexistente")
        void plantillaInexistente() throws Exception {
            MvcResult resultado = como("admin").get(PLANTILLAS + "/999999").andReturn();

            assertThat(status(resultado)).isEqualTo(404);
            assertThat(codigo(resultado)).isEqualTo("RECURSO_NO_ENCONTRADO");
        }
    }

    // ---------------------------------------------------------------- Ayudas

    MvcResult crear(Navegador navegador, String titulo, String texto) throws Exception {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("titulo", titulo);
        pedido.put("texto", texto);
        return navegador.post(PLANTILLAS, json.writeValueAsString(pedido)).andReturn();
    }

    long creada(Navegador navegador, String titulo, String texto) throws Exception {
        MvcResult resultado = crear(navegador, titulo, texto);
        assertThat(status(resultado)).as(texto(resultado)).isEqualTo(201);
        return cuerpo(resultado).path("id").asLong();
    }

    MvcResult editar(Navegador navegador, long plantilla, int versionBase, String titulo, String texto)
            throws Exception {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("versionBase", versionBase);
        pedido.put("titulo", titulo);
        pedido.put("texto", texto);
        return navegador.post(PLANTILLAS + "/" + plantilla + "/versiones", json.writeValueAsString(pedido)).andReturn();
    }

    MvcResult estado(Navegador navegador, long plantilla, boolean activa) throws Exception {
        return put(navegador, PLANTILLAS + "/" + plantilla + "/estado", Map.of("activa", activa));
    }

    static Map<String, Object> firma(long plantilla, int version) {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("plantillaId", plantilla);
        pedido.put("versionNumero", version);
        pedido.put("firmanteNombre", "Paciente Uno");
        pedido.put("firmanteDni", DNI_PACIENTE_UNO);
        pedido.put("aceptado", true);
        return pedido;
    }

    private int plantillas() {
        return jdbc.queryForObject("SELECT count(*) FROM plantilla_consentimiento", Integer.class);
    }

    private int versiones(long plantilla) {
        return jdbc.queryForObject("SELECT count(*) FROM plantilla_consentimiento_version WHERE plantilla_id = ?",
                Integer.class, plantilla);
    }

    private static List<String> titulos(JsonNode lista) {
        List<String> titulos = new ArrayList<>();
        lista.forEach(p -> titulos.add(p.path("titulo").asText()));
        return titulos;
    }
}
