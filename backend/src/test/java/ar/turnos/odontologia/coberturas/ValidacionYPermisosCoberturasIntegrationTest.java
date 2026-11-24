package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Validación de los datos del catálogo y permisos sobre el catálogo de coberturas (C-18, spec catalogo-coberturas,
 * tarea 3.4).
 */
@Tag("integration")
class ValidacionYPermisosCoberturasIntegrationTest extends IntegracionCoberturasBase {

    @Nested
    class ValidacionDeLosDatosDelCatalogo {

        @Test
        @DisplayName("Obra social sin nombre")
        void obraSocialSinNombre() throws Exception {
            // WHEN admin da de alta una obra social con nombre en blanco y tipo OBRA_SOCIAL
            MvcResult r = post(como("admin"), OBRAS_SOCIALES, obraSocialSolicitud("   ", null, "OBRA_SOCIAL"));

            // THEN la respuesta es 422 con el código DATOS_INVALIDOS y el campo nombre
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("DATOS_INVALIDOS");
            assertThat(campos(r)).contains("nombre");
            assertThat(cantidad("obra_social")).isZero();
        }

        @Test
        @DisplayName("Tipo inválido")
        void tipoInvalido() throws Exception {
            // WHEN admin da de alta "Obra Social Ejemplo" con tipo MUTUAL
            MvcResult r = post(como("admin"), OBRAS_SOCIALES, obraSocialSolicitud("Obra Social Ejemplo", null, "MUTUAL"));

            // THEN la respuesta es 422 con el código DATOS_INVALIDOS
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("DATOS_INVALIDOS");
            assertThat(campos(r)).contains("tipo");
        }

        @Test
        @DisplayName("Descripción demasiado larga")
        void descripcionDemasiadoLarga() throws Exception {
            // WHEN admin da de alta N-0101 con una descripción de 201 caracteres
            MvcResult r = post(como("admin"), NOMENCLADOR, codigoSolicitud("N-0101", "d".repeat(201), null));

            // THEN la respuesta es 422 con el código DATOS_INVALIDOS y el campo descripcion
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("DATOS_INVALIDOS");
            assertThat(campos(r)).contains("descripcion");
        }

        @Test
        @DisplayName("Los máximos de cada campo se respetan al límite")
        void maximosDeCadaCampo() throws Exception {
            var admin = como("admin");
            // 120, 20, 80, 20 y 200 caracteres: justo en el máximo
            assertThat(status(post(admin, OBRAS_SOCIALES,
                    obraSocialSolicitud("o".repeat(120), "s".repeat(20), "PREPAGA")))).isEqualTo(201);
            assertThat(status(post(admin, OBRAS_SOCIALES, obraSocialSolicitud("o", "s".repeat(21), "PREPAGA"))))
                    .isEqualTo(422);
            long obra = obraSocial("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL");
            assertThat(status(post(admin, OBRAS_SOCIALES + "/" + obra + "/planes", Map.of("nombre", "p".repeat(80)))))
                    .isEqualTo(201);
            assertThat(status(post(admin, OBRAS_SOCIALES + "/" + obra + "/planes", Map.of("nombre", "p".repeat(81)))))
                    .isEqualTo(422);
            assertThat(status(post(admin, NOMENCLADOR, codigoSolicitud("c".repeat(20), "d".repeat(200), null))))
                    .isEqualTo(201);
            assertThat(status(post(admin, NOMENCLADOR, codigoSolicitud("c".repeat(21), "d", null)))).isEqualTo(422);
        }

        @Test
        @DisplayName("Un cuerpo ilegible o sin campos obligatorios es 422")
        void cuerpoIlegible() throws Exception {
            var admin = como("admin");
            MvcResult sinNada = post(admin, NOMENCLADOR, Map.of());
            assertThat(status(sinNada)).isEqualTo(422);
            assertThat(codigoDeError(sinNada)).isEqualTo("DATOS_INVALIDOS");
            assertThat(campos(sinNada)).contains("codigo", "descripcion");
            MvcResult sinPlanNombre = post(admin, OBRAS_SOCIALES + "/1/planes", Map.of());
            assertThat(status(sinPlanNombre)).isEqualTo(422);
            assertThat(campos(sinPlanNombre)).contains("nombre");
        }

        private java.util.List<String> campos(MvcResult r) throws Exception {
            JsonNode errores = cuerpo(r).path("errores");
            java.util.List<String> campos = new java.util.ArrayList<>();
            errores.forEach(e -> campos.add(e.path("campo").asText()));
            return campos;
        }
    }

    @Nested
    class PermisosSobreElCatalogoDeCoberturas {

