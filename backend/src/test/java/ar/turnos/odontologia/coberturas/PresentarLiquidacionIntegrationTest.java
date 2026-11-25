package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Presentar la liquidación y que una presentada no cambie (C-20, spec liquidaciones-obras-sociales, tarea 6.1, RN-LQ-04
 * y RN-LQ-05). Reloj: 2026-11-24 a las 12:00 en Buenos Aires. Solo datos ficticios del seed de la KB.
 */
class PresentarLiquidacionIntegrationTest extends IntegracionLiquidacionesBase {

    @BeforeEach
    void catalogo() {
        semilla();
    }

    private long borradorDeOctubre() throws Exception {
        atendido(pacienteUno, consulta, "2026-10-06T10:00");
        atendido(pacienteUno, limpieza, "2026-10-20T10:00");
        return generada(obraSocialEjemplo, "2026-10").path("id").asLong();
    }

    private MvcResult presentarComo(String usuario, long liquidacion) throws Exception {
        return post(como(usuario), url(liquidacion) + "/presentar", null);
    }

    @Test
    @DisplayName("Presentar una liquidación completa")
    void presentarUnaLiquidacionCompleta() throws Exception {
        // GIVEN la liquidación de "Obra Social Ejemplo" de 2026-10 en BORRADOR con total solicitado 27500.00
        long id = borradorDeOctubre();
        assertThat(totalSolicitadoGuardado(id)).isEqualByComparingTo("27500.00");

        // WHEN admin la presenta
        MvcResult r = presentarComo("admin", id);

        // THEN la respuesta es 200 con estado PRESENTADA, la fecha de presentación y total solicitado 27500.00
        assertThat(status(r)).isEqualTo(200);
        JsonNode presentada = cuerpo(r);
        assertThat(presentada.path("estado").asText()).isEqualTo("PRESENTADA");
        assertThat(presentada.path("presentadaEn").asText()).startsWith("2026-11-24T15:00");
        assertThat(presentada.path("presentadaPor").asText()).isEqualTo("admin");
        assertThat(numero(presentada.path("totalSolicitado"))).isEqualByComparingTo("27500.00");
        assertThat(lineasDe(presentada)).hasSize(2);
        assertThat(estadoGuardadoLiquidacion(id)).isEqualTo("PRESENTADA");
        assertThat(totalSolicitadoGuardado(id)).isEqualByComparingTo("27500.00");
    }

    @Test
    @DisplayName("Presentar con una línea sin código")
    void presentarConUnaLineaSinCodigo() throws Exception {
        // GIVEN una liquidación en BORRADOR con una línea de "Consulta o control" sin código
        long n0102 = codigo("N-0102", "Consulta con diagnóstico", consulta);
        cobertura(planBase, n0102, 80);
        atendido(pacienteUno, consulta, "2026-10-06T10:00");
        long id = generada(obraSocialEjemplo, "2026-10").path("id").asLong();

        // WHEN admin la presenta
        MvcResult r = presentarComo("admin", id);

        // THEN la respuesta es 409 con el código LINEA_SIN_CODIGO y sigue en BORRADOR
        assertThat(status(r)).isEqualTo(409);
        assertThat(codigoDeError(r)).isEqualTo("LINEA_SIN_CODIGO");
        assertThat(estadoGuardadoLiquidacion(id)).isEqualTo("BORRADOR");
    }

    @Test
    @DisplayName("Presentar una liquidación vacía")
    void presentarUnaLiquidacionVacia() throws Exception {
        // GIVEN la liquidación de "Prepaga Prueba" de 2026-09 en BORRADOR sin líneas
        long id = generada(prepaga, "2026-09").path("id").asLong();

        // WHEN admin la presenta
        MvcResult r = presentarComo("admin", id);

        // THEN la respuesta es 409 con el código LIQUIDACION_VACIA
        assertThat(status(r)).isEqualTo(409);
        assertThat(codigoDeError(r)).isEqualTo("LIQUIDACION_VACIA");
        assertThat(estadoGuardadoLiquidacion(id)).isEqualTo("BORRADOR");
    }

    @Test
    @DisplayName("Presentar dos veces")
    void presentarDosVeces() throws Exception {
        // GIVEN la liquidación de 2026-10 está PRESENTADA
        long id = borradorDeOctubre();
        assertThat(status(presentarComo("admin", id))).isEqualTo(200);
        String presentadaEn = jdbc.queryForObject("SELECT presentada_en::text FROM liquidacion WHERE id = ?",
                String.class, id);

        // WHEN admin la presenta otra vez
        ahoraEs("2026-11-25T15:00:00Z");
        MvcResult r = presentarComo("admin", id);

        // THEN la respuesta es 409 con el código LIQUIDACION_PRESENTADA
        assertThat(status(r)).isEqualTo(409);
        assertThat(codigoDeError(r)).isEqualTo("LIQUIDACION_PRESENTADA");
        assertThat(jdbc.queryForObject("SELECT presentada_en::text FROM liquidacion WHERE id = ?", String.class, id))
                .isEqualTo(presentadaEn);
    }

    @Test
    @DisplayName("Presentar una cerrada o inexistente")
    void presentarUnaCerradaOInexistente() throws Exception {
        long id = borradorDeOctubre();
        jdbc.update("UPDATE liquidacion SET estado = 'PRESENTADA', presentada_en = now(), presentada_por = 'admin' "
                + "WHERE id = ?", id);
        jdbc.update("UPDATE liquidacion SET estado = 'CERRADA', cerrada_en = now(), cerrada_por = 'admin' WHERE id = ?",
                id);

        MvcResult cerrada = presentarComo("admin", id);
        MvcResult inexistente = presentarComo("admin", 999999L);

        assertThat(status(cerrada)).isEqualTo(409);
        assertThat(codigoDeError(cerrada)).isEqualTo("LIQUIDACION_CERRADA");
        assertThat(status(inexistente)).isEqualTo(404);
    }

