package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Consulta de presupuestos por el personal (C-19, spec presupuestos, tarea 6.2, design D7): el odontólogo y el
 * administrador ven la pieza y la cara; la respuesta a la recepción no tiene esas claves (PA-24).
 */
class ConsultaPresupuestosIntegrationTest extends IntegracionPresupuestosBase {

    @Test
    @DisplayName("El odontólogo ve pieza y cara")
    void elOdontologoVePiezaYCara() throws Exception {
        // GIVEN un presupuesto de "Paciente Tres" con "Extracción simple" en la pieza 36
        semilla();
        long presupuesto = emitirComo("ana.ejemplo", pacienteTres, item(extraccion, null, 36, null, 1));

        // WHEN ana.ejemplo consulta ese presupuesto
        MvcResult r = get(como("ana.ejemplo"), presupuestoUrl(pacienteTres, presupuesto));

        // THEN la respuesta es 200 con el ítem "Extracción simple" en la pieza 36
        assertThat(status(r)).isEqualTo(200);
        assertThat(item(cuerpo(r), "Extracción simple").path("pieza").asInt()).isEqualTo(36);
        assertThat(item(cuerpo(r), "Extracción simple").has("cara")).isTrue();
    }

    @Test
    @DisplayName("La recepción no ve pieza ni cara")
    void laRecepcionNoVePiezaNiCara() throws Exception {
        // GIVEN un presupuesto de "Paciente Uno" con "Consulta o control" en la pieza 16, cara O
        semilla();
        long presupuesto = emitirComo("ana.ejemplo", pacienteUno, item(consulta, null, 16, "O", 1));

        // WHEN recepcion lista los presupuestos de "Paciente Uno" y consulta ese presupuesto
        MvcResult lista = get(como("recepcion"), presupuestosUrl(pacienteUno));
        MvcResult uno = get(como("recepcion"), presupuestoUrl(pacienteUno, presupuesto));

        // THEN ambas respuestas son 200 con "Consulta o control", el importe, la cobertura y el copago
        assertThat(status(lista)).isEqualTo(200);
        assertThat(status(uno)).isEqualTo(200);
        JsonNode delaLista = cuerpo(lista).get(0);
        for (JsonNode p : new JsonNode[] {delaLista, cuerpo(uno)}) {
            JsonNode i = item(p, "Consulta o control");
            assertThat(i.path("importe").decimalValue()).isEqualByComparingTo("15000.00");
            assertThat(i.path("cobertura").decimalValue()).isEqualByComparingTo("15000.00");
            assertThat(i.path("copago").decimalValue()).isEqualByComparingTo("0.00");
        }
        // AND ninguna de las dos contiene los campos pieza ni cara: no basta con que valgan null
        for (MvcResult r : new MvcResult[] {lista, uno}) {
            assertThat(texto(r)).doesNotContain("\"pieza\"", "\"cara\"");
            assertThat(cuerpo(r).findParents("pieza")).isEmpty();
            assertThat(cuerpo(r).findParents("cara")).isEmpty();
        }
    }

    @Test
    @DisplayName("El administrador consulta los presupuestos")
    void elAdministradorConsultaLosPresupuestos() throws Exception {
        // GIVEN "Paciente Uno" tiene un presupuesto del 2026-11-20 y otro del 2026-11-24
        semilla();
        ahoraEs("2026-11-20T15:00:00Z");
        long viejo = emitirComo("ana.ejemplo", pacienteUno, item(consulta, null, 16, "O", 1));
        ahoraEs("2026-11-24T15:00:00Z");
        long nuevo = emitirComo("ana.ejemplo", pacienteUno, item(limpieza, 1));

        // WHEN admin lista los presupuestos de "Paciente Uno"
        MvcResult r = get(como("admin"), presupuestosUrl(pacienteUno));

        // THEN la respuesta es 200 con el del 2026-11-24 primero
        assertThat(status(r)).isEqualTo(200);
        JsonNode lista = cuerpo(r);
        assertThat(lista).hasSize(2);
        assertThat(lista.get(0).path("id").asLong()).isEqualTo(nuevo);
        assertThat(lista.get(0).path("fecha").asText()).isEqualTo("2026-11-24");
        assertThat(lista.get(1).path("id").asLong()).isEqualTo(viejo);
        assertThat(lista.get(1).path("fecha").asText()).isEqualTo("2026-11-20");
        // AND el administrador sí ve pieza y cara
        assertThat(item(lista.get(1), "Consulta o control").path("pieza").asInt()).isEqualTo(16);
    }

    @Test
    @DisplayName("Un presupuesto de otro paciente o inexistente es 404 para el personal")
    void presupuestoDeOtroPacienteOInexistente() throws Exception {
        semilla();
        long presupuesto = emitirComo("ana.ejemplo", pacienteUno, item(consulta, 1));
        for (String usuario : new String[] {"admin", "recepcion", "ana.ejemplo"}) {
            MvcResult otro = get(como(usuario), presupuestoUrl(pacienteDos, presupuesto));
            assertThat(status(otro)).isEqualTo(404);
            assertThat(codigoDeError(otro)).isEqualTo("RECURSO_NO_ENCONTRADO");
            assertThat(status(get(como(usuario), presupuestoUrl(pacienteUno, 999999L)))).isEqualTo(404);
        }
        assertThat(status(get(como("admin"), presupuestosUrl(999999L)))).isEqualTo(404);
    }
}
