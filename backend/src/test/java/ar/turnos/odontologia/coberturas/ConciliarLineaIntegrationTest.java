package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Registrar lo cobrado de cada línea (C-20, spec conciliacion-liquidaciones, tarea 7.1, RN-LQ-06). La liquidación de
 * "Obra Social Ejemplo" de {@code 2026-10} está {@code PRESENTADA} con dos líneas de "Paciente Uno": "Consulta o
 * control" ({@code N-0101}, 15000.00) y "Limpieza (profilaxis)" ({@code N-0201}, 12500.00). Reloj: 2026-11-24 a las
 * 12:00 en Buenos Aires. Solo datos ficticios.
 */
class ConciliarLineaIntegrationTest extends IntegracionLiquidacionesBase {

    private long liquidacion;
    private long lineaConsulta;
    private long lineaLimpieza;

    @BeforeEach
    void liquidacionPresentada() throws Exception {
        semilla();
        atendido(pacienteUno, consulta, "2026-10-06T10:00");
        atendido(pacienteUno, limpieza, "2026-10-20T10:00");
        JsonNode generada = generada(obraSocialEjemplo, "2026-10");
        liquidacion = generada.path("id").asLong();
        lineaConsulta = lineaDe(generada, "Consulta o control").path("id").asLong();
        lineaLimpieza = lineaDe(generada, "Limpieza (profilaxis)").path("id").asLong();
        presentar(liquidacion);
    }

    private static BigDecimal monto(String valor) {
        return new BigDecimal(valor);
    }

    private Map<String, Object> estadoDeLaLinea(long linea) {
        return jdbc.queryForMap("SELECT monto_cobrado, resultado, motivo_rechazo, conciliada_por FROM liquidacion_linea "
                + "WHERE id = ?", linea);
    }

    private void assertSinConciliar(long linea) {
        Map<String, Object> fila = estadoDeLaLinea(linea);
        assertThat(fila.get("monto_cobrado")).isNull();
        assertThat(fila.get("resultado")).isNull();
        assertThat(fila.get("motivo_rechazo")).isNull();
        assertThat(fila.get("conciliada_por")).isNull();
    }

    // --- Registrar lo cobrado de cada línea ---

    @Test
    @DisplayName("Línea cobrada completa")
    void lineaCobradaCompleta() throws Exception {
        // WHEN admin registra 15000.00 cobrados en la línea de "Consulta o control"
        MvcResult r = conciliar(liquidacion, lineaConsulta, monto("15000.00"), null);

        // THEN la respuesta es 200 y la línea tiene monto cobrado 15000.00 y resultado COBRADA
        assertThat(status(r)).isEqualTo(200);
        JsonNode linea = lineaDe(cuerpo(r), "Consulta o control");
        assertThat(numero(linea.path("montoCobrado"))).isEqualByComparingTo("15000.00");
        assertThat(linea.path("resultado").asText()).isEqualTo("COBRADA");
        assertThat(linea.path("motivoRechazo").isNull()).isTrue();
        // AND se guardó quién y cuándo la registró
        assertThat(linea.path("conciliadaPor").asText()).isEqualTo("admin");
        assertThat(linea.path("conciliadaEn").asText()).startsWith("2026-11-24T15:00");
        assertThat(estadoDeLaLinea(lineaConsulta).get("conciliada_por")).isEqualTo("admin");
        // AND el monto solicitado no cambia
        assertThat(numero(linea.path("montoSolicitado"))).isEqualByComparingTo("15000.00");
    }

    @Test
    @DisplayName("Línea cobrada en parte")
    void lineaCobradaEnParte() throws Exception {
        // WHEN admin registra 10000.00 cobrados en la línea de "Limpieza (profilaxis)"
        MvcResult r = conciliar(liquidacion, lineaLimpieza, monto("10000.00"), null);

        // THEN la línea tiene monto cobrado 10000.00 y resultado PARCIAL
        assertThat(status(r)).isEqualTo(200);
        JsonNode linea = lineaDe(cuerpo(r), "Limpieza (profilaxis)");
        assertThat(numero(linea.path("montoCobrado"))).isEqualByComparingTo("10000.00");
        assertThat(linea.path("resultado").asText()).isEqualTo("PARCIAL");
        assertThat(numero(linea.path("montoSolicitado"))).isEqualByComparingTo("12500.00");
    }

