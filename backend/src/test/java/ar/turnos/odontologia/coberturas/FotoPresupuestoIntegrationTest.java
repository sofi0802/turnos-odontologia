package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * El presupuesto es una foto del cálculo (C-19, spec presupuestos, tarea 5.1, DD-15, RN-PR-04): cambiar después los
 * aranceles, la cobertura o la afiliación no cambia un presupuesto emitido, y la base no deja modificar un ítem.
 */
class FotoPresupuestoIntegrationTest extends IntegracionPresupuestosBase {

    private long emitirLimpiezaDeUno() throws Exception {
        return emitirComo("ana.ejemplo", pacienteUno, item(limpieza, 1));
    }

    @Test
    @DisplayName("Cambia el precio después de emitir")
    void cambiaElPrecio() throws Exception {
        // GIVEN ana.ejemplo emitió para "Paciente Uno" un presupuesto con "Limpieza (profilaxis)" a 25000.00
        semilla();
        long presupuesto = emitirLimpiezaDeUno();

        // WHEN admin cambia el precio de "Limpieza (profilaxis)" a 30000.00 y ana.ejemplo consulta el presupuesto
        assertThat(status(put(como("admin"), arancelUrl(limpieza), precioSolicitud(new BigDecimal("30000.00")))))
                .isEqualTo(200);
        JsonNode p = presupuesto(como("ana.ejemplo"), pacienteUno, presupuesto);

        // THEN el ítem sigue con precio unitario 25000.00, cobertura 12500.00 y copago 12500.00
        JsonNode i = item(p, "Limpieza (profilaxis)");
        assertThat(i.path("precioUnitario").decimalValue()).isEqualByComparingTo("25000.00");
        assertThat(i.path("cobertura").decimalValue()).isEqualByComparingTo("12500.00");
        assertThat(i.path("copago").decimalValue()).isEqualByComparingTo("12500.00");
        assertThat(p.path("totales").path("precio").decimalValue()).isEqualByComparingTo("25000.00");
    }

    @Test
    @DisplayName("Cambia la cobertura del plan después de emitir")
    void cambiaLaCoberturaDelPlan() throws Exception {
        // GIVEN ana.ejemplo emitió para "Paciente Uno" un presupuesto con "Limpieza (profilaxis)" al 50 %
        semilla();
        long presupuesto = emitirLimpiezaDeUno();

        // WHEN admin carga para "Plan Base" el código N-0201 al 100 % y ana.ejemplo consulta el presupuesto
        MvcResult cambio = put(como("admin"), coberturaPlanUrl(obraSocialEjemplo, planBase) + "/" + n0201,
                porcentajeSolicitud(100));
        assertThat(status(cambio)).isEqualTo(200);
        JsonNode p = presupuesto(como("ana.ejemplo"), pacienteUno, presupuesto);

        // THEN el ítem sigue con porcentaje 50 y copago 12500.00
        JsonNode i = item(p, "Limpieza (profilaxis)");
        assertThat(i.path("porcentajeCobertura").asInt()).isEqualTo(50);
        assertThat(i.path("copago").decimalValue()).isEqualByComparingTo("12500.00");
    }

    @Test
    @DisplayName("Se corrige la afiliación después de emitir")
    void seCorrigeLaAfiliacion() throws Exception {
        // GIVEN ana.ejemplo emitió para "Paciente Uno" un presupuesto con "Plan Base"
        semilla();
        long presupuesto = emitirLimpiezaDeUno();
        JsonNode antes = presupuesto(como("ana.ejemplo"), pacienteUno, presupuesto);
        long afiliacion = jdbc.queryForObject("SELECT id FROM afiliacion_paciente WHERE paciente_id = ?", Long.class,
                pacienteUno);

        // WHEN recepcion corrige la afiliación de "Paciente Uno" a "Plan Integral" y ana.ejemplo consulta el presupuesto
        Map<String, Object> correccion = new LinkedHashMap<>();
        correccion.put("planId", planIntegral);
        correccion.put("numeroAfiliado", "OSE-000001");
        correccion.put("vigenteDesde", "2026-01-01");
        MvcResult r = put(como("recepcion"), afiliaciones(pacienteUno) + "/" + afiliacion, correccion);
        assertThat(status(r)).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT plan_id FROM afiliacion_paciente WHERE id = ?", Long.class, afiliacion))
                .isEqualTo(planIntegral);
        JsonNode despues = presupuesto(como("ana.ejemplo"), pacienteUno, presupuesto);

        // THEN el presupuesto sigue mostrando "Plan Base" y los mismos importes
        assertThat(despues.path("cobertura").path("plan").asText()).isEqualTo("Plan Base");
        assertThat(despues.path("cobertura").path("afiliacionId").asLong()).isEqualTo(afiliacion);
        assertThat(despues.path("totales")).isEqualTo(antes.path("totales"));
        assertThat(despues.path("items")).isEqualTo(antes.path("items"));
    }

    @Test
    @DisplayName("La base rechaza modificar un ítem emitido")
    void laBaseRechazaModificarUnItem() throws Exception {
        // GIVEN un presupuesto emitido para "Paciente Uno"
        semilla();
        emitirLimpiezaDeUno();
        long item = jdbc.queryForObject("SELECT id FROM presupuesto_item", Long.class);

        // WHEN se intenta cambiar el precio unitario de uno de sus ítems directamente en la base
        assertThatThrownBy(() -> jdbc.update("UPDATE presupuesto_item SET precio_unitario = 1.00 WHERE id = ?", item))
                .hasMessageContaining("solo agregado");

        // THEN la base rechaza el cambio y el ítem queda igual
        assertThat(jdbc.queryForObject("SELECT precio_unitario FROM presupuesto_item WHERE id = ?", BigDecimal.class,
                item)).isEqualByComparingTo("25000.00");
    }
}
