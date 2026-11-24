package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Emisión y cálculo del presupuesto (C-19, spec presupuestos, tareas 4.1 y 4.3): cobertura y copago de cada ítem,
 * paciente particular, código del nomenclador, prestaciones presupuestables e ítems válidos. Hoy es 2026-11-24 en
 * Buenos Aires. Un test por escenario.
 */
class EmisionPresupuestoIntegrationTest extends IntegracionPresupuestosBase {

    @SafeVarargs
    private MvcResult emitir(String usuario, long paciente, Map<String, Object>... items) throws Exception {
        return post(como(usuario), presupuestosUrl(paciente), presupuestoSolicitud(items));
    }

    private static void assertImporte(JsonNode nodo, String esperado) {
        assertThat(nodo.decimalValue()).isEqualByComparingTo(esperado);
    }

    private static void assertItem(JsonNode item, String importe, int porcentaje, String cobertura, String copago) {
        assertImporte(item.path("importe"), importe);
        assertThat(item.path("porcentajeCobertura").asInt()).isEqualTo(porcentaje);
        assertImporte(item.path("cobertura"), cobertura);
        assertImporte(item.path("copago"), copago);
    }

    private static void assertTotales(JsonNode presupuesto, String precio, String cobertura, String copago) {
        assertImporte(presupuesto.path("totales").path("precio"), precio);
        assertImporte(presupuesto.path("totales").path("cobertura"), cobertura);
        assertImporte(presupuesto.path("totales").path("copago"), copago);
    }

    @Nested
    class EmitirUnPresupuesto {

        @Test
        @DisplayName("Presupuesto de un paciente con obra social")
        void pacienteConObraSocial() throws Exception {
            // GIVEN "Paciente Uno" tiene "Plan Base" vigente
            semilla();

            // WHEN ana.ejemplo emite un presupuesto con "Consulta o control" y "Limpieza (profilaxis)", cantidad 1 cada uno
            MvcResult r = emitir("ana.ejemplo", pacienteUno, item(consulta, 1), item(limpieza, 1));

            // THEN la respuesta es 201 con estado PENDIENTE, fecha 2026-11-24, vencimiento 2026-12-24 y profesional
            assertThat(status(r)).isEqualTo(201);
            JsonNode p = cuerpo(r);
            assertThat(r.getResponse().getHeader("Location")).endsWith(presupuestoUrl(pacienteUno, p.path("id").asLong()));
            assertThat(p.path("estado").asText()).isEqualTo("PENDIENTE");
            assertThat(p.path("vencido").asBoolean()).isFalse();
            assertThat(p.path("fecha").asText()).isEqualTo("2026-11-24");
            assertThat(p.path("venceEl").asText()).isEqualTo("2026-12-24");
            assertThat(p.path("profesional").path("id").asLong()).isEqualTo(profesionalAna);
            assertThat(p.path("profesional").path("nombre").asText()).isEqualTo("Ana Ejemplo");
            assertThat(p.path("particular").asBoolean()).isFalse();
            // AND el ítem "Consulta o control" tiene importe 15000.00, porcentaje 100, cobertura 15000.00 y copago 0.00
            assertItem(item(p, "Consulta o control"), "15000.00", 100, "15000.00", "0.00");
            assertThat(item(p, "Consulta o control").path("codigoNomenclador").asText()).isEqualTo("N-0101");
            // AND el ítem "Limpieza (profilaxis)" tiene importe 25000.00, porcentaje 50, cobertura 12500.00 y copago 12500.00
            assertItem(item(p, "Limpieza (profilaxis)"), "25000.00", 50, "12500.00", "12500.00");
            // AND los totales son precio 40000.00, cobertura 27500.00 y copago 12500.00, con la cobertura usada
            assertTotales(p, "40000.00", "27500.00", "12500.00");
            assertThat(p.path("cobertura").path("obraSocial").asText()).isEqualTo("Obra Social Ejemplo");
            assertThat(p.path("cobertura").path("plan").asText()).isEqualTo("Plan Base");
            assertThat(p.path("cobertura").path("numeroAfiliado").asText()).isEqualTo("OSE-000001");
            // AND los importes salen con dos decimales en el JSON
            assertThat(texto(r)).contains("\"precio\":40000.00", "\"cobertura\":27500.00", "\"copago\":12500.00");
            // AND la suma de los ítems leída de la base coincide con los totales guardados
            Map<String, Object> suma = jdbc.queryForMap("SELECT sum(i.importe) AS importe, sum(i.cobertura) AS cobertura, "
                    + "sum(i.copago) AS copago, max(p.total_precio) AS tp, max(p.total_cobertura) AS tc, "
                    + "max(p.total_copago) AS tcp FROM presupuesto_item i JOIN presupuesto p ON p.id = i.presupuesto_id");
            assertThat((BigDecimal) suma.get("importe")).isEqualByComparingTo((BigDecimal) suma.get("tp"));
            assertThat((BigDecimal) suma.get("cobertura")).isEqualByComparingTo((BigDecimal) suma.get("tc"));
            assertThat((BigDecimal) suma.get("copago")).isEqualByComparingTo((BigDecimal) suma.get("tcp"));
        }