    @Test
    @DisplayName("Línea rechazada con motivo")
    void lineaRechazadaConMotivo() throws Exception {
        // WHEN admin registra 0.00 cobrados en la línea de "Limpieza (profilaxis)" con el motivo "Falta la orden de la práctica"
        MvcResult r = conciliar(liquidacion, lineaLimpieza, monto("0.00"), "Falta la orden de la práctica");

        // THEN la línea tiene monto cobrado 0.00, resultado RECHAZADA y ese motivo
        assertThat(status(r)).isEqualTo(200);
        JsonNode linea = lineaDe(cuerpo(r), "Limpieza (profilaxis)");
        assertThat(numero(linea.path("montoCobrado"))).isEqualByComparingTo("0.00");
        assertThat(linea.path("resultado").asText()).isEqualTo("RECHAZADA");
        assertThat(linea.path("motivoRechazo").asText()).isEqualTo("Falta la orden de la práctica");
        assertThat(estadoDeLaLinea(lineaLimpieza).get("motivo_rechazo")).isEqualTo("Falta la orden de la práctica");
    }

    @Test
    @DisplayName("Línea de otra liquidación")
    void lineaDeOtraLiquidacion() throws Exception {
        // GIVEN una línea de la liquidación de "Prepaga Prueba"
        atendido(pacienteTres, consulta, "2026-10-07T10:00");
        JsonNode deLaPrepaga = generada(prepaga, "2026-10");
        long lineaAjena = lineaDe(deLaPrepaga, "Consulta o control").path("id").asLong();

        // WHEN admin registra lo cobrado indicando la liquidación de 2026-10 y una línea de otra liquidación
        MvcResult r = conciliar(liquidacion, lineaAjena, monto("12000.00"), null);

        // THEN la respuesta es 404 con el código RECURSO_NO_ENCONTRADO y ninguna línea cambia
        assertThat(status(r)).isEqualTo(404);
        assertThat(codigoDeError(r)).isEqualTo("RECURSO_NO_ENCONTRADO");
        assertSinConciliar(lineaAjena);
        assertSinConciliar(lineaConsulta);
        assertSinConciliar(lineaLimpieza);
        // AND una liquidación que no existe también responde 404
        assertThat(status(conciliar(999999L, lineaConsulta, monto("1.00"), null))).isEqualTo(404);
    }

    // --- Monto cobrado y motivo válidos ---

    @Test
    @DisplayName("Monto mayor que el solicitado")
    void montoMayorQueElSolicitado() throws Exception {
        // WHEN admin registra 15000.01 cobrados en la línea de "Consulta o control"
        MvcResult r = conciliar(liquidacion, lineaConsulta, monto("15000.01"), null);

        // THEN la respuesta es 422 con el código MONTO_COBRADO_INVALIDO y la línea sigue sin conciliar
        assertThat(status(r)).isEqualTo(422);
        assertThat(codigoDeError(r)).isEqualTo("MONTO_COBRADO_INVALIDO");
        assertThat(texto(r)).doesNotContain("15000");
        assertSinConciliar(lineaConsulta);
    }

    @Test
    @DisplayName("Monto negativo o con tres decimales")
    void montoNegativoOConTresDecimales() throws Exception {
        // WHEN admin registra -1.00 o 100.005 cobrados en la línea de "Limpieza (profilaxis)"
        MvcResult negativo = conciliar(liquidacion, lineaLimpieza, monto("-1.00"), null);
        MvcResult tresDecimales = conciliar(liquidacion, lineaLimpieza, monto("100.005"), null);

        // THEN cada respuesta es 422 con el código MONTO_COBRADO_INVALIDO
        assertThat(status(negativo)).isEqualTo(422);
        assertThat(codigoDeError(negativo)).isEqualTo("MONTO_COBRADO_INVALIDO");
        assertThat(status(tresDecimales)).isEqualTo(422);
        assertThat(codigoDeError(tresDecimales)).isEqualTo("MONTO_COBRADO_INVALIDO");
        assertSinConciliar(lineaLimpieza);
    }