    @Test
    @DisplayName("Cambios en el catálogo después de presentar")
    void cambiosEnElCatalogoDespuesDePresentar() throws Exception {
        // GIVEN la liquidación de 2026-10 está PRESENTADA con la consulta de "Paciente Uno" en 15000.00 y el plan "Plan Base"
        long id = borradorDeOctubre();
        presentar(id);

        // WHEN después cambian el arancel de "Consulta o control" a 16000.00 y el nombre del plan a "Plan Base 2027"
        arancel(consulta, "16000.00");
        jdbc.update("UPDATE plan SET nombre = 'Plan Base 2027' WHERE id = ?", planBase);
        cobertura(planBase, n0101, 50);
        jdbc.update("UPDATE paciente SET nombre = 'Otro', dni = '99000099' WHERE id = ?", pacienteUno);
        jdbc.update("UPDATE afiliacion_paciente SET numero_afiliado = 'OSE-999999' WHERE paciente_id = ?", pacienteUno);

        // THEN la consulta de la liquidación sigue mostrando 15000.00 y "Plan Base"
        JsonNode detalle = detalle(id);
        JsonNode linea = lineaDe(detalle, "Consulta o control");
        assertThat(numero(linea.path("montoSolicitado"))).isEqualByComparingTo("15000.00");
        assertThat(numero(linea.path("precio"))).isEqualByComparingTo("15000.00");
        assertThat(linea.path("porcentaje").asInt()).isEqualTo(100);
        assertThat(grupoDe(detalle, "Plan Base").path("lineas")).hasSize(2);
        assertThat(linea.path("paciente").path("nombre").asText()).isEqualTo("Paciente Uno");
        assertThat(linea.path("paciente").path("dni").asText()).isEqualTo("99000001");
        assertThat(linea.path("numeroAfiliado").asText()).isEqualTo("OSE-000001");
        assertThat(numero(detalle.path("totalSolicitado"))).isEqualByComparingTo("27500.00");
    }

    @Test
    @DisplayName("La base rechaza modificar una línea presentada")
    void laBaseRechazaModificarUnaLineaPresentada() throws Exception {
        // GIVEN la liquidación de 2026-10 está PRESENTADA
        long id = borradorDeOctubre();
        presentar(id);
        long linea = jdbc.queryForObject("SELECT min(id) FROM liquidacion_linea WHERE liquidacion_id = ?", Long.class,
                id);
        long turnoNuevo = atendido(pacienteUno, consulta, "2026-10-21T10:00");

        // WHEN se intenta por SQL cambiar el monto solicitado de una línea, insertar o borrar una línea, o borrar la liquidación
        // THEN la base rechaza cada operación y los datos no cambian
        assertThatThrownBy(() -> jdbc.update("UPDATE liquidacion_linea SET precio = 1.00, monto_solicitado = 1.00 "
                + "WHERE id = ?", linea)).hasMessageContaining("presentada");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO liquidacion_linea(liquidacion_id, obra_social_id, turno_id, "
                + "paciente_id, plan_id, prestacion_id, fecha, paciente_nombre, paciente_dni, numero_afiliado, "
                + "plan_nombre, prestacion_nombre, precio, origen_precio) VALUES (?,?,?,?,?,?, DATE '2026-10-21', "
                + "'Paciente Uno', '99000001', 'OSE-000001', 'Plan Base', 'Consulta o control', 1.00, 'ARANCEL')", id,
                obraSocialEjemplo, turnoNuevo, pacienteUno, planBase, consulta)).hasMessageContaining("BORRADOR");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM liquidacion_linea WHERE id = ?", linea))
                .hasMessageContaining("BORRADOR");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM liquidacion WHERE id = ?", id))
                .hasMessageContaining("no se borra");
        assertThat(lineas(id)).isEqualTo(2);
        assertThat(sumaDeLasLineas(id)).isEqualByComparingTo("27500.00");
        assertThat(cantidad("liquidacion")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT precio FROM liquidacion_linea WHERE id = ?", BigDecimal.class, linea))
                .isGreaterThan(new BigDecimal("1.00"));
    }

    @Test
    @DisplayName("Sin borrado por la API")
    void sinBorradoPorLaApi() throws Exception {
        // GIVEN una liquidación en BORRADOR y otra PRESENTADA
        long borrador = borradorDeOctubre();
        long presentada = generada(prepaga, "2026-10").path("id").asLong();
        atendido(pacienteTres, consulta, "2026-10-07T10:00");
        regenerar(presentada);
        presentar(presentada);

        // WHEN admin envía DELETE a una liquidación
        MvcResult deBorrador = delete(como("admin"), url(borrador));
        MvcResult dePresentada = delete(como("admin"), url(presentada));
        MvcResult reemplazo = put(como("admin"), url(borrador), Map.of("estado", "CERRADA"));

        // THEN la respuesta es 405 y la liquidación sigue existiendo
        assertThat(status(deBorrador)).isEqualTo(405);
        assertThat(status(dePresentada)).isEqualTo(405);
        assertThat(status(reemplazo)).isEqualTo(405);
        assertThat(cantidad("liquidacion")).isEqualTo(2);
        assertThat(estadoGuardadoLiquidacion(borrador)).isEqualTo("BORRADOR");
    }
}
