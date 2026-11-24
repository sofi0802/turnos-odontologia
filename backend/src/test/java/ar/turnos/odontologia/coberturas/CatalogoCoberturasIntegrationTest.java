package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.shared.security.Navegador;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Obras sociales y planes (C-18, spec catalogo-coberturas, tarea 3.1): alta y modificación con nombre único, planes
 * dentro de su obra social, desactivar en lugar de borrar y consulta del catálogo.
 */
@Tag("integration")
class CatalogoCoberturasIntegrationTest extends IntegracionCoberturasBase {

    @Nested
    class AltaYModificacionDeObrasSociales {

        @Test
        @DisplayName("Alta de una obra social")
        void altaDeUnaObraSocial() throws Exception {
            // WHEN admin da de alta "Obra Social Ejemplo" con sigla OSE y tipo OBRA_SOCIAL
            MvcResult r = post(como("admin"), OBRAS_SOCIALES,
                    obraSocialSolicitud("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL"));

            // THEN la respuesta es 201 con la obra social activa, su sigla y su tipo
            assertThat(status(r)).isEqualTo(201);
            JsonNode o = cuerpo(r);
            assertThat(o.path("nombre").asText()).isEqualTo("Obra Social Ejemplo");
            assertThat(o.path("sigla").asText()).isEqualTo("OSE");
            assertThat(o.path("tipo").asText()).isEqualTo("OBRA_SOCIAL");
            assertThat(o.path("activa").asBoolean()).isTrue();
            assertThat(r.getResponse().getHeader("Location")).endsWith(OBRAS_SOCIALES + "/" + o.path("id").asLong());
        }

        @Test
        @DisplayName("Alta de una prepaga sin sigla")
        void altaDeUnaPrepagaSinSigla() throws Exception {
            // WHEN admin da de alta "Prepaga Prueba" con tipo PREPAGA y sin sigla
            MvcResult r = post(como("admin"), OBRAS_SOCIALES, obraSocialSolicitud("Prepaga Prueba", null, "PREPAGA"));

            // THEN la respuesta es 201 con la obra social activa, de tipo PREPAGA y sin sigla
            assertThat(status(r)).isEqualTo(201);
            assertThat(cuerpo(r).path("activa").asBoolean()).isTrue();
            assertThat(cuerpo(r).path("tipo").asText()).isEqualTo("PREPAGA");
            assertThat(cuerpo(r).path("sigla").isNull()).isTrue();
        }

        @Test
        @DisplayName("Nombre repetido con otras mayúsculas")
        void nombreRepetidoConOtrasMayusculas() throws Exception {
            // GIVEN existe "Obra Social Ejemplo"
            obraSocial("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL");

            // WHEN admin da de alta " obra social ejemplo " con tipo OBRA_SOCIAL
            MvcResult r = post(como("admin"), OBRAS_SOCIALES,
                    obraSocialSolicitud(" obra social ejemplo ", null, "OBRA_SOCIAL"));

            // THEN la respuesta es 409 con el código OBRA_SOCIAL_EXISTENTE
            assertThat(status(r)).isEqualTo(409);
            assertThat(codigoDeError(r)).isEqualTo("OBRA_SOCIAL_EXISTENTE");
            // AND sigue existiendo una sola obra social con ese nombre
            assertThat(cantidad("obra_social")).isEqualTo(1);
        }

        @Test
        @DisplayName("Modificar el tipo y la sigla")
        void modificarElTipoYLaSigla() throws Exception {
            // GIVEN existe "Prepaga Prueba" de tipo PREPAGA sin sigla
            long id = obraSocial("Prepaga Prueba", null, "PREPAGA");
            Navegador admin = como("admin");

            // WHEN admin la modifica con sigla PP y el mismo tipo
            MvcResult r = put(admin, OBRAS_SOCIALES + "/" + id, obraSocialSolicitud("Prepaga Prueba", "PP", "PREPAGA"));

            // THEN la respuesta es 200 y la consulta de "Prepaga Prueba" muestra la sigla PP
            assertThat(status(r)).isEqualTo(200);
            assertThat(cuerpo(get(admin, OBRAS_SOCIALES + "/" + id)).path("sigla").asText()).isEqualTo("PP");
        }