        @Test
        @DisplayName("El profesional sale de la sesión")
        void elProfesionalSaleDeLaSesion() throws Exception {
            semilla();

            // WHEN bruno.prueba emite un presupuesto con "Consulta o control" e incluye en el pedido el id de la Dra. Ana
            Map<String, Object> pedido = presupuestoSolicitud(item(consulta, 1));
            pedido.put("profesionalId", profesionalAna);
            MvcResult r = post(como("bruno.prueba"), presupuestosUrl(pacienteUno), pedido);

            // THEN la respuesta es 201 con profesional "Dr. Bruno Prueba"
            assertThat(status(r)).isEqualTo(201);
            assertThat(cuerpo(r).path("profesional").path("id").asLong()).isEqualTo(profesionalBruno);
            assertThat(cuerpo(r).path("profesional").path("nombre").asText()).isEqualTo("Bruno Prueba");
            assertThat(jdbc.queryForObject("SELECT profesional_id FROM presupuesto", Long.class)).isEqualTo(profesionalBruno);
        }

        @Test
        @DisplayName("Paciente inexistente")
        void pacienteInexistente() throws Exception {
            semilla();

            // WHEN ana.ejemplo emite un presupuesto para el paciente con id 999999 con un ítem "Consulta o control"
            MvcResult r = emitir("ana.ejemplo", 999999L, item(consulta, 1));

            // THEN la respuesta es 404 con el código RECURSO_NO_ENCONTRADO
            assertThat(status(r)).isEqualTo(404);
            assertThat(codigoDeError(r)).isEqualTo("RECURSO_NO_ENCONTRADO");
            assertThat(cantidad("presupuesto")).isZero();
        }
    }

    @Nested
    class CalculoDeCoberturaYCopagoDeCadaItem {

        @Test
        @DisplayName("Paciente con prepaga")
        void pacienteConPrepaga() throws Exception {
            // GIVEN "Paciente Tres" tiene "Plan Oro" vigente
            semilla();

            // WHEN ana.ejemplo emite un presupuesto con "Consulta o control" y "Limpieza (profilaxis)", cantidad 1 cada uno
            MvcResult r = emitir("ana.ejemplo", pacienteTres, item(consulta, 1), item(limpieza, 1));

            // THEN "Consulta o control" tiene cobertura 12000.00 y copago 3000.00
            assertThat(status(r)).isEqualTo(201);
            JsonNode p = cuerpo(r);
            assertItem(item(p, "Consulta o control"), "15000.00", 80, "12000.00", "3000.00");
            // AND "Limpieza (profilaxis)" tiene cobertura 20000.00 y copago 5000.00
            assertItem(item(p, "Limpieza (profilaxis)"), "25000.00", 80, "20000.00", "5000.00");
            // AND los totales son precio 40000.00, cobertura 32000.00 y copago 8000.00
            assertTotales(p, "40000.00", "32000.00", "8000.00");
            assertThat(p.path("cobertura").path("plan").asText()).isEqualTo("Plan Oro");
        }

        @Test
        @DisplayName("Cantidad mayor que uno")
        void cantidadMayorQueUno() throws Exception {
            semilla();

            // WHEN ana.ejemplo emite un presupuesto para "Paciente Tres" con "Extracción simple", cantidad 2
            MvcResult r = emitir("ana.ejemplo", pacienteTres, item(extraccion, 2));

            // THEN el ítem tiene precio unitario 30000.00, importe 60000.00, porcentaje 80, cobertura 48000.00 y copago 12000.00
            assertThat(status(r)).isEqualTo(201);
            JsonNode i = item(cuerpo(r), "Extracción simple");
            assertImporte(i.path("precioUnitario"), "30000.00");
            assertThat(i.path("cantidad").asInt()).isEqualTo(2);
            assertItem(i, "60000.00", 80, "48000.00", "12000.00");
        }