    @Test
    @DisplayName("Monto ausente o que no es un número")
    void montoAusenteOQueNoEsUnNumero() throws Exception {
        MvcResult ausente = put(como("admin"), urlLinea(liquidacion, lineaConsulta) + "/conciliacion", Map.of());
        MvcResult comoTexto = put(como("admin"), urlLinea(liquidacion, lineaConsulta) + "/conciliacion",
                Map.of("montoCobrado", "mucho"));

        assertThat(status(ausente)).isEqualTo(422);
        assertThat(codigoDeError(ausente)).isEqualTo("MONTO_COBRADO_INVALIDO");
        assertThat(status(comoTexto)).isEqualTo(422);
        assertThat(codigoDeError(comoTexto)).isEqualTo("DATOS_INVALIDOS");
        assertSinConciliar(lineaConsulta);
    }

    @Test
    @DisplayName("Rechazo sin motivo")
    void rechazoSinMotivo() throws Exception {
        // WHEN admin registra 0.00 cobrados en la línea de "Limpieza (profilaxis)" con el motivo vacío o solo con espacios
        for (String motivo : new String[] {"", "   ", null}) {
            MvcResult r = conciliar(liquidacion, lineaLimpieza, monto("0.00"), motivo);

            // THEN la respuesta es 422 con el código MOTIVO_REQUERIDO y la línea sigue sin conciliar
            assertThat(status(r)).as("motivo [" + motivo + "]").isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("MOTIVO_REQUERIDO");
            assertSinConciliar(lineaLimpieza);
        }
    }

    @Test
    @DisplayName("Motivo en una línea cobrada")
    void motivoEnUnaLineaCobrada() throws Exception {
        // WHEN admin registra 15000.00 cobrados en la línea de "Consulta o control" con un motivo
        MvcResult r = conciliar(liquidacion, lineaConsulta, monto("15000.00"), "Un motivo cualquiera");

        // THEN la respuesta es 422 con el código DATOS_INVALIDOS y el campo motivo
        assertThat(status(r)).isEqualTo(422);
        assertThat(codigoDeError(r)).isEqualTo("DATOS_INVALIDOS");
        assertThat(campos(r)).containsExactly("motivo");
        assertThat(texto(r)).doesNotContain("Un motivo cualquiera");
        assertSinConciliar(lineaConsulta);
    }

    @Test
    @DisplayName("Un motivo de 201 caracteres es inválido y uno de 200 se acepta")
    void motivoLargo() throws Exception {
        MvcResult largo = conciliar(liquidacion, lineaLimpieza, monto("0.00"), "x".repeat(201));
        MvcResult justo = conciliar(liquidacion, lineaLimpieza, monto("0.00"), "  " + "x".repeat(200) + "  ");

        assertThat(status(largo)).isEqualTo(422);
        assertThat(codigoDeError(largo)).isEqualTo("DATOS_INVALIDOS");
        assertThat(campos(largo)).containsExactly("motivo");
        assertThat(status(justo)).isEqualTo(200);
        assertThat(estadoDeLaLinea(lineaLimpieza).get("motivo_rechazo")).isEqualTo("x".repeat(200));
    }

    // --- Solo se concilia una liquidación presentada ---

    @Test
    @DisplayName("Conciliar un borrador")
    void conciliarUnBorrador() throws Exception {
        // GIVEN la liquidación de "Prepaga Prueba" de 2026-10 está en BORRADOR
        atendido(pacienteTres, consulta, "2026-10-07T10:00");
        JsonNode deLaPrepaga = generada(prepaga, "2026-10");
        long borrador = deLaPrepaga.path("id").asLong();
        long linea = lineaDe(deLaPrepaga, "Consulta o control").path("id").asLong();

        // WHEN admin registra lo cobrado en una de sus líneas
        MvcResult r = conciliar(borrador, linea, monto("12000.00"), null);

        // THEN la respuesta es 409 con el código LIQUIDACION_NO_PRESENTADA
        assertThat(status(r)).isEqualTo(409);
        assertThat(codigoDeError(r)).isEqualTo("LIQUIDACION_NO_PRESENTADA");
        assertSinConciliar(linea);
    }