        @Test
        @DisplayName("La recepción no modifica el catálogo")
        void laRecepcionNoModificaElCatalogo() throws Exception {
            // WHEN recepcion da de alta "Prepaga Prueba" con tipo PREPAGA
            MvcResult r = post(como("recepcion"), OBRAS_SOCIALES, obraSocialSolicitud("Prepaga Prueba", null, "PREPAGA"));

            // THEN la respuesta es 403 con el código ACCESO_DENEGADO
            assertThat(status(r)).isEqualTo(403);
            assertThat(codigoDeError(r)).isEqualTo("ACCESO_DENEGADO");
            // AND no existe "Prepaga Prueba"
            assertThat(cantidad("obra_social")).isZero();
        }

        @Test
        @DisplayName("El odontólogo no desactiva un plan")
        void elOdontologoNoDesactivaUnPlan() throws Exception {
            // GIVEN "Plan Base" activo
            long ose = obraSocial("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL");
            long base = plan(ose, "Plan Base");

            // WHEN ana.ejemplo desactiva "Plan Base"
            MvcResult r = post(como("ana.ejemplo"), OBRAS_SOCIALES + "/" + ose + "/planes/" + base + "/desactivar", null);

            // THEN la respuesta es 403 con el código ACCESO_DENEGADO
            assertThat(status(r)).isEqualTo(403);
            assertThat(codigoDeError(r)).isEqualTo("ACCESO_DENEGADO");
            // AND "Plan Base" sigue activo
            assertThat(jdbc.queryForObject("SELECT activo FROM plan WHERE id = ?", Boolean.class, base)).isTrue();
        }

        @Test
        @DisplayName("El odontólogo consulta el catálogo")
        void elOdontologoConsultaElCatalogo() throws Exception {
            // WHEN ana.ejemplo lista las obras sociales
            MvcResult r = get(como("ana.ejemplo"), OBRAS_SOCIALES);

            // THEN la respuesta es 200
            assertThat(status(r)).isEqualTo(200);
        }

        @Test
        @DisplayName("El paciente no consulta el catálogo")
        void elPacienteNoConsultaElCatalogo() throws Exception {
            // WHEN paciente.uno lista las obras sociales
            MvcResult r = get(como("paciente.uno"), OBRAS_SOCIALES);

            // THEN la respuesta es 403 con el código ACCESO_DENEGADO
            assertThat(status(r)).isEqualTo(403);
            assertThat(codigoDeError(r)).isEqualTo("ACCESO_DENEGADO");
        }

        @Test
        @DisplayName("Ninguna escritura del catálogo está abierta a la recepción ni al odontólogo")
        void ningunaEscrituraEstaAbierta() throws Exception {
            long ose = obraSocial("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL");
            long base = plan(ose, "Plan Base");
            long cod = codigo("N-0101", "Consulta", null);
            for (String usuario : new String[] {"recepcion", "ana.ejemplo"}) {
                var navegador = como(usuario);
                assertThat(status(put(navegador, OBRAS_SOCIALES + "/" + ose,
                        obraSocialSolicitud("Otro", null, "PREPAGA")))).isEqualTo(403);
                assertThat(status(post(navegador, OBRAS_SOCIALES + "/" + ose + "/desactivar", null))).isEqualTo(403);
                assertThat(status(post(navegador, OBRAS_SOCIALES + "/" + ose + "/planes", Map.of("nombre", "X"))))
                        .isEqualTo(403);
                assertThat(status(put(navegador, OBRAS_SOCIALES + "/" + ose + "/planes/" + base, Map.of("nombre", "X"))))
                        .isEqualTo(403);
                assertThat(status(post(navegador, NOMENCLADOR, codigoSolicitud("N-9", "x", null)))).isEqualTo(403);
                assertThat(status(put(navegador, NOMENCLADOR + "/" + cod, codigoSolicitud("N-9", "x", null))))
                        .isEqualTo(403);
                assertThat(status(post(navegador, NOMENCLADOR + "/" + cod + "/desactivar", null))).isEqualTo(403);
            }
            assertThat(jdbc.queryForObject("SELECT nombre FROM obra_social WHERE id = ?", String.class, ose))
                    .isEqualTo("Obra Social Ejemplo");
            assertThat(jdbc.queryForObject("SELECT activo FROM nomenclador WHERE id = ?", Boolean.class, cod)).isTrue();
            assertThat(cantidad("plan")).isEqualTo(1);
        }

        @Test
        @DisplayName("El paciente no consulta el nomenclador ni los planes")
        void elPacienteNoConsultaElNomencladorNiLosPlanes() throws Exception {
            long ose = obraSocial("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL");
            var paciente = como("paciente.uno");
            assertThat(status(get(paciente, NOMENCLADOR))).isEqualTo(403);
            assertThat(status(get(paciente, OBRAS_SOCIALES + "/" + ose + "/planes"))).isEqualTo(403);
        }
    }
}