        @Test
        @DisplayName("Código sin cobertura en el plan")
        void codigoSinCoberturaEnElPlan() throws Exception {
            // GIVEN "Plan Base" no tiene cobertura cargada para N-0301
            semilla();
            assertThat(porcentajeGuardado(planBase, n0301)).isNull();

            // WHEN ana.ejemplo emite un presupuesto para "Paciente Uno" con "Extracción simple"
            MvcResult r = emitir("ana.ejemplo", pacienteUno, item(extraccion, 1));

            // THEN el ítem tiene porcentaje 0, cobertura 0.00 y copago 30000.00
            assertThat(status(r)).isEqualTo(201);
            JsonNode i = item(cuerpo(r), "Extracción simple");
            assertItem(i, "30000.00", 0, "0.00", "30000.00");
            assertThat(i.path("codigoNomenclador").asText()).isEqualTo("N-0301");
        }

        @Test
        @DisplayName("Redondeo half up a centavos")
        void redondeoHalfUp() throws Exception {
            // GIVEN admin cambió el precio de "Limpieza (profilaxis)" a 25000.15
            semilla();
            assertThat(status(put(como("admin"), arancelUrl(limpieza), precioSolicitud(new BigDecimal("25000.15")))))
                    .isEqualTo(200);

            // WHEN ana.ejemplo emite un presupuesto para "Paciente Uno" con "Limpieza (profilaxis)"
            MvcResult r = emitir("ana.ejemplo", pacienteUno, item(limpieza, 1));

            // THEN el ítem tiene importe 25000.15, porcentaje 50, cobertura 12500.08 y copago 12500.07
            assertThat(status(r)).isEqualTo(201);
            assertItem(item(cuerpo(r), "Limpieza (profilaxis)"), "25000.15", 50, "12500.08", "12500.07");
        }
    }

    @Nested
    class PacienteParticular {

        @Test
        @DisplayName("Paciente sin obra social paga todo")
        void sinObraSocialPagaTodo() throws Exception {
            // GIVEN "Paciente Dos" no tiene afiliaciones
            semilla();

            // WHEN ana.ejemplo emite un presupuesto con "Consulta o control" y "Limpieza (profilaxis)"
            MvcResult r = emitir("ana.ejemplo", pacienteDos, item(consulta, 1), item(limpieza, 1));

            // THEN la respuesta es 201 marcada como particular y sin afiliación
            assertThat(status(r)).isEqualTo(201);
            JsonNode p = cuerpo(r);
            assertThat(p.path("particular").asBoolean()).isTrue();
            assertThat(p.path("cobertura").isNull()).isTrue();
            assertThat(jdbc.queryForObject("SELECT afiliacion_id IS NULL FROM presupuesto", Boolean.class)).isTrue();
            // AND los totales son precio 40000.00, cobertura 0.00 y copago 40000.00
            assertTotales(p, "40000.00", "0.00", "40000.00");
            assertItem(item(p, "Consulta o control"), "15000.00", 0, "0.00", "15000.00");
        }

        @Test
        @DisplayName("Afiliación cerrada antes de la fecha del presupuesto")
        void afiliacionCerradaAntes() throws Exception {
            // GIVEN la afiliación de "Paciente Uno" a "Plan Base" se cerró el 2026-11-23
            semilla();
            jdbc.update("UPDATE afiliacion_paciente SET vigente_hasta = DATE '2026-11-23' WHERE paciente_id = ?", pacienteUno);

            // WHEN ana.ejemplo emite un presupuesto para "Paciente Uno" con "Consulta o control"
            MvcResult r = emitir("ana.ejemplo", pacienteUno, item(consulta, 1));

            // THEN el presupuesto es particular, con cobertura 0.00 y copago 15000.00
            assertThat(status(r)).isEqualTo(201);
            JsonNode p = cuerpo(r);
            assertThat(p.path("particular").asBoolean()).isTrue();
            assertTotales(p, "15000.00", "0.00", "15000.00");
        }
    }

    @Nested
    class CodigoDelNomencladorDeCadaItem {

