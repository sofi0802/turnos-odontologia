package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Cobertura de cada plan por código del nomenclador (C-19, spec aranceles-y-cobertura-planes, tarea 3.3): carga,
 * validación del porcentaje y consulta. Un test por escenario.
 */
class CoberturaPlanIntegrationTest extends IntegracionPresupuestosBase {

    private MvcResult cargar(String usuario, long obraSocial, long plan, long codigo, Object porcentaje) throws Exception {
        return put(como(usuario), coberturaPlanUrl(obraSocial, plan) + "/" + codigo, porcentajeSolicitud(porcentaje));
    }

    @Nested
    class PorcentajeDeCoberturaPorPlanYCodigo {

        @Test
        @DisplayName("Cargar la cobertura de un código")
        void cargarLaCobertura() throws Exception {
            // GIVEN "Plan Base" no tiene cobertura cargada para N-0201
            semilla();
            jdbc.update("DELETE FROM cobertura_plan WHERE plan_id = ? AND nomenclador_id = ?", planBase, n0201);
            assertThat(porcentajeGuardado(planBase, n0201)).isNull();

            // WHEN admin carga para "Plan Base" de "Obra Social Ejemplo" el código N-0201 al 50 %
            MvcResult r = cargar("admin", obraSocialEjemplo, planBase, n0201, 50);

            // THEN la respuesta es 200 con N-0201 y el porcentaje 50
            assertThat(status(r)).isEqualTo(200);
            JsonNode cuerpo = cuerpo(r);
            assertThat(cuerpo.path("codigo").asText()).isEqualTo("N-0201");
            assertThat(cuerpo.path("porcentaje").asInt()).isEqualTo(50);
            assertThat(porcentajeGuardado(planBase, n0201)).isEqualTo(50);
        }

        @Test
        @DisplayName("Cambiar el porcentaje lo reemplaza")
        void cambiarElPorcentaje() throws Exception {
            // GIVEN "Plan Oro" cubre N-0101 al 80 %
            semilla();
            assertThat(porcentajeGuardado(planOro, n0101)).isEqualTo(80);

            // WHEN admin carga para "Plan Oro" el código N-0101 al 90 %
            MvcResult r = cargar("admin", prepaga, planOro, n0101, 90);

            // THEN la respuesta es 200 con el porcentaje 90
            assertThat(status(r)).isEqualTo(200);
            assertThat(cuerpo(r).path("porcentaje").asInt()).isEqualTo(90);
            // AND la cobertura de "Plan Oro" muestra una sola vez N-0101, con 90
            JsonNode lista = cuerpo(get(como("admin"), coberturaPlanUrl(prepaga, planOro)));
            int veces = 0;
            for (JsonNode fila : lista) {
                if ("N-0101".equals(fila.path("codigo").asText())) {
                    veces++;
                    assertThat(fila.path("porcentaje").asInt()).isEqualTo(90);
                }
            }
            assertThat(veces).isEqualTo(1);
        }

        @Test
        @DisplayName("Plan de otra obra social")
        void planDeOtraObraSocial() throws Exception {
            // GIVEN el catálogo de la KB
            semilla();

            // WHEN admin carga la cobertura de N-0101 al 80 % en la ruta de "Obra Social Ejemplo" con el id de "Plan Oro"
            MvcResult r = cargar("admin", obraSocialEjemplo, planOro, n0101, 80);

            // THEN la respuesta es 404 con el código RECURSO_NO_ENCONTRADO
            assertThat(status(r)).isEqualTo(404);
            assertThat(codigoDeError(r)).isEqualTo("RECURSO_NO_ENCONTRADO");
        }

        @Test
        @DisplayName("Código del nomenclador inexistente")
        void codigoInexistente() throws Exception {
            semilla();

            // WHEN admin carga para "Plan Base" el código con id 999999 al 50 %
            MvcResult r = cargar("admin", obraSocialEjemplo, planBase, 999999L, 50);

            // THEN la respuesta es 404 con el código RECURSO_NO_ENCONTRADO
            assertThat(status(r)).isEqualTo(404);
            assertThat(codigoDeError(r)).isEqualTo("RECURSO_NO_ENCONTRADO");
        }