    @Test
    @DisplayName("Corregir una línea conciliada")
    void corregirUnaLineaConciliada() throws Exception {
        // GIVEN la línea de "Limpieza (profilaxis)" tiene 10000.00 cobrados y resultado PARCIAL
        conciliar(liquidacion, lineaLimpieza, monto("10000.00"), null);
        assertThat(estadoDeLaLinea(lineaLimpieza).get("resultado")).isEqualTo("PARCIAL");

        // WHEN admin registra 12500.00 cobrados en esa línea
        MvcResult r = conciliar(liquidacion, lineaLimpieza, monto("12500.00"), null);

        // THEN la línea queda con 12500.00 y resultado COBRADA
        assertThat(status(r)).isEqualTo(200);
        JsonNode linea = lineaDe(cuerpo(r), "Limpieza (profilaxis)");
        assertThat(numero(linea.path("montoCobrado"))).isEqualByComparingTo("12500.00");
        assertThat(linea.path("resultado").asText()).isEqualTo("COBRADA");
        assertThat(totalCobradoGuardado(liquidacion)).isEqualByComparingTo("12500.00");
    }

    @Test
    @DisplayName("Corregir un rechazo a cobrada borra el motivo")
    void corregirUnRechazoBorraElMotivo() throws Exception {
        conciliar(liquidacion, lineaLimpieza, monto("0.00"), "Falta la orden de la práctica");

        MvcResult r = conciliar(liquidacion, lineaLimpieza, monto("12500.00"), null);

        assertThat(status(r)).isEqualTo(200);
        assertThat(estadoDeLaLinea(lineaLimpieza).get("motivo_rechazo")).isNull();
        assertThat(estadoDeLaLinea(lineaLimpieza).get("resultado")).isEqualTo("COBRADA");
    }

    @Test
    @DisplayName("Conciliar una liquidación cerrada")
    void conciliarUnaLiquidacionCerrada() throws Exception {
        // GIVEN la liquidación de 2026-10 está CERRADA
        conciliar(liquidacion, lineaConsulta, monto("15000.00"), null);
        conciliar(liquidacion, lineaLimpieza, monto("12500.00"), null);
        jdbc.update("UPDATE liquidacion SET estado = 'CERRADA', cerrada_en = now(), cerrada_por = 'admin' WHERE id = ?",
                liquidacion);

        // WHEN admin registra lo cobrado en una de sus líneas
        MvcResult r = conciliar(liquidacion, lineaLimpieza, monto("0.00"), "Motivo ficticio");

        // THEN la respuesta es 409 con el código LIQUIDACION_CERRADA y la línea no cambia
        assertThat(status(r)).isEqualTo(409);
        assertThat(codigoDeError(r)).isEqualTo("LIQUIDACION_CERRADA");
        assertThat(estadoDeLaLinea(lineaLimpieza).get("resultado")).isEqualTo("COBRADA");
        assertThat(sumaDeLoCobrado(liquidacion)).isEqualByComparingTo("27500.00");
    }

    @Test
    @DisplayName("Los importes se guardan exactos con dos decimales")
    void importesExactos() throws Exception {
        // GIVEN una línea con monto solicitado 12500.00 y un cobro de 0.01
        MvcResult r = conciliar(liquidacion, lineaLimpieza, monto("0.01"), null);

        assertThat(status(r)).isEqualTo(200);
        assertThat(estadoDeLaLinea(lineaLimpieza).get("monto_cobrado")).isEqualTo(new BigDecimal("0.01"));
        assertThat(List.of(estadoDeLaLinea(lineaLimpieza).get("resultado"))).containsExactly("PARCIAL");
        // AND un cobro con ceros de más se normaliza
        conciliar(liquidacion, lineaLimpieza, monto("10000.0"), null);
        assertThat(estadoDeLaLinea(lineaLimpieza).get("monto_cobrado")).isEqualTo(new BigDecimal("10000.00"));
    }
}