        @Test
        @DisplayName("Prestación sin código")
        void prestacionSinCodigo() throws Exception {
            // GIVEN "Tratamiento de conducto" no tiene código del nomenclador
            semilla();

            // WHEN bruno.prueba emite un presupuesto para "Paciente Tres" con "Tratamiento de conducto" en la pieza 36
            MvcResult r = emitir("bruno.prueba", pacienteTres, item(conducto, null, 36, null, 1));

            // THEN el ítem no tiene código, con porcentaje 0, cobertura 0.00 y copago 90000.00
            assertThat(status(r)).isEqualTo(201);
            JsonNode i = item(cuerpo(r), "Tratamiento de conducto");
            assertThat(i.path("codigoNomenclador").isNull()).isTrue();
            assertThat(i.path("pieza").asInt()).isEqualTo(36);
            assertItem(i, "90000.00", 0, "0.00", "90000.00");
        }

        @Test
        @DisplayName("Prestación con varios códigos sin indicar cuál")
        void variosCodigosSinIndicar() throws Exception {
            // GIVEN admin agregó el código N-0102 "Consulta de urgencia", vinculado a "Consulta o control"
            semilla();
            codigo("N-0102", "Consulta de urgencia", consulta);

            // WHEN ana.ejemplo emite un presupuesto para "Paciente Uno" con "Consulta o control" sin indicar el código
            MvcResult r = emitir("ana.ejemplo", pacienteUno, item(consulta, 1));

            // THEN la respuesta es 422 con el código CODIGO_NOMENCLADOR_REQUERIDO
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("CODIGO_NOMENCLADOR_REQUERIDO");
            // AND no se crea ningún presupuesto
            assertThat(cantidad("presupuesto")).isZero();
        }

        @Test
        @DisplayName("Prestación con varios códigos indicando uno")
        void variosCodigosIndicandoUno() throws Exception {
            // GIVEN admin agregó el código N-0102 vinculado a "Consulta o control" y "Plan Base" no lo cubre
            semilla();
            long n0102 = codigo("N-0102", "Consulta de urgencia", consulta);

            // WHEN ana.ejemplo emite un presupuesto con "Consulta o control" y el código N-0102
            MvcResult r = emitir("ana.ejemplo", pacienteUno, item(consulta, n0102, null, null, 1));

            // THEN el ítem tiene el código N-0102, porcentaje 0 y copago 15000.00
            assertThat(status(r)).isEqualTo(201);
            JsonNode i = item(cuerpo(r), "Consulta o control");
            assertThat(i.path("codigoNomenclador").asText()).isEqualTo("N-0102");
            assertItem(i, "15000.00", 0, "0.00", "15000.00");
        }

        @Test
        @DisplayName("Código de otra prestación")
        void codigoDeOtraPrestacion() throws Exception {
            semilla();

            // WHEN ana.ejemplo emite un presupuesto con "Consulta o control" y el código N-0201
            MvcResult r = emitir("ana.ejemplo", pacienteUno, item(consulta, n0201, null, null, 1));

            // THEN la respuesta es 422 con el código CODIGO_NOMENCLADOR_INVALIDO
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("CODIGO_NOMENCLADOR_INVALIDO");
            assertThat(cantidad("presupuesto")).isZero();
        }

        @Test
        @DisplayName("Código inactivo")
        void codigoInactivo() throws Exception {
            // GIVEN admin desactivó N-0301
            semilla();
            jdbc.update("UPDATE nomenclador SET activo = false WHERE id = ?", n0301);

            // WHEN ana.ejemplo emite un presupuesto para "Paciente Tres" con "Extracción simple" y el código N-0301
            MvcResult r = emitir("ana.ejemplo", pacienteTres, item(extraccion, n0301, null, null, 1));

            // THEN la respuesta es 422 con el código CODIGO_NOMENCLADOR_INVALIDO
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("CODIGO_NOMENCLADOR_INVALIDO");
        }

        @Test
        @DisplayName("Un código indicado en una prestación sin códigos es inválido")
        void codigoEnPrestacionSinCodigos() throws Exception {
            semilla();
            MvcResult r = emitir("ana.ejemplo", pacienteUno, item(conducto, n0101, null, null, 1));
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("CODIGO_NOMENCLADOR_INVALIDO");
        }