        @Test
        @DisplayName("Modificar con el nombre de otra obra social")
        void modificarConElNombreDeOtraObraSocial() throws Exception {
            // GIVEN existen "Obra Social Ejemplo" y "Prepaga Prueba"
            obraSocial("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL");
            long prepaga = obraSocial("Prepaga Prueba", null, "PREPAGA");

            // WHEN admin cambia el nombre de "Prepaga Prueba" a "Obra Social Ejemplo"
            MvcResult r = put(como("admin"), OBRAS_SOCIALES + "/" + prepaga,
                    obraSocialSolicitud("Obra Social Ejemplo", null, "PREPAGA"));

            // THEN la respuesta es 409 con el código OBRA_SOCIAL_EXISTENTE
            assertThat(status(r)).isEqualTo(409);
            assertThat(codigoDeError(r)).isEqualTo("OBRA_SOCIAL_EXISTENTE");
        }

        @Test
        @DisplayName("Modificar una obra social conservando su propio nombre")
        void modificarConservandoElNombre() throws Exception {
            long id = obraSocial("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL");
            MvcResult r = put(como("admin"), OBRAS_SOCIALES + "/" + id,
                    obraSocialSolicitud("OBRA SOCIAL EJEMPLO", "OSE", "OBRA_SOCIAL"));
            assertThat(status(r)).isEqualTo(200);
            assertThat(cuerpo(r).path("nombre").asText()).isEqualTo("OBRA SOCIAL EJEMPLO");
        }
    }

    @Nested
    class PlanesConNombreUnicoDentroDeSuObraSocial {

        @Test
        @DisplayName("Alta de un plan")
        void altaDeUnPlan() throws Exception {
            // GIVEN existe "Obra Social Ejemplo"
            long ose = obraSocial("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL");

            // WHEN admin da de alta "Plan Base" en "Obra Social Ejemplo"
            MvcResult r = post(como("admin"), OBRAS_SOCIALES + "/" + ose + "/planes", java.util.Map.of("nombre", "Plan Base"));

            // THEN la respuesta es 201 con "Plan Base" activo, de "Obra Social Ejemplo"
            assertThat(status(r)).isEqualTo(201);
            assertThat(cuerpo(r).path("nombre").asText()).isEqualTo("Plan Base");
            assertThat(cuerpo(r).path("activo").asBoolean()).isTrue();
            assertThat(cuerpo(r).path("obraSocialId").asLong()).isEqualTo(ose);
        }

        @Test
        @DisplayName("Plan repetido en la misma obra social")
        void planRepetidoEnLaMismaObraSocial() throws Exception {
            // GIVEN "Obra Social Ejemplo" tiene "Plan Base"
            long ose = obraSocial("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL");
            plan(ose, "Plan Base");

            // WHEN admin da de alta "PLAN BASE" en "Obra Social Ejemplo"
            MvcResult r = post(como("admin"), OBRAS_SOCIALES + "/" + ose + "/planes", java.util.Map.of("nombre", "PLAN BASE"));

            // THEN la respuesta es 409 con el código PLAN_EXISTENTE
            assertThat(status(r)).isEqualTo(409);
            assertThat(codigoDeError(r)).isEqualTo("PLAN_EXISTENTE");
        }

        @Test
        @DisplayName("Mismo nombre de plan en otra obra social")
        void mismoNombreDePlanEnOtraObraSocial() throws Exception {
            // GIVEN "Obra Social Ejemplo" tiene "Plan Base"
            long ose = obraSocial("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL");
            plan(ose, "Plan Base");
            long prepaga = obraSocial("Prepaga Prueba", null, "PREPAGA");

            // WHEN admin da de alta "Plan Base" en "Prepaga Prueba"
            MvcResult r = post(como("admin"), OBRAS_SOCIALES + "/" + prepaga + "/planes",
                    java.util.Map.of("nombre", "Plan Base"));

            // THEN la respuesta es 201
            assertThat(status(r)).isEqualTo(201);
        }

        @Test
        @DisplayName("Plan de una obra social inexistente")
        void planDeUnaObraSocialInexistente() throws Exception {
            // WHEN admin da de alta "Plan Oro" en la obra social con id 999999
            MvcResult r = post(como("admin"), OBRAS_SOCIALES + "/999999/planes", java.util.Map.of("nombre", "Plan Oro"));

            // THEN la respuesta es 404 con el código RECURSO_NO_ENCONTRADO
            assertThat(status(r)).isEqualTo(404);
            assertThat(codigoDeError(r)).isEqualTo("RECURSO_NO_ENCONTRADO");
        }

