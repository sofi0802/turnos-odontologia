package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Generar la liquidación de una obra social para un mes cerrado (C-20, spec liquidaciones-obras-sociales, tarea 3.1,
 * RN-LQ-01 y RN-LQ-02). Reloj: 2026-11-24 a las 12:00 en Buenos Aires. Solo datos ficticios.
 */
class GenerarLiquidacionIntegrationTest extends IntegracionLiquidacionesBase {

    @Test
    @DisplayName("Generar la liquidación de octubre")
    void generarLaLiquidacionDeOctubre() throws Exception {
        // GIVEN "Paciente Uno" tiene un turno ATENDIDO de "Consulta o control" el 2026-10-06 a las 10:00
        semilla();
        atendido(pacienteUno, consulta, "2026-10-06T10:00");

        // WHEN admin genera la liquidación de "Obra Social Ejemplo" para el período 2026-10
        MvcResult r = generar(obraSocialEjemplo, "2026-10");

        // THEN la respuesta es 201 con una liquidación en BORRADOR, período 2026-10, una línea y total 15000.00
        assertThat(status(r)).isEqualTo(201);
        JsonNode liquidacion = cuerpo(r);
        assertThat(liquidacion.path("estado").asText()).isEqualTo("BORRADOR");
        assertThat(liquidacion.path("periodo").asText()).isEqualTo("2026-10");
        assertThat(liquidacion.path("obraSocial").path("nombre").asText()).isEqualTo("Obra Social Ejemplo");
        assertThat(lineasDe(liquidacion)).hasSize(1);
        assertThat(numero(liquidacion.path("totalSolicitado"))).isEqualByComparingTo("15000.00");
        assertThat(liquidacion.path("generadaPor").asText()).isEqualTo("admin");
        assertThat(r.getResponse().getHeader("Location")).endsWith(LIQUIDACIONES + "/" + liquidacion.path("id").asLong());
        // AND la base guarda lo mismo
        long id = liquidacion.path("id").asLong();
        assertThat(estadoGuardadoLiquidacion(id)).isEqualTo("BORRADOR");
        assertThat(totalSolicitadoGuardado(id)).isEqualByComparingTo("15000.00");
        assertThat(lineas(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("El mes en curso no está cerrado")
    void elMesEnCursoNoEstaCerrado() throws Exception {
        // GIVEN hoy es 2026-11-24
        semilla();
        atendido(pacienteUno, consulta, "2026-11-10T10:00");

        // WHEN admin genera la liquidación de "Obra Social Ejemplo" para el período 2026-11
        MvcResult r = generar(obraSocialEjemplo, "2026-11");

        // THEN la respuesta es 409 con el código PERIODO_NO_CERRADO y no se crea ninguna liquidación
        assertThat(status(r)).isEqualTo(409);
        assertThat(codigoDeError(r)).isEqualTo("PERIODO_NO_CERRADO");
        assertThat(cantidad("liquidacion")).isZero();
    }

    @Test
    @DisplayName("El primer día del mes siguiente el período ya está cerrado")
    void elPrimerDiaDelMesSiguienteElPeriodoYaEstaCerrado() throws Exception {
        // GIVEN hoy es 2026-11-01 a las 00:00 en America/Argentina/Buenos_Aires
        semilla();
        ahoraEs("2026-11-01T03:00:00Z");

        // WHEN admin genera la liquidación de "Obra Social Ejemplo" para el período 2026-10
        MvcResult r = generar(obraSocialEjemplo, "2026-10");

        // THEN la respuesta es 201
        assertThat(status(r)).isEqualTo(201);
        // AND un minuto antes, a las 23:59 del 2026-10-31, todavía no
        ahoraEs("2026-11-01T02:59:00Z");
        jdbc.execute("TRUNCATE liquidacion_turno_quitado, liquidacion_linea, liquidacion RESTART IDENTITY CASCADE");
        assertThat(status(generar(obraSocialEjemplo, "2026-10"))).isEqualTo(409);
    }

    @Test
    @DisplayName("Liquidación repetida para la misma obra social y mes")
    void liquidacionRepetidaParaLaMismaObraSocialYMes() throws Exception {
        // GIVEN existe la liquidación de "Obra Social Ejemplo" para 2026-10
        semilla();
        generada(obraSocialEjemplo, "2026-10");

        // WHEN admin vuelve a generar la liquidación de "Obra Social Ejemplo" para 2026-10
        MvcResult r = generar(obraSocialEjemplo, "2026-10");

        // THEN la respuesta es 409 con el código LIQUIDACION_EXISTENTE y sigue habiendo una sola
        assertThat(status(r)).isEqualTo(409);
        assertThat(codigoDeError(r)).isEqualTo("LIQUIDACION_EXISTENTE");
        assertThat(cantidad("liquidacion")).isEqualTo(1);
    }

    @Test
    @DisplayName("Otra obra social en el mismo mes")
    void otraObraSocialEnElMismoMes() throws Exception {
        // GIVEN existe la liquidación de "Obra Social Ejemplo" para 2026-10
        semilla();
        generada(obraSocialEjemplo, "2026-10");

        // WHEN admin genera la liquidación de "Prepaga Prueba" para 2026-10
        MvcResult r = generar(prepaga, "2026-10");

        // THEN la respuesta es 201
        assertThat(status(r)).isEqualTo(201);
        assertThat(cantidad("liquidacion")).isEqualTo(2);
    }

    @Test
    @DisplayName("Obra social inexistente o período mal formado")
    void obraSocialInexistenteOPeriodoMalFormado() throws Exception {
        semilla();

        // WHEN admin genera una liquidación para una obra social que no existe
        MvcResult inexistente = generar(999999L, "2026-10");
        // THEN la respuesta es 404 con el código RECURSO_NO_ENCONTRADO
        assertThat(status(inexistente)).isEqualTo(404);
        assertThat(codigoDeError(inexistente)).isEqualTo("RECURSO_NO_ENCONTRADO");

        // AND si el período es 2026-13 o falta, la respuesta es 422 con el código DATOS_INVALIDOS y el campo periodo
        for (String periodo : new String[] {"2026-13", "octubre", "2026-1"}) {
            MvcResult malFormado = generar(obraSocialEjemplo, periodo);
            assertThat(status(malFormado)).as(periodo).isEqualTo(422);
            assertThat(codigoDeError(malFormado)).isEqualTo("DATOS_INVALIDOS");
            assertThat(campos(malFormado)).containsExactly("periodo");
        }
        MvcResult sinPeriodo = post(como("admin"), LIQUIDACIONES, generacion(obraSocialEjemplo, null));
        assertThat(status(sinPeriodo)).isEqualTo(422);
        assertThat(codigoDeError(sinPeriodo)).isEqualTo("DATOS_INVALIDOS");
        assertThat(campos(sinPeriodo)).containsExactly("periodo");
        assertThat(cantidad("liquidacion")).isZero();
    }

    @Test
    @DisplayName("Mes sin turnos para liquidar")
    void mesSinTurnosParaLiquidar() throws Exception {
        // GIVEN no hay turnos atendidos de afiliados a "Prepaga Prueba" hasta el 2026-09-30
        semilla();

        // WHEN admin genera la liquidación de "Prepaga Prueba" para 2026-09
        MvcResult r = generar(prepaga, "2026-09");

        // THEN la respuesta es 201 con una liquidación en BORRADOR, sin líneas y total solicitado 0.00
        assertThat(status(r)).isEqualTo(201);
        JsonNode liquidacion = cuerpo(r);
        assertThat(liquidacion.path("estado").asText()).isEqualTo("BORRADOR");
        assertThat(lineasDe(liquidacion)).isEmpty();
        assertThat(numero(liquidacion.path("totalSolicitado"))).isEqualByComparingTo("0.00");
    }
}