        @Test
        @DisplayName("Una prestación con un solo código activo lo usa aunque otro esté inactivo")
        void unSoloCodigoActivo() throws Exception {
            semilla();
            long n0102 = codigo("N-0102", "Consulta de urgencia", consulta);
            jdbc.update("UPDATE nomenclador SET activo = false WHERE id = ?", n0102);
            MvcResult r = emitir("ana.ejemplo", pacienteUno, item(consulta, 1));
            assertThat(status(r)).isEqualTo(201);
            assertThat(item(cuerpo(r), "Consulta o control").path("codigoNomenclador").asText()).isEqualTo("N-0101");
        }
    }

    @Nested
    class PrestacionesPresupuestables {

        @Test
        @DisplayName("Prestación sin precio")
        void prestacionSinPrecio() throws Exception {
            // GIVEN "Tratamiento de conducto" no tiene precio particular
            semilla();
            jdbc.update("DELETE FROM arancel WHERE prestacion_id = ?", conducto);

            // WHEN bruno.prueba emite un presupuesto para "Paciente Uno" con "Consulta o control" y "Tratamiento de conducto"
            MvcResult r = emitir("bruno.prueba", pacienteUno, item(consulta, 1), item(conducto, 1));

            // THEN la respuesta es 409 con el código ARANCEL_INEXISTENTE
            assertThat(status(r)).isEqualTo(409);
            assertThat(codigoDeError(r)).isEqualTo("ARANCEL_INEXISTENTE");
            // AND "Paciente Uno" no tiene presupuestos
            assertThat(jdbc.queryForObject("SELECT count(*) FROM presupuesto WHERE paciente_id = ?", Integer.class,
                    pacienteUno)).isZero();
        }

        @Test
        @DisplayName("Prestación inexistente")
        void prestacionInexistente() throws Exception {
            semilla();

            // WHEN ana.ejemplo emite un presupuesto para "Paciente Uno" con la prestación de id 999999
            MvcResult r = emitir("ana.ejemplo", pacienteUno, item(999999L, 1));

            // THEN la respuesta es 422 con el código PRESTACION_INEXISTENTE
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("PRESTACION_INEXISTENTE");
            assertThat(cantidad("presupuesto")).isZero();
        }

        @Test
        @DisplayName("Prestación inactiva")
        void prestacionInactiva() throws Exception {
            // GIVEN admin desactivó "Extracción simple"
            semilla();
            jdbc.update("UPDATE prestacion SET activo = false WHERE id = ?", extraccion);

            // WHEN ana.ejemplo emite un presupuesto para "Paciente Tres" con "Extracción simple"
            MvcResult r = emitir("ana.ejemplo", pacienteTres, item(extraccion, 1));

            // THEN la respuesta es 409 con el código PRESTACION_INACTIVA
            assertThat(status(r)).isEqualTo(409);
            assertThat(codigoDeError(r)).isEqualTo("PRESTACION_INACTIVA");
            assertThat(cantidad("presupuesto")).isZero();
        }
    }

    @Nested
    class ItemsValidos {

        private void assertSoloElCampo(MvcResult r, String campo) throws Exception {
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("DATOS_INVALIDOS");
            assertThat(campos(r)).containsExactly(campo);
            // el error trae solo el campo y un mensaje fijo, nunca el valor recibido
            assertThat(cuerpo(r).path("errores").get(0).path("mensaje").asText()).isEqualTo("valor inválido");
            assertThat(cuerpo(r).path("errores").get(0).size()).isEqualTo(2);
            assertThat(cantidad("presupuesto")).isZero();
        }

        @Test
        @DisplayName("Presupuesto sin ítems")
        void sinItems() throws Exception {
            semilla();
            // WHEN ana.ejemplo emite un presupuesto para "Paciente Uno" sin ítems
            MvcResult r = emitir("ana.ejemplo", pacienteUno);

            // THEN la respuesta es 422 con el código DATOS_INVALIDOS y el campo items
            assertSoloElCampo(r, "items");
        }

        @Test
        @DisplayName("Más de 50 ítems")
        void masDeCincuentaItems() throws Exception {
            semilla();
            @SuppressWarnings("unchecked")
            Map<String, Object>[] cincuentaYUno = new Map[51];
            for (int i = 0; i < 51; i++) {
                cincuentaYUno[i] = item(consulta, 1);
            }
            assertSoloElCampo(emitir("ana.ejemplo", pacienteUno, cincuentaYUno), "items");
            @SuppressWarnings("unchecked")
            Map<String, Object>[] cincuenta = new Map[50];
            java.util.Arrays.fill(cincuenta, item(consulta, 1));
            MvcResult r = emitir("ana.ejemplo", pacienteUno, cincuenta);
            assertThat(status(r)).isEqualTo(201);
            assertThat(cuerpo(r).path("items")).hasSize(50);
        }