        @Test
        @DisplayName("Modificar un plan desde otra obra social")
        void modificarUnPlanDesdeOtraObraSocial() throws Exception {
            // GIVEN "Plan Oro" pertenece a "Prepaga Prueba"
            long ose = obraSocial("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL");
            long prepaga = obraSocial("Prepaga Prueba", null, "PREPAGA");
            long oro = plan(prepaga, "Plan Oro");

            // WHEN admin modifica "Plan Oro" usando la ruta de "Obra Social Ejemplo"
            MvcResult r = put(como("admin"), OBRAS_SOCIALES + "/" + ose + "/planes/" + oro,
                    java.util.Map.of("nombre", "Plan Oro Plus"));

            // THEN la respuesta es 404 con el código RECURSO_NO_ENCONTRADO
            assertThat(status(r)).isEqualTo(404);
            assertThat(codigoDeError(r)).isEqualTo("RECURSO_NO_ENCONTRADO");
            // AND "Plan Oro" sigue perteneciendo a "Prepaga Prueba" sin cambios
            assertThat(jdbc.queryForObject("SELECT obra_social_id FROM plan WHERE id = ?", Long.class, oro))
                    .isEqualTo(prepaga);
            assertThat(jdbc.queryForObject("SELECT nombre FROM plan WHERE id = ?", String.class, oro))
                    .isEqualTo("Plan Oro");
        }

        @Test
        @DisplayName("Modificar el nombre de un plan")
        void modificarElNombreDeUnPlan() throws Exception {
            long ose = obraSocial("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL");
            long base = plan(ose, "Plan Base");
            plan(ose, "Plan Integral");
            Navegador admin = como("admin");

            MvcResult renombrado = put(admin, OBRAS_SOCIALES + "/" + ose + "/planes/" + base,
                    java.util.Map.of("nombre", "Plan Base Plus"));
            MvcResult repetido = put(admin, OBRAS_SOCIALES + "/" + ose + "/planes/" + base,
                    java.util.Map.of("nombre", "plan integral"));

            assertThat(status(renombrado)).isEqualTo(200);
            assertThat(cuerpo(renombrado).path("nombre").asText()).isEqualTo("Plan Base Plus");
            assertThat(status(repetido)).isEqualTo(409);
            assertThat(codigoDeError(repetido)).isEqualTo("PLAN_EXISTENTE");
        }
    }

    @Nested
    class DesactivarEnLugarDeBorrar {

        @Test
        @DisplayName("Desactivar una obra social no desactiva sus planes")
        void desactivarUnaObraSocialNoDesactivaSusPlanes() throws Exception {
            // GIVEN "Obra Social Ejemplo" activa con "Plan Base" activo
            long ose = obraSocial("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL");
            long base = plan(ose, "Plan Base");

            // WHEN admin desactiva "Obra Social Ejemplo"
            MvcResult r = post(como("admin"), OBRAS_SOCIALES + "/" + ose + "/desactivar", null);

            // THEN la respuesta es 200 con la obra social inactiva
            assertThat(status(r)).isEqualTo(200);
            assertThat(cuerpo(r).path("activa").asBoolean()).isFalse();
            // AND "Plan Base" sigue activo
            assertThat(jdbc.queryForObject("SELECT activo FROM plan WHERE id = ?", Boolean.class, base)).isTrue();
        }