        @Test
        @DisplayName("Un plan inexistente también es 404")
        void planInexistente() throws Exception {
            semilla();
            MvcResult r = cargar("admin", obraSocialEjemplo, 999999L, n0101, 50);
            assertThat(status(r)).isEqualTo(404);
            assertThat(codigoDeError(r)).isEqualTo("RECURSO_NO_ENCONTRADO");
        }
    }

    @Nested
    class PorcentajeValido {

        @Test
        @DisplayName("Porcentaje mayor que cien")
        void mayorQueCien() throws Exception {
            semilla();
            // WHEN admin carga para "Plan Base" el código N-0101 al 101 %
            MvcResult r = cargar("admin", obraSocialEjemplo, planBase, n0101, 101);

            // THEN la respuesta es 422 con el código DATOS_INVALIDOS y el campo porcentaje
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("DATOS_INVALIDOS");
            assertThat(campos(r)).containsExactly("porcentaje");
            assertThat(porcentajeGuardado(planBase, n0101)).isEqualTo(100);
        }

        @Test
        @DisplayName("Porcentaje negativo")
        void negativo() throws Exception {
            semilla();
            MvcResult r = cargar("admin", obraSocialEjemplo, planBase, n0101, -1);
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("DATOS_INVALIDOS");
            assertThat(campos(r)).containsExactly("porcentaje");
            assertThat(porcentajeGuardado(planBase, n0101)).isEqualTo(100);
        }

        @Test
        @DisplayName("Porcentaje con decimales")
        void conDecimales() throws Exception {
            semilla();
            // WHEN admin carga para "Plan Base" el código N-0201 al 50.5 %
            MvcResult r = cargar("admin", obraSocialEjemplo, planBase, n0201, new BigDecimal("50.5"));

            // THEN la respuesta es 422 con el código DATOS_INVALIDOS y el campo porcentaje
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("DATOS_INVALIDOS");
            assertThat(campos(r)).containsExactly("porcentaje");
            assertThat(porcentajeGuardado(planBase, n0201)).isEqualTo(50);
        }

        @Test
        @DisplayName("Porcentaje cero")
        void cero() throws Exception {
            semilla();
            // WHEN admin carga para "Plan Base" el código N-0301 al 0 %
            MvcResult r = cargar("admin", obraSocialEjemplo, planBase, n0301, 0);

            // THEN la respuesta es 200 con N-0301 y el porcentaje 0
            assertThat(status(r)).isEqualTo(200);
            assertThat(cuerpo(r).path("codigo").asText()).isEqualTo("N-0301");
            assertThat(cuerpo(r).path("porcentaje").asInt()).isZero();
            assertThat(porcentajeGuardado(planBase, n0301)).isZero();
        }

        @Test
        @DisplayName("Porcentaje ausente")
        void ausente() throws Exception {
            semilla();
            MvcResult r = put(como("admin"), coberturaPlanUrl(obraSocialEjemplo, planBase) + "/" + n0101,
                    new java.util.LinkedHashMap<String, Object>());
            assertThat(status(r)).isEqualTo(422);
            assertThat(campos(r)).containsExactly("porcentaje");
        }
    }

    @Nested
    class ConsultaDeLaCoberturaDeUnPlan {

        @Test
        @DisplayName("Cobertura del seed de \"Plan Base\"")
        void coberturaDelSeed() throws Exception {
            // GIVEN "Plan Base" cubre N-0201 al 50 % y N-0101 al 100 %, y no tiene cargado N-0301
            semilla();

            // WHEN ana.ejemplo consulta la cobertura de "Plan Base"
            MvcResult r = get(como("ana.ejemplo"), coberturaPlanUrl(obraSocialEjemplo, planBase));

            // THEN la respuesta es 200 con N-0101 al 100 % y N-0201 al 50 %, en ese orden, y sin N-0301
            assertThat(status(r)).isEqualTo(200);
            JsonNode lista = cuerpo(r);
            assertThat(lista).hasSize(2);
            assertThat(lista.get(0).path("codigo").asText()).isEqualTo("N-0101");
            assertThat(lista.get(0).path("porcentaje").asInt()).isEqualTo(100);
            assertThat(lista.get(0).path("descripcion").asText()).isEqualTo("Consulta");
            assertThat(lista.get(0).path("activo").asBoolean()).isTrue();
            assertThat(lista.get(1).path("codigo").asText()).isEqualTo("N-0201");
            assertThat(lista.get(1).path("porcentaje").asInt()).isEqualTo(50);
        }
    }
}
