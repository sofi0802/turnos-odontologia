package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Quitar líneas de una liquidación en borrador (C-20, spec liquidaciones-obras-sociales, tarea 5.3, RN-LQ-04 y design
 * D7). Reloj: 2026-11-24 a las 12:00 en Buenos Aires. Solo datos ficticios del seed de la KB.
 */
class QuitarLineaIntegrationTest extends IntegracionLiquidacionesBase {

    private long turnoConsulta;
    private long turnoLimpieza;
    private long liquidacion;
    private long lineaConsulta;
    private long lineaLimpieza;

    @BeforeEach
    void borradorConDosLineas() throws Exception {
        // GIVEN la liquidación de "Obra Social Ejemplo" de 2026-10 en BORRADOR con la consulta (15000.00) y la limpieza (12500.00) de "Paciente Uno"
        semilla();
        turnoConsulta = atendido(pacienteUno, consulta, "2026-10-06T10:00");
        turnoLimpieza = atendido(pacienteUno, limpieza, "2026-10-20T10:00");
        JsonNode generada = generada(obraSocialEjemplo, "2026-10");
        liquidacion = generada.path("id").asLong();
        lineaConsulta = lineaDe(generada, "Consulta o control").path("id").asLong();
        lineaLimpieza = lineaDe(generada, "Limpieza (profilaxis)").path("id").asLong();
    }

    @Test
    @DisplayName("Quitar una línea")
    void quitarUnaLinea() throws Exception {
        // WHEN admin quita la línea de la limpieza
        MvcResult r = delete(como("admin"), urlLinea(liquidacion, lineaLimpieza));

        // THEN la respuesta es 204 y la liquidación tiene una línea y total solicitado 15000.00
        assertThat(status(r)).isEqualTo(204);
        assertThat(lineas(liquidacion)).isEqualTo(1);
        assertThat(totalSolicitadoGuardado(liquidacion)).isEqualByComparingTo("15000.00");
        assertThat(turnosLiquidados(liquidacion)).containsExactly(turnoConsulta);
        // AND el turno quedó registrado como quitado de esa liquidación
        assertThat(jdbc.queryForList("SELECT turno_id FROM liquidacion_turno_quitado WHERE liquidacion_id = ?",
                Long.class, liquidacion)).containsExactly(turnoLimpieza);
    }

    @Test
    @DisplayName("El turno quitado no vuelve al regenerar")
    void elTurnoQuitadoNoVuelveAlRegenerar() throws Exception {
        // GIVEN la línea de la limpieza se quitó de la liquidación de 2026-10
        delete(como("admin"), urlLinea(liquidacion, lineaLimpieza));

        // WHEN admin regenera la liquidación de 2026-10
        JsonNode regenerada = cuerpo(regenerar(liquidacion));

        // THEN la limpieza no vuelve a esa liquidación
        assertThat(idsDeTurnos(regenerada)).containsExactly(turnoConsulta);
        assertThat(numero(regenerada.path("totalSolicitado"))).isEqualByComparingTo("15000.00");
        assertThat(lineas(liquidacion)).isEqualTo(1);
    }

    @Test
    @DisplayName("El turno quitado entra en el mes siguiente")
    void elTurnoQuitadoEntraEnElMesSiguiente() throws Exception {
        // GIVEN la línea de la limpieza del 2026-10-20 se quitó de la liquidación de 2026-10
        delete(como("admin"), urlLinea(liquidacion, lineaLimpieza));

        // WHEN admin genera la liquidación de "Obra Social Ejemplo" para 2026-11 con hoy 2026-12-02
        ahoraEs("2026-12-02T15:00:00Z");
        JsonNode noviembre = generada(obraSocialEjemplo, "2026-11");

        // THEN la de 2026-11 tiene la limpieza con fecha 2026-10-20
        assertThat(idsDeTurnos(noviembre)).containsExactly(turnoLimpieza);
        assertThat(lineaDe(noviembre, "Limpieza (profilaxis)").path("fecha").asText()).isEqualTo("2026-10-20");
        // AND la de 2026-10 sigue sin ella
        assertThat(turnosLiquidados(liquidacion)).containsExactly(turnoConsulta);
    }

    @Test
    @DisplayName("Quitar una línea de una liquidación presentada")
    void quitarUnaLineaDeUnaLiquidacionPresentada() throws Exception {
        // GIVEN la liquidación de 2026-10 está PRESENTADA
        jdbc.update("UPDATE liquidacion SET estado = 'PRESENTADA', presentada_en = now(), presentada_por = 'admin' "
                + "WHERE id = ?", liquidacion);

        // WHEN admin quita una de sus líneas
        MvcResult r = delete(como("admin"), urlLinea(liquidacion, lineaLimpieza));

        // THEN la respuesta es 409 con el código LIQUIDACION_PRESENTADA y la línea sigue
        assertThat(status(r)).isEqualTo(409);
        assertThat(codigoDeError(r)).isEqualTo("LIQUIDACION_PRESENTADA");
        assertThat(lineas(liquidacion)).isEqualTo(2);
        assertThat(cantidad("liquidacion_turno_quitado")).isZero();
    }

    @Test
    @DisplayName("Quitar una línea de una liquidación cerrada, de otra liquidación o inexistente")
    void quitarEnOtrosCasos() throws Exception {
        long otra = generada(prepaga, "2026-10").path("id").asLong();

        // una línea que no es de esa liquidación
        MvcResult ajena = delete(como("admin"), urlLinea(otra, lineaLimpieza));
        assertThat(status(ajena)).isEqualTo(404);
        assertThat(codigoDeError(ajena)).isEqualTo("RECURSO_NO_ENCONTRADO");
        // una liquidación que no existe
        assertThat(status(delete(como("admin"), urlLinea(999999L, lineaLimpieza)))).isEqualTo(404);
        // una cerrada
        jdbc.update("UPDATE liquidacion SET estado = 'PRESENTADA', presentada_en = now(), presentada_por = 'admin' "
                + "WHERE id = ?", liquidacion);
        jdbc.update("UPDATE liquidacion SET estado = 'CERRADA', cerrada_en = now(), cerrada_por = 'admin' WHERE id = ?",
                liquidacion);
        MvcResult cerrada = delete(como("admin"), urlLinea(liquidacion, lineaLimpieza));
        assertThat(status(cerrada)).isEqualTo(409);
        assertThat(codigoDeError(cerrada)).isEqualTo("LIQUIDACION_CERRADA");
        assertThat(lineas(liquidacion)).isEqualTo(2);
        assertThat(lineaConsulta).isPositive();
    }

    @Test
    @DisplayName("Quitar la misma línea dos veces")
    void quitarDosVeces() throws Exception {
        assertThat(status(delete(como("admin"), urlLinea(liquidacion, lineaLimpieza)))).isEqualTo(204);

        MvcResult otraVez = delete(como("admin"), urlLinea(liquidacion, lineaLimpieza));

        assertThat(status(otraVez)).isEqualTo(404);
        assertThat(cantidad("liquidacion_turno_quitado")).isEqualTo(1);
    }
}