        @Test
        @DisplayName("Reactivar un plan")
        void reactivarUnPlan() throws Exception {
            // GIVEN "Plan Integral" está inactivo
            long ose = obraSocial("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL");
            long integral = plan(ose, "Plan Integral");
            jdbc.update("UPDATE plan SET activo = FALSE WHERE id = ?", integral);

            // WHEN admin activa "Plan Integral"
            MvcResult r = post(como("admin"), OBRAS_SOCIALES + "/" + ose + "/planes/" + integral + "/activar", null);

            // THEN la respuesta es 200 con "Plan Integral" activo
            assertThat(status(r)).isEqualTo(200);
            assertThat(cuerpo(r).path("activo").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("Desactivar un plan")
        void desactivarUnPlan() throws Exception {
            long ose = obraSocial("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL");
            long base = plan(ose, "Plan Base");

            MvcResult r = post(como("admin"), OBRAS_SOCIALES + "/" + ose + "/planes/" + base + "/desactivar", null);

            assertThat(status(r)).isEqualTo(200);
            assertThat(cuerpo(r).path("activo").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("Reactivar una obra social")
        void reactivarUnaObraSocial() throws Exception {
            long ose = obraSocial("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL");
            jdbc.update("UPDATE obra_social SET activa = FALSE WHERE id = ?", ose);

            MvcResult r = post(como("admin"), OBRAS_SOCIALES + "/" + ose + "/activar", null);

            assertThat(status(r)).isEqualTo(200);
            assertThat(cuerpo(r).path("activa").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("No hay borrado")
        void noHayBorrado() throws Exception {
            // WHEN admin pide DELETE sobre "Obra Social Ejemplo"
            long ose = obraSocial("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL");
            MvcResult r = delete(como("admin"), OBRAS_SOCIALES + "/" + ose);

            // THEN la respuesta no es exitosa y "Obra Social Ejemplo" sigue existiendo
            assertThat(status(r)).isGreaterThanOrEqualTo(400);
            assertThat(cantidad("obra_social")).isEqualTo(1);
        }
    }

    @Nested
    class ConsultaDelCatalogoDeCoberturas {

        @Test
        @DisplayName("Listar las obras sociales con sus planes")
        void listarLasObrasSocialesConSusPlanes() throws Exception {
            // GIVEN "Obra Social Ejemplo" con "Plan Base" y "Plan Integral", y "Prepaga Prueba" con "Plan Oro"
            long prepaga = obraSocial("Prepaga Prueba", null, "PREPAGA");
            long ose = obraSocial("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL");
            plan(ose, "Plan Integral");
            plan(ose, "Plan Base");
            plan(prepaga, "Plan Oro");

            // WHEN recepcion lista las obras sociales
            MvcResult r = get(como("recepcion"), OBRAS_SOCIALES);

            // THEN la respuesta es 200 con "Obra Social Ejemplo" y "Prepaga Prueba", en ese orden, con sus planes
            assertThat(status(r)).isEqualTo(200);
            JsonNode lista = cuerpo(r);
            assertThat(lista).hasSize(2);
            assertThat(lista.get(0).path("nombre").asText()).isEqualTo("Obra Social Ejemplo");
            assertThat(lista.get(1).path("nombre").asText()).isEqualTo("Prepaga Prueba");
            assertThat(lista.get(0).path("planes")).extracting(p -> p.path("nombre").asText())
                    .containsExactly("Plan Base", "Plan Integral");
            assertThat(lista.get(1).path("planes")).extracting(p -> p.path("nombre").asText())
                    .containsExactly("Plan Oro");
        }

        @Test
        @DisplayName("Obra social inexistente")
        void obraSocialInexistente() throws Exception {
            // WHEN admin consulta la obra social con id 999999
            MvcResult r = get(como("admin"), OBRAS_SOCIALES + "/999999");

            // THEN la respuesta es 404 con el código RECURSO_NO_ENCONTRADO
            assertThat(status(r)).isEqualTo(404);
            assertThat(codigoDeError(r)).isEqualTo("RECURSO_NO_ENCONTRADO");
        }

        @Test
        @DisplayName("Consultar una obra social, sus planes y un plan por id")
        void consultarPorId() throws Exception {
            long ose = obraSocial("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL");
            long base = plan(ose, "Plan Base");
            Navegador recepcion = como("recepcion");

            MvcResult obra = get(recepcion, OBRAS_SOCIALES + "/" + ose);
            MvcResult planes = get(recepcion, OBRAS_SOCIALES + "/" + ose + "/planes");
            MvcResult unPlan = get(recepcion, OBRAS_SOCIALES + "/" + ose + "/planes/" + base);
            MvcResult planAjeno = get(recepcion, OBRAS_SOCIALES + "/999999/planes/" + base);

            assertThat(cuerpo(obra).path("planes")).hasSize(1);
            assertThat(cuerpo(planes)).hasSize(1);
            assertThat(cuerpo(unPlan).path("nombre").asText()).isEqualTo("Plan Base");
            assertThat(status(planAjeno)).isEqualTo(404);
            assertThat(get(recepcion, OBRAS_SOCIALES + "/" + ose + "/planes/999999").getResponse().getStatus())
                    .isEqualTo(404);
        }
    }
}
