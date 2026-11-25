package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Turnos sin arancel (C-20, spec liquidaciones-obras-sociales, tarea 4.3, RN-LQ-03 y PA-40): un turno que entraría pero
 * cuya prestación no tiene precio no genera línea y la respuesta solo informa cuántos son. El escenario de cargar el
 * arancel y regenerar está en {@code RegenerarLiquidacionIntegrationTest}. Reloj: 2026-11-24 a las 12:00 en Buenos
 * Aires. Solo datos ficticios.
 */
@ExtendWith(OutputCaptureExtension.class)
class TurnosSinArancelIntegrationTest extends IntegracionLiquidacionesBase {

    @Test
    @DisplayName("Prestación sin arancel")
    void prestacionSinArancel(CapturedOutput salida) throws Exception {
        // GIVEN "Limpieza (profilaxis)" no tiene arancel y "Paciente Uno" tiene un turno ATENDIDO de "Limpieza (profilaxis)" el 2026-10-20
        semilla();
        jdbc.update("DELETE FROM arancel WHERE prestacion_id = ?", limpieza);
        long turno = atendido(pacienteUno, limpieza, "2026-10-20T10:00");

        // WHEN admin genera la liquidación de "Obra Social Ejemplo" para 2026-10
        JsonNode liquidacion = generada(obraSocialEjemplo, "2026-10");

        // THEN la liquidación no tiene ese turno y la respuesta informa 1 turno sin arancel
        assertThat(idsDeTurnos(liquidacion)).doesNotContain(turno);
        assertThat(lineasDe(liquidacion)).isEmpty();
        assertThat(liquidacion.path("turnosSinArancel").asInt()).isEqualTo(1);
        // AND no identifica el turno ni la prestación en los logs
        assertThat(salida.getAll()).doesNotContain("Limpieza (profilaxis)", "99000001", "OSE-000001");
    }

    @Test
    @DisplayName("Con arancel la respuesta informa cero turnos sin arancel")
    void conArancelNoHayTurnosSinArancel() throws Exception {
        // GIVEN todas las prestaciones tienen arancel
        semilla();
        atendido(pacienteUno, limpieza, "2026-10-20T10:00");

        // WHEN admin genera la liquidación
        JsonNode liquidacion = generada(obraSocialEjemplo, "2026-10");

        // THEN la respuesta informa 0 y la línea está
        assertThat(liquidacion.path("turnosSinArancel").asInt(-1)).isZero();
        assertThat(lineasDe(liquidacion)).hasSize(1);
    }

    @Test
    @DisplayName("Dos prestaciones sin arancel se cuentan por turno")
    void seCuentanPorTurno() throws Exception {
        // GIVEN dos turnos de "Limpieza (profilaxis)" y uno de "Consulta o control" con solo la consulta con arancel
        semilla();
        jdbc.update("DELETE FROM arancel WHERE prestacion_id = ?", limpieza);
        atendido(pacienteUno, limpieza, "2026-10-20T10:00");
        atendido(pacienteUno, limpieza, "2026-10-21T10:00");
        atendido(pacienteUno, consulta, "2026-10-06T10:00");

        // WHEN admin genera la liquidación
        JsonNode liquidacion = generada(obraSocialEjemplo, "2026-10");

        // THEN hay una línea y 2 turnos sin arancel
        assertThat(lineasDe(liquidacion)).hasSize(1);
        assertThat(liquidacion.path("turnosSinArancel").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("Un turno sin cobertura no cuenta como sin arancel")
    void sinCoberturaNoCuenta() throws Exception {
        // GIVEN "Extracción simple" sin arancel y sin cobertura en "Plan Base"
        semilla();
        jdbc.update("DELETE FROM arancel WHERE prestacion_id = ?", extraccion);
        atendido(pacienteUno, extraccion, "2026-10-09T10:00");

        // WHEN admin genera la liquidación
        JsonNode liquidacion = generada(obraSocialEjemplo, "2026-10");

        // THEN el turno no cumplía las condiciones para entrar: 0 turnos sin arancel
        assertThat(liquidacion.path("turnosSinArancel").asInt(-1)).isZero();
    }

    @Test
    @DisplayName("El turno sin arancel queda libre para una liquidación posterior")
    void quedaLibre() throws Exception {
        // GIVEN un turno de octubre sin arancel que no entró en la liquidación de 2026-10
        semilla();
        jdbc.update("DELETE FROM arancel WHERE prestacion_id = ?", limpieza);
        long turno = atendido(pacienteUno, limpieza, "2026-10-20T10:00");
        generada(obraSocialEjemplo, "2026-10");

        // WHEN se carga el arancel y admin genera la liquidación de 2026-11 con hoy 2026-12-02
        arancel(limpieza, "25000.00");
        ahoraEs("2026-12-02T15:00:00Z");
        JsonNode noviembre = generada(obraSocialEjemplo, "2026-11");

        // THEN el turno entra en la de 2026-11 con su fecha real
        assertThat(idsDeTurnos(noviembre)).containsExactly(turno);
        assertThat(lineaDe(noviembre, "Limpieza (profilaxis)").path("fecha").asText()).isEqualTo("2026-10-20");
    }
}
