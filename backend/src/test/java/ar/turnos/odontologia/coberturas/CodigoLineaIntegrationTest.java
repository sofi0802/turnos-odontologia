package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Código del nomenclador de cada línea (C-20, spec liquidaciones-obras-sociales, tarea 5.1, PA-38 y design D6). Se agrega
 * el código ficticio {@code N-0102} a "Consulta o control", que "Plan Base" cubre al 80 %. Reloj: 2026-11-24 a las
 * 12:00 en Buenos Aires. Solo datos ficticios.
 */
class CodigoLineaIntegrationTest extends IntegracionLiquidacionesBase {

    private long n0102;
    private long turnoConsulta;

    @BeforeEach
    void dosCodigosParaLaConsulta() {
        semilla();
        n0102 = codigo("N-0102", "Consulta con diagnóstico", consulta);
        cobertura(planBase, n0102, 80);
        turnoConsulta = atendido(pacienteUno, consulta, "2026-10-06T10:00");
    }

    private static List<String> codigosDisponibles(JsonNode linea) {
        List<String> codigos = new ArrayList<>();
        linea.path("codigosDisponibles").forEach(c -> codigos.add(c.path("codigo").asText()));
        return codigos;
    }

    @Test
    @DisplayName("Prestación con varios códigos")
    void prestacionConVariosCodigos() throws Exception {
        // GIVEN "Consulta o control" tiene además el código ficticio N-0102, que "Plan Base" cubre al 80 %
        // AND "Paciente Uno" tiene un turno ATENDIDO de "Consulta o control" el 2026-10-06

        // WHEN admin genera la liquidación de "Obra Social Ejemplo" para 2026-10
        JsonNode liquidacion = generada(obraSocialEjemplo, "2026-10");

        // THEN la línea de ese turno no tiene código, porcentaje ni monto, informa los códigos N-0101 y N-0102 para elegir y no suma al total solicitado
        JsonNode linea = lineaDe(liquidacion, "Consulta o control");
        assertThat(linea.path("codigo").isNull()).isTrue();
        assertThat(linea.path("porcentaje").isNull()).isTrue();
        assertThat(linea.path("montoSolicitado").isNull()).isTrue();
        assertThat(linea.path("codigoElegido").asBoolean()).isFalse();
        assertThat(codigosDisponibles(linea)).containsExactly("N-0101", "N-0102");
        assertThat(numero(liquidacion.path("totalSolicitado"))).isEqualByComparingTo("0.00");
        assertThat(totalSolicitadoGuardado(liquidacion.path("id").asLong())).isEqualByComparingTo("0.00");
        assertThat(numero(linea.path("precio"))).isEqualByComparingTo("15000.00");
    }

    @Test
    @DisplayName("Elegir el código de una línea")
    void elegirElCodigoDeUnaLinea() throws Exception {
        // GIVEN la línea anterior sin código en una liquidación en BORRADOR
        JsonNode liquidacion = generada(obraSocialEjemplo, "2026-10");
        long id = liquidacion.path("id").asLong();
        long linea = lineaDe(liquidacion, "Consulta o control").path("id").asLong();

        // WHEN admin elige N-0102 para esa línea
        MvcResult r = put(como("admin"), urlLinea(id, linea) + "/codigo", codigoSolicitud(n0102));

        // THEN la respuesta es 200, la línea tiene código N-0102, porcentaje 80 y monto 12000.00, y el total solicitado suma 12000.00
        assertThat(status(r)).isEqualTo(200);
        JsonNode detalle = cuerpo(r);
        JsonNode elegida = lineaDe(detalle, "Consulta o control");
        assertThat(elegida.path("codigo").asText()).isEqualTo("N-0102");
        assertThat(elegida.path("porcentaje").asInt()).isEqualTo(80);
        assertThat(numero(elegida.path("montoSolicitado"))).isEqualByComparingTo("12000.00");
        assertThat(elegida.path("codigoElegido").asBoolean()).isTrue();
        assertThat(numero(detalle.path("totalSolicitado"))).isEqualByComparingTo("12000.00");
        assertThat(totalSolicitadoGuardado(id)).isEqualByComparingTo("12000.00");
        assertThat(sumaDeLasLineas(id)).isEqualByComparingTo("12000.00");
    }

