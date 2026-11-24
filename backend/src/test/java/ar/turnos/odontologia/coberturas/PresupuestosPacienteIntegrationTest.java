package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * El paciente consulta sus presupuestos (C-19, spec presupuestos, tarea 6.3): la sesión identifica al paciente, nunca
 * un id del pedido, y no puede cambiar nada. {@code SecurityConfig} no cambia.
 */
class PresupuestosPacienteIntegrationTest extends IntegracionPresupuestosBase {

    private static final String PROPIOS = "/api/v1/paciente/presupuestos";

    @Test
    @DisplayName("El paciente ve los suyos")
    void elPacienteVeLosSuyos() throws Exception {
        // GIVEN "Paciente Uno" tiene un presupuesto y "Paciente Dos" tiene otro
        semilla();
        long deUno = emitirComo("ana.ejemplo", pacienteUno, item(consulta, null, 16, "O", 1), item(limpieza, 1));
        emitirComo("ana.ejemplo", pacienteDos, item(consulta, 1));

        // WHEN paciente.uno lista sus presupuestos
        MvcResult r = get(como("paciente.uno"), PROPIOS);

        // THEN la respuesta es 200 con solo el presupuesto de "Paciente Uno", con sus ítems y totales
        assertThat(status(r)).isEqualTo(200);
        JsonNode lista = cuerpo(r);
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).path("id").asLong()).isEqualTo(deUno);
        assertThat(lista.get(0).path("items")).hasSize(2);
        assertThat(lista.get(0).path("totales").path("precio").decimalValue()).isEqualByComparingTo("40000.00");
        assertThat(lista.get(0).path("totales").path("cobertura").decimalValue()).isEqualByComparingTo("27500.00");
        assertThat(lista.get(0).path("totales").path("copago").decimalValue()).isEqualByComparingTo("12500.00");
        // AND ve la pieza y la cara de sus propios presupuestos (decisión 7)
        assertThat(item(lista.get(0), "Consulta o control").path("pieza").asInt()).isEqualTo(16);
        assertThat(item(lista.get(0), "Consulta o control").path("cara").asText()).isEqualTo("O");
        // AND el detalle propio también responde 200
        MvcResult detalle = get(como("paciente.uno"), PROPIOS + "/" + deUno);
        assertThat(status(detalle)).isEqualTo(200);
        assertThat(cuerpo(detalle).path("id").asLong()).isEqualTo(deUno);
    }

    @Test
    @DisplayName("Presupuesto de otro paciente")
    void presupuestoDeOtroPaciente() throws Exception {
        // GIVEN "Paciente Dos" tiene un presupuesto
        semilla();
        long deDos = emitirComo("ana.ejemplo", pacienteDos, item(consulta, 1));

        // WHEN paciente.uno consulta ese presupuesto por su id
        MvcResult r = get(como("paciente.uno"), PROPIOS + "/" + deDos);

        // THEN la respuesta es 404 con el código RECURSO_NO_ENCONTRADO
        assertThat(status(r)).isEqualTo(404);
        assertThat(codigoDeError(r)).isEqualTo("RECURSO_NO_ENCONTRADO");
        assertThat(status(get(como("paciente.uno"), PROPIOS + "/999999"))).isEqualTo(404);
    }

    @Test
    @DisplayName("El paciente no acepta su presupuesto")
    void elPacienteNoAceptaSuPresupuesto() throws Exception {
        // GIVEN "Paciente Uno" tiene un presupuesto PENDIENTE
        semilla();
        long presupuesto = emitirComo("ana.ejemplo", pacienteUno, item(consulta, 1));

        // WHEN paciente.uno pide aceptarlo por la ruta del personal
        MvcResult r = post(como("paciente.uno"), presupuestoUrl(pacienteUno, presupuesto) + "/aceptar", null);

        // THEN la respuesta es 403 con el código ACCESO_DENEGADO
        assertThat(status(r)).isEqualTo(403);
        assertThat(codigoDeError(r)).isEqualTo("ACCESO_DENEGADO");
        // AND el presupuesto sigue PENDIENTE
        assertThat(estadoGuardado(presupuesto)).isEqualTo("PENDIENTE");
        // AND tampoco hay una ruta propia para cambiarlo
        assertThat(status(post(como("paciente.uno"), PROPIOS + "/" + presupuesto + "/aceptar", null)))
                .isGreaterThanOrEqualTo(400);
        assertThat(estadoGuardado(presupuesto)).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("Un id en el pedido no cambia de paciente")
    void unIdEnElPedidoNoCambiaDePaciente() throws Exception {
        semilla();
        long deDos = emitirComo("ana.ejemplo", pacienteDos, item(consulta, 1));
        MvcResult r = get(como("paciente.uno"), PROPIOS + "?pacienteId=" + pacienteDos);
        assertThat(status(r)).isEqualTo(200);
        assertThat(cuerpo(r)).isEmpty();
        assertThat(status(get(como("paciente.uno"), presupuestosUrl(pacienteDos)))).isEqualTo(403);
        assertThat(status(get(como("paciente.uno"), presupuestoUrl(pacienteDos, deDos)))).isEqualTo(403);
    }
}
