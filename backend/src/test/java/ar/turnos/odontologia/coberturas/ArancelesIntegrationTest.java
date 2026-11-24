package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Aranceles de las prestaciones (C-19, spec aranceles-y-cobertura-planes, tarea 3.1): precio particular de cada
 * prestación, su validación y su consulta. Un test por escenario.
 */
class ArancelesIntegrationTest extends IntegracionPresupuestosBase {

    private MvcResult cargar(String usuario, long prestacion, Object precio) throws Exception {
        return put(como(usuario), arancelUrl(prestacion), precioSolicitud(precio));
    }

    @Nested
    class PrecioParticularDeCadaPrestacion {

        @Test
        @DisplayName("Cargar el precio de una prestación")
        void cargarElPrecio() throws Exception {
            // GIVEN "Consulta o control" no tiene precio
            assertThat(precioGuardado(consulta)).isNull();

            // WHEN admin carga el precio de "Consulta o control" en 15000.00
            MvcResult r = cargar("admin", consulta, new BigDecimal("15000.00"));

            // THEN la respuesta es 200 con "Consulta o control" y el precio 15000.00
            assertThat(status(r)).isEqualTo(200);
            JsonNode cuerpo = cuerpo(r);
            assertThat(cuerpo.path("prestacion").path("nombre").asText()).isEqualTo("Consulta o control");
            assertThat(cuerpo.path("precio").decimalValue()).isEqualByComparingTo("15000.00");
            assertThat(precioGuardado(consulta)).isEqualByComparingTo("15000.00");
        }

        @Test
        @DisplayName("Cambiar el precio reemplaza el anterior")
        void cambiarElPrecio() throws Exception {
            // GIVEN "Limpieza (profilaxis)" tiene el precio 25000.00
            arancel(limpieza, "25000.00");

            // WHEN admin carga el precio de "Limpieza (profilaxis)" en 27000.00
            MvcResult r = cargar("admin", limpieza, new BigDecimal("27000.00"));

            // THEN la respuesta es 200 con el precio 27000.00
            assertThat(status(r)).isEqualTo(200);
            assertThat(cuerpo(r).path("precio").decimalValue()).isEqualByComparingTo("27000.00");
            // AND la lista de aranceles muestra una sola vez "Limpieza (profilaxis)", con 27000.00
            JsonNode lista = cuerpo(get(como("admin"), ARANCELES));
            int veces = 0;
            for (JsonNode fila : lista) {
                if ("Limpieza (profilaxis)".equals(fila.path("prestacion").path("nombre").asText())) {
                    veces++;
                    assertThat(fila.path("precio").decimalValue()).isEqualByComparingTo("27000.00");
                }
            }
            assertThat(veces).isEqualTo(1);
            assertThat(cantidad("arancel")).isEqualTo(1);
        }

        @Test
        @DisplayName("Precio de una prestación inexistente")
        void prestacionInexistente() throws Exception {
            // WHEN admin carga el precio de la prestación con id 999999 en 15000.00
            MvcResult r = cargar("admin", 999999L, new BigDecimal("15000.00"));

            // THEN la respuesta es 404 con el código RECURSO_NO_ENCONTRADO
            assertThat(status(r)).isEqualTo(404);
            assertThat(codigoDeError(r)).isEqualTo("RECURSO_NO_ENCONTRADO");
            assertThat(cantidad("arancel")).isZero();
        }
    }

    @Nested
    class PrecioValido {

        @Test
        @DisplayName("Precio cero")
        void precioCero() throws Exception {
            // GIVEN "Consulta o control" tiene el precio 15000.00
            arancel(consulta, "15000.00");

            // WHEN admin carga el precio de "Consulta o control" en 0
            MvcResult r = cargar("admin", consulta, BigDecimal.ZERO);

            // THEN la respuesta es 422 con el código DATOS_INVALIDOS y el campo precio
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("DATOS_INVALIDOS");
            assertThat(campos(r)).containsExactly("precio");
            // AND "Consulta o control" sigue con 15000.00
            assertThat(precioGuardado(consulta)).isEqualByComparingTo("15000.00");
        }

        @Test
        @DisplayName("Precio negativo")
        void precioNegativo() throws Exception {
            // WHEN admin carga el precio de "Consulta o control" en -100.00
            MvcResult r = cargar("admin", consulta, new BigDecimal("-100.00"));

            // THEN la respuesta es 422 con el código DATOS_INVALIDOS y el campo precio
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("DATOS_INVALIDOS");
            assertThat(campos(r)).containsExactly("precio");
            assertThat(cantidad("arancel")).isZero();
        }

        @Test
        @DisplayName("Precio con tres decimales")
        void precioConTresDecimales() throws Exception {
            // WHEN admin carga el precio de "Consulta o control" en 15000.005
            MvcResult r = cargar("admin", consulta, new BigDecimal("15000.005"));

            // THEN la respuesta es 422 con el código DATOS_INVALIDOS y el campo precio
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("DATOS_INVALIDOS");
            assertThat(campos(r)).containsExactly("precio");
            assertThat(cantidad("arancel")).isZero();
        }

        @Test
        @DisplayName("Precio ausente o mayor que el máximo")
        void precioAusenteOMaximo() throws Exception {
            MvcResult ausente = put(como("admin"), arancelUrl(consulta), new java.util.LinkedHashMap<String, Object>());
            assertThat(status(ausente)).isEqualTo(422);
            assertThat(campos(ausente)).containsExactly("precio");

            MvcResult enorme = cargar("admin", consulta, new BigDecimal("10000000000.00"));
            assertThat(status(enorme)).isEqualTo(422);
            assertThat(campos(enorme)).containsExactly("precio");

            MvcResult maximo = cargar("admin", consulta, new BigDecimal("9999999999.99"));
            assertThat(status(maximo)).isEqualTo(200);
        }
    }

    @Nested
    class ConsultaDeLosAranceles {

        @Test
        @DisplayName("Prestación sin precio en la lista")
        void prestacionSinPrecioEnLaLista() throws Exception {
            // GIVEN "Consulta o control" tiene el precio 15000.00 y "Tratamiento de conducto" no tiene precio
            arancel(consulta, "15000.00");

            // WHEN recepcion lista los aranceles
            MvcResult r = get(como("recepcion"), ARANCELES);

            // THEN la respuesta es 200 con "Consulta o control" y 15000.00, y "Tratamiento de conducto" sin precio
            assertThat(status(r)).isEqualTo(200);
            JsonNode lista = cuerpo(r);
            assertThat(lista).hasSize(4);
            assertThat(lista.get(0).path("prestacion").path("nombre").asText()).isEqualTo("Consulta o control");
            assertThat(lista.get(0).path("precio").decimalValue()).isEqualByComparingTo("15000.00");
            assertThat(lista.get(0).path("activa").asBoolean()).isTrue();
            assertThat(lista.get(0).path("actualizadoEn").isNull()).isFalse();
            JsonNode conductoFila = null;
            for (JsonNode fila : lista) {
                if ("Tratamiento de conducto".equals(fila.path("prestacion").path("nombre").asText())) {
                    conductoFila = fila;
                }
            }
            assertThat(conductoFila).isNotNull();
            assertThat(conductoFila.path("precio").isNull()).isTrue();
            assertThat(conductoFila.path("actualizadoEn").isNull()).isTrue();
            // AND ordenadas por nombre
            assertThat(lista.findValuesAsText("nombre")).isSorted();
        }
    }
}
