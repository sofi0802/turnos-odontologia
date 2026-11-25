package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Monto solicitado de cada línea y totales (C-20, spec liquidaciones-obras-sociales, tarea 4.1, RN-LQ-03): precio por
 * porcentaje del plan dividido 100, redondeado a centavos half up. Reloj: 2026-11-24 a las 12:00 en Buenos Aires. Solo
 * datos ficticios del seed de la KB.
 */
class MontosLiquidacionIntegrationTest extends IntegracionLiquidacionesBase {

    @Test
    @DisplayName("Liquidación de \"Obra Social Ejemplo\" de octubre")
    void liquidacionDeObraSocialEjemploDeOctubre() throws Exception {
        // GIVEN "Paciente Uno" tiene turnos ATENDIDO de "Consulta o control" el 2026-10-06 y de "Limpieza (profilaxis)" el 2026-10-20
        semilla();
        atendido(pacienteUno, consulta, "2026-10-06T10:00");
        atendido(pacienteUno, limpieza, "2026-10-20T10:00");

        // WHEN admin genera la liquidación de "Obra Social Ejemplo" para 2026-10
        JsonNode liquidacion = generada(obraSocialEjemplo, "2026-10");

        // THEN la línea de "Consulta o control" tiene precio 15000.00, porcentaje 100 y monto 15000.00
        JsonNode deConsulta = lineaDe(liquidacion, "Consulta o control");
        assertThat(numero(deConsulta.path("precio"))).isEqualByComparingTo("15000.00");
        assertThat(deConsulta.path("porcentaje").asInt()).isEqualTo(100);
        assertThat(numero(deConsulta.path("montoSolicitado"))).isEqualByComparingTo("15000.00");
        assertThat(deConsulta.path("origenPrecio").asText()).isEqualTo("ARANCEL");
        // AND la de "Limpieza (profilaxis)" tiene precio 25000.00, porcentaje 50 y monto 12500.00
        JsonNode deLimpieza = lineaDe(liquidacion, "Limpieza (profilaxis)");
        assertThat(numero(deLimpieza.path("precio"))).isEqualByComparingTo("25000.00");
        assertThat(deLimpieza.path("porcentaje").asInt()).isEqualTo(50);
        assertThat(numero(deLimpieza.path("montoSolicitado"))).isEqualByComparingTo("12500.00");
        // AND el grupo "Plan Base" tiene subtotal 27500.00 y el total solicitado es 27500.00
        assertThat(numero(grupoDe(liquidacion, "Plan Base").path("subtotal"))).isEqualByComparingTo("27500.00");
        assertThat(numero(liquidacion.path("totalSolicitado"))).isEqualByComparingTo("27500.00");
        // AND la suma de las líneas leída de la base coincide con el total guardado
        long id = liquidacion.path("id").asLong();
        assertThat(sumaDeLasLineas(id)).isEqualByComparingTo("27500.00");
        assertThat(totalSolicitadoGuardado(id)).isEqualByComparingTo(sumaDeLasLineas(id));
    }

    @Test
    @DisplayName("Liquidación de \"Prepaga Prueba\" con cobertura del 80 %")
    void liquidacionDePrepagaPruebaConCoberturaDel80() throws Exception {
        // GIVEN "Paciente Tres" tiene turnos ATENDIDO de "Consulta o control" el 2026-10-07, de "Limpieza (profilaxis)" el 2026-10-14 y de "Extracción simple" el 2026-10-21
        semilla();
        atendido(pacienteTres, consulta, "2026-10-07T10:00");
        atendido(pacienteTres, limpieza, "2026-10-14T10:00");
        atendido(pacienteTres, extraccion, "2026-10-21T10:00");

        // WHEN admin genera la liquidación de "Prepaga Prueba" para 2026-10
        JsonNode liquidacion = generada(prepaga, "2026-10");

        // THEN los montos son 12000.00, 20000.00 y 24000.00, y el total solicitado es 56000.00
        assertThat(numero(lineaDe(liquidacion, "Consulta o control").path("montoSolicitado")))
                .isEqualByComparingTo("12000.00");
        assertThat(numero(lineaDe(liquidacion, "Limpieza (profilaxis)").path("montoSolicitado")))
                .isEqualByComparingTo("20000.00");
        assertThat(numero(lineaDe(liquidacion, "Extracción simple").path("montoSolicitado")))
                .isEqualByComparingTo("24000.00");
        assertThat(numero(liquidacion.path("totalSolicitado"))).isEqualByComparingTo("56000.00");
        assertThat(sumaDeLasLineas(liquidacion.path("id").asLong())).isEqualByComparingTo("56000.00");
    }

    @Test
    @DisplayName("Redondeo half up")
    void redondeoHalfUp() throws Exception {
        // GIVEN el arancel de "Limpieza (profilaxis)" es 25000.15 y "Paciente Uno" tiene un turno ATENDIDO de "Limpieza (profilaxis)" el 2026-10-20
        semilla();
        arancel(limpieza, "25000.15");
        atendido(pacienteUno, limpieza, "2026-10-20T10:00");

        // WHEN admin genera la liquidación de "Obra Social Ejemplo" para 2026-10
        JsonNode liquidacion = generada(obraSocialEjemplo, "2026-10");

        // THEN la línea tiene monto 12500.08
        assertThat(numero(lineaDe(liquidacion, "Limpieza (profilaxis)").path("montoSolicitado")))
                .isEqualByComparingTo("12500.08");
        assertThat(numero(liquidacion.path("totalSolicitado"))).isEqualByComparingTo("12500.08");
        assertThat(totalSolicitadoGuardado(liquidacion.path("id").asLong())).isEqualByComparingTo("12500.08");
    }

    @Test
    @DisplayName("Varios planes de la misma obra social")
    void variosPlanesDeLaMismaObraSocial() throws Exception {
        // GIVEN "Paciente Uno" ("Plan Base") y "Paciente Dos", afiliado a "Plan Integral" desde 2026-10-01, tienen cada uno un turno ATENDIDO de "Consulta o control" en octubre
        semilla();
        cobertura(planIntegral, n0101, 100);
        afiliacion(pacienteDos, planIntegral, "OSE-000002", "2026-10-01", null);
        atendido(pacienteUno, consulta, "2026-10-06T10:00");
        atendido(pacienteDos, consulta, "2026-10-07T10:00");

        // WHEN admin consulta la liquidación de "Obra Social Ejemplo" de 2026-10
        JsonNode liquidacion = generada(obraSocialEjemplo, "2026-10");

        // THEN el detalle tiene un grupo "Plan Base" con subtotal 15000.00, otro "Plan Integral" con subtotal 15000.00 y total 30000.00
        assertThat(liquidacion.path("grupos")).hasSize(2);
        assertThat(numero(grupoDe(liquidacion, "Plan Base").path("subtotal"))).isEqualByComparingTo("15000.00");
        assertThat(numero(grupoDe(liquidacion, "Plan Integral").path("subtotal"))).isEqualByComparingTo("15000.00");
        assertThat(numero(liquidacion.path("totalSolicitado"))).isEqualByComparingTo("30000.00");
        assertThat(grupoDe(liquidacion, "Plan Integral").path("lineas").get(0).path("paciente").path("nombre").asText())
                .isEqualTo("Paciente Dos");
    }
}
