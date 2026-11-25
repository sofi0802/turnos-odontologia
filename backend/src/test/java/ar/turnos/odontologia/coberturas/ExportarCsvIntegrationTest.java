package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Exportar la liquidación en CSV (C-20, spec liquidaciones-obras-sociales, tarea 6.3, RN-LQ-05 y design D10): formato
 * byte por byte (BOM, {@code ;}, {@code \r\n}, fila de total) y encabezados HTTP. Reloj: 2026-11-24 a las 12:00 en
 * Buenos Aires. Solo datos ficticios del seed de la KB.
 */
class ExportarCsvIntegrationTest extends IntegracionLiquidacionesBase {

    private static final String ENCABEZADO = "fecha;paciente;dni;numero_afiliado;plan;codigo;prestacion;monto_solicitado";

    @BeforeEach
    void catalogo() {
        semilla();
    }

    private MvcResult csv(long liquidacion) throws Exception {
        return get(como("admin"), url(liquidacion) + "/csv");
    }

    private static byte[] bytes(MvcResult r) {
        return r.getResponse().getContentAsByteArray();
    }

    @Test
    @DisplayName("Descargar una liquidación presentada")
    void descargarUnaLiquidacionPresentada() throws Exception {
        // GIVEN la liquidación de "Obra Social Ejemplo" de 2026-10 está PRESENTADA con la consulta y la limpieza de "Paciente Uno"
        atendido(pacienteUno, consulta, "2026-10-06T10:00");
        atendido(pacienteUno, limpieza, "2026-10-20T10:00");
        long id = generada(obraSocialEjemplo, "2026-10").path("id").asLong();
        presentar(id);

        // WHEN admin descarga su CSV
        MvcResult r = csv(id);

        // THEN la respuesta es 200 con text/csv; charset=UTF-8, Content-Disposition: attachment y Cache-Control: no-store
        assertThat(status(r)).isEqualTo(200);
        assertThat(r.getResponse().getContentType()).startsWith("text/csv").containsIgnoringCase("charset=UTF-8");
        assertThat(r.getResponse().getHeader("Content-Disposition"))
                .isEqualTo("attachment; filename=\"liquidacion-" + obraSocialEjemplo + "-2026-10.csv\"");
        assertThat(r.getResponse().getHeader("Cache-Control")).contains("no-store");
        // AND el archivo tiene el encabezado, la fila de la consulta, la de la limpieza con 12500.00 y la fila final con el total 27500.00
        String esperado = "﻿" + ENCABEZADO + "\r\n"
                + "2026-10-06;Paciente Uno;99000001;OSE-000001;Plan Base;N-0101;Consulta o control;15000.00\r\n"
                + "2026-10-20;Paciente Uno;99000001;OSE-000001;Plan Base;N-0201;Limpieza (profilaxis);12500.00\r\n"
                + "Total;;;;;;;27500.00\r\n";
        assertThat(bytes(r)).isEqualTo(esperado.getBytes(StandardCharsets.UTF_8));
        assertThat(bytes(r)).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
    }

    @Test
    @DisplayName("Descargar un borrador")
    void descargarUnBorrador() throws Exception {
        // GIVEN la liquidación de 2026-10 está en BORRADOR
        atendido(pacienteUno, consulta, "2026-10-06T10:00");
        long id = generada(obraSocialEjemplo, "2026-10").path("id").asLong();

        // WHEN admin descarga su CSV
        MvcResult r = csv(id);

        // THEN la respuesta es 409 con el código LIQUIDACION_NO_PRESENTADA
        assertThat(status(r)).isEqualTo(409);
        assertThat(codigoDeError(r)).isEqualTo("LIQUIDACION_NO_PRESENTADA");
        assertThat(texto(r)).doesNotContain("Paciente Uno", "99000001");
    }

    @Test
    @DisplayName("Celdas que empiezan con un signo de fórmula")
    void celdasQueEmpiezanConUnSignoDeFormula() throws Exception {
        // GIVEN una liquidación presentada con una prestación ficticia llamada "=Control"
        long formula = prestacion("=Control");
        long n0901 = codigo("N-0901", "Control ficticio", formula);
        arancel(formula, "10000.00");
        cobertura(planBase, n0901, 100);
        atendido(pacienteUno, formula, "2026-10-06T10:00");
        long id = generada(obraSocialEjemplo, "2026-10").path("id").asLong();
        presentar(id);

        // WHEN admin descarga su CSV
        MvcResult r = csv(id);

        // THEN la celda de la prestación no empieza con = y la planilla no la interpreta como fórmula
        String contenido = new String(bytes(r), StandardCharsets.UTF_8);
        assertThat(contenido).contains(";N-0901;'=Control;10000.00\r\n");
        assertThat(contenido).doesNotContain(";=Control");
        for (String fila : contenido.substring(1).split("\r\n")) {
            for (String celda : fila.split(";")) {
                assertThat(celda).doesNotStartWith("=");
            }
        }
    }

    @Test
    @DisplayName("Una liquidación cerrada también se descarga y una inexistente responde 404")
    void cerradaEInexistente() throws Exception {
        atendido(pacienteUno, consulta, "2026-10-06T10:00");
        long id = generada(obraSocialEjemplo, "2026-10").path("id").asLong();
        presentar(id);
        jdbc.update("UPDATE liquidacion_linea SET monto_cobrado = 15000.00, resultado = 'COBRADA', "
                + "conciliada_en = now(), conciliada_por = 'admin' WHERE liquidacion_id = ?", id);
        jdbc.update("UPDATE liquidacion SET estado = 'CERRADA', cerrada_en = now(), cerrada_por = 'admin' WHERE id = ?",
                id);

        MvcResult cerrada = csv(id);
        MvcResult inexistente = csv(999999L);

        assertThat(status(cerrada)).isEqualTo(200);
        assertThat(new String(bytes(cerrada), StandardCharsets.UTF_8)).contains("Total;;;;;;;15000.00\r\n");
        // el CSV no lleva la conciliación (RN-LQ-05)
        assertThat(new String(bytes(cerrada), StandardCharsets.UTF_8)).doesNotContain("COBRADA", "monto_cobrado");
        assertThat(status(inexistente)).isEqualTo(404);
    }

    @Test
    @DisplayName("El nombre del archivo lleva solo ids y el período, sin datos personales")
    void nombreDelArchivoSinDatosPersonales() throws Exception {
        atendido(pacienteTres, consulta, "2026-10-07T10:00");
        long id = generada(prepaga, "2026-10").path("id").asLong();
        presentar(id);

        MvcResult r = csv(id);

        assertThat(r.getResponse().getHeader("Content-Disposition")).isEqualTo(
                "attachment; filename=\"liquidacion-" + prepaga + "-2026-10.csv\"");
        assertThat(r.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }
}