        @Test
        @DisplayName("Cantidad cero")
        void cantidadCero() throws Exception {
            semilla();
            // WHEN ana.ejemplo emite un presupuesto para "Paciente Uno" con "Limpieza (profilaxis)", cantidad 0
            MvcResult r = emitir("ana.ejemplo", pacienteUno, item(limpieza, 0));

            // THEN la respuesta es 422 con el código DATOS_INVALIDOS y el campo items[0].cantidad
            assertSoloElCampo(r, "items[0].cantidad");
        }

        @Test
        @DisplayName("Cantidad mayor que 99")
        void cantidadMayorQueNoventaYNueve() throws Exception {
            semilla();
            assertSoloElCampo(emitir("ana.ejemplo", pacienteUno, item(consulta, 1), item(limpieza, 100)),
                    "items[1].cantidad");
        }

        @Test
        @DisplayName("Pieza fuera de la notación FDI")
        void piezaFueraDeFdi() throws Exception {
            semilla();
            // WHEN ana.ejemplo emite un presupuesto para "Paciente Uno" con "Extracción simple" en la pieza 19
            MvcResult r = emitir("ana.ejemplo", pacienteUno, item(extraccion, null, 19, null, 1));

            // THEN la respuesta es 422 con el código DATOS_INVALIDOS y el campo items[0].pieza
            assertSoloElCampo(r, "items[0].pieza");
        }

        @Test
        @DisplayName("Cara palatina en una pieza inferior")
        void caraPalatinaEnPiezaInferior() throws Exception {
            semilla();
            // WHEN ana.ejemplo emite un presupuesto con "Consulta o control" en la pieza 36, cara P
            MvcResult r = emitir("ana.ejemplo", pacienteUno, item(consulta, null, 36, "P", 1));

            // THEN la respuesta es 422 con el código DATOS_INVALIDOS y el campo items[0].cara
            assertSoloElCampo(r, "items[0].cara");
        }

        @Test
        @DisplayName("Cara sin pieza")
        void caraSinPieza() throws Exception {
            semilla();
            // WHEN ana.ejemplo emite un presupuesto con "Consulta o control", cara O y sin pieza
            MvcResult r = emitir("ana.ejemplo", pacienteUno, item(consulta, null, null, "O", 1));

            // THEN la respuesta es 422 con el código DATOS_INVALIDOS y el campo items[0].cara
            assertSoloElCampo(r, "items[0].cara");
        }

        @Test
        @DisplayName("Pieza y cara válidas")
        void piezaYCaraValidas() throws Exception {
            // GIVEN el catálogo de la KB
            semilla();

            // WHEN ana.ejemplo emite un presupuesto para "Paciente Tres" con "Extracción simple" en la pieza 36 y
            // "Consulta o control" en la pieza 16, cara O
            MvcResult r = emitir("ana.ejemplo", pacienteTres, item(extraccion, null, 36, null, 1),
                    item(consulta, null, 16, "O", 1));

            // THEN la respuesta es 201 con las piezas 36 y 16, y la cara O en la 16
            assertThat(status(r)).isEqualTo(201);
            JsonNode p = cuerpo(r);
            assertThat(item(p, "Extracción simple").path("pieza").asInt()).isEqualTo(36);
            assertThat(item(p, "Extracción simple").path("cara").isNull()).isTrue();
            assertThat(item(p, "Consulta o control").path("pieza").asInt()).isEqualTo(16);
            assertThat(item(p, "Consulta o control").path("cara").asText()).isEqualTo("O");
        }

        @Test
        @DisplayName("Un cuerpo ilegible o un campo de tipo equivocado responde 422")
        void cuerpoIlegible() throws Exception {
            semilla();
            Map<String, Object> raro = new LinkedHashMap<>();
            raro.put("items", "no es una lista");
            MvcResult r = post(como("ana.ejemplo"), presupuestosUrl(pacienteUno), raro);
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("DATOS_INVALIDOS");
            assertThat(cantidad("presupuesto")).isZero();
        }
    }
}