    @Test
    @DisplayName("Código que no es de la prestación")
    void codigoQueNoEsDeLaPrestacion() throws Exception {
        // GIVEN la línea anterior sin código
        JsonNode liquidacion = generada(obraSocialEjemplo, "2026-10");
        long id = liquidacion.path("id").asLong();
        long linea = lineaDe(liquidacion, "Consulta o control").path("id").asLong();

        // WHEN admin elige N-0301 para esa línea
        MvcResult r = put(como("admin"), urlLinea(id, linea) + "/codigo", codigoSolicitud(n0301));

        // THEN la respuesta es 422 con el código CODIGO_NOMENCLADOR_INVALIDO y la línea sigue sin código
        assertThat(status(r)).isEqualTo(422);
        assertThat(codigoDeError(r)).isEqualTo("CODIGO_NOMENCLADOR_INVALIDO");
        assertThat(jdbc.queryForObject("SELECT nomenclador_id FROM liquidacion_linea WHERE id = ?", Long.class, linea))
                .isNull();
        assertThat(totalSolicitadoGuardado(id)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Un código que el plan cubre con 0 %, inexistente o inactivo tampoco se puede elegir")
    void codigoSinCoberturaInexistenteOInactivo() throws Exception {
        long id = generada(obraSocialEjemplo, "2026-10").path("id").asLong();
        long linea = jdbc.queryForObject("SELECT id FROM liquidacion_linea WHERE liquidacion_id = ?", Long.class, id);
        cobertura(planBase, n0102, 0);

        // el plan ya no cubre N-0102
        assertThat(codigoDeError(put(como("admin"), urlLinea(id, linea) + "/codigo", codigoSolicitud(n0102))))
                .isEqualTo("CODIGO_NOMENCLADOR_INVALIDO");
        // un id que no existe
        assertThat(codigoDeError(put(como("admin"), urlLinea(id, linea) + "/codigo", codigoSolicitud(999999L))))
                .isEqualTo("CODIGO_NOMENCLADOR_INVALIDO");
        // un código inactivo
        cobertura(planBase, n0102, 80);
        jdbc.update("UPDATE nomenclador SET activo = false WHERE id = ?", n0102);
        assertThat(status(put(como("admin"), urlLinea(id, linea) + "/codigo", codigoSolicitud(n0102)))).isEqualTo(422);
        // sin cuerpo
        assertThat(status(put(como("admin"), urlLinea(id, linea) + "/codigo", java.util.Map.of()))).isEqualTo(422);
    }

    @Test
    @DisplayName("El código elegido se puede cambiar por otro mientras sea borrador")
    void cambiarElCodigoElegido() throws Exception {
        JsonNode liquidacion = generada(obraSocialEjemplo, "2026-10");
        long id = liquidacion.path("id").asLong();
        long linea = lineaDe(liquidacion, "Consulta o control").path("id").asLong();
        put(como("admin"), urlLinea(id, linea) + "/codigo", codigoSolicitud(n0102));

        MvcResult r = put(como("admin"), urlLinea(id, linea) + "/codigo", codigoSolicitud(n0101));

        assertThat(status(r)).isEqualTo(200);
        JsonNode cambiada = lineaDe(cuerpo(r), "Consulta o control");
        assertThat(cambiada.path("codigo").asText()).isEqualTo("N-0101");
        assertThat(numero(cambiada.path("montoSolicitado"))).isEqualByComparingTo("15000.00");
    }

    @Test
    @DisplayName("Una línea con código automático no cambia de código")
    void codigoAutomaticoNoCambia() throws Exception {
        // GIVEN una limpieza, que tiene un solo código activo (N-0201): lo toma sola
        atendido(pacienteUno, limpieza, "2026-10-20T10:00");
        JsonNode liquidacion = generada(obraSocialEjemplo, "2026-10");
        long id = liquidacion.path("id").asLong();
        JsonNode deLimpieza = lineaDe(liquidacion, "Limpieza (profilaxis)");
        assertThat(deLimpieza.path("codigo").asText()).isEqualTo("N-0201");
        assertThat(deLimpieza.path("codigoElegido").asBoolean()).isFalse();

        // WHEN admin intenta elegir otro código para esa línea
        MvcResult r = put(como("admin"), urlLinea(id, deLimpieza.path("id").asLong()) + "/codigo",
                codigoSolicitud(n0101));

        // THEN la respuesta es 422 CODIGO_NOMENCLADOR_INVALIDO y la línea no cambia
        assertThat(status(r)).isEqualTo(422);
        assertThat(codigoDeError(r)).isEqualTo("CODIGO_NOMENCLADOR_INVALIDO");
        assertThat(jdbc.queryForObject("SELECT codigo_nomenclador FROM liquidacion_linea WHERE id = ?", String.class,
                deLimpieza.path("id").asLong())).isEqualTo("N-0201");
        // AND tampoco se "elige" el mismo código automático: seguiría sin ser una elección del administrador
        MvcResult mismo = put(como("admin"), urlLinea(id, deLimpieza.path("id").asLong()) + "/codigo",
                codigoSolicitud(n0201));
        assertThat(status(mismo)).isEqualTo(422);
        assertThat(jdbc.queryForObject("SELECT codigo_elegido FROM liquidacion_linea WHERE id = ?", Boolean.class,
                deLimpieza.path("id").asLong())).isFalse();
    }

    @Test
    @DisplayName("Una línea de otra liquidación o una liquidación inexistente responden 404")
    void lineaAjenaOLiquidacionInexistente() throws Exception {
        JsonNode octubre = generada(obraSocialEjemplo, "2026-10");
        long linea = lineaDe(octubre, "Consulta o control").path("id").asLong();
        long otra = generada(prepaga, "2026-10").path("id").asLong();

        MvcResult ajena = put(como("admin"), urlLinea(otra, linea) + "/codigo", codigoSolicitud(n0102));
        MvcResult inexistente = put(como("admin"), urlLinea(999999L, linea) + "/codigo", codigoSolicitud(n0102));

        assertThat(status(ajena)).isEqualTo(404);
        assertThat(codigoDeError(ajena)).isEqualTo("RECURSO_NO_ENCONTRADO");
        assertThat(status(inexistente)).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT nomenclador_id FROM liquidacion_linea WHERE id = ?", Long.class, linea))
                .isNull();
        assertThat(turnoConsulta).isPositive();
    }
}
