package ar.turnos.odontologia.coberturas.application;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion;
import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion.Candidato;
import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion.CodigoCubierto;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Escritor del CSV de una liquidación (C-20, tarea 6.3, design D10 y RN-LQ-05): separador {@code ;}, decimales con
 * punto, UTF-8 con BOM, fin de línea {@code \r\n}, escapado y defensa contra la inyección de fórmulas (OWASP). Solo
 * datos ficticios.
 */
class CsvLiquidacionTest {

    private static final String ENCABEZADO = "fecha;paciente;dni;numero_afiliado;plan;codigo;prestacion;monto_solicitado";

    private static LineaLiquidacion linea(long turno, String fecha, String paciente, String plan, String prestacion,
                                          String precio, int porcentaje, String codigo) {
        Candidato c = new Candidato(turno, 20L, 30L, 40L, LocalDate.parse(fecha), paciente, "99000001", "OSE-000001",
                plan, prestacion, new BigDecimal(precio), List.of(new CodigoCubierto(1L, codigo, porcentaje)));
        return LineaLiquidacion.desde(c, null, null).linea().conId(turno);
    }

    private static String texto(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String cuerpo(List<LineaLiquidacion> lineas, String total) {
        String completo = texto(CsvLiquidacion.generar(lineas, new BigDecimal(total)));
        assertThat(completo).startsWith("﻿");
        return completo.substring(1);
    }

    @Test
    @DisplayName("Comienza con la marca de orden de bytes UTF-8")
    void bom() {
        byte[] bytes = CsvLiquidacion.generar(List.of(), new BigDecimal("0.00"));

        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(bytes[3]).isEqualTo((byte) 'f');
    }

    @Test
    @DisplayName("Sin líneas solo tiene el encabezado y el total en cero")
    void sinLineas() {
        assertThat(cuerpo(List.of(), "0.00")).isEqualTo(ENCABEZADO + "\r\nTotal;;;;;;;0.00\r\n");
    }

    @Test
    @DisplayName("El contenido exacto de la liquidación de 27500.00")
    void contenidoExacto() {
        List<LineaLiquidacion> lineas = List.of(
                linea(2L, "2026-10-20", "Paciente Uno", "Plan Base", "Limpieza (profilaxis)", "25000.00", 50, "N-0201"),
                linea(1L, "2026-10-06", "Paciente Uno", "Plan Base", "Consulta o control", "15000.00", 100, "N-0101"));

        assertThat(cuerpo(lineas, "27500.00")).isEqualTo(ENCABEZADO + "\r\n"
                + "2026-10-06;Paciente Uno;99000001;OSE-000001;Plan Base;N-0101;Consulta o control;15000.00\r\n"
                + "2026-10-20;Paciente Uno;99000001;OSE-000001;Plan Base;N-0201;Limpieza (profilaxis);12500.00\r\n"
                + "Total;;;;;;;27500.00\r\n");
    }

    @Test
    @DisplayName("Las filas se ordenan por plan, fecha y turno")
    void orden() {
        List<LineaLiquidacion> lineas = List.of(
                linea(5L, "2026-10-02", "Paciente Dos", "Plan Integral", "Consulta o control", "100.00", 100, "N-0101"),
                linea(4L, "2026-10-09", "Paciente Uno", "Plan Base", "Consulta o control", "100.00", 100, "N-0101"),
                linea(3L, "2026-10-09", "Paciente Tres", "Plan Base", "Consulta o control", "100.00", 100, "N-0101"),
                linea(6L, "2026-10-01", "Paciente Cuatro", "Plan Base", "Consulta o control", "100.00", 100, "N-0101"));

        String[] filas = cuerpo(lineas, "400.00").split("\r\n");

        assertThat(filas[1]).startsWith("2026-10-01;Paciente Cuatro");
        assertThat(filas[2]).startsWith("2026-10-09;Paciente Tres");
        assertThat(filas[3]).startsWith("2026-10-09;Paciente Uno");
        assertThat(filas[4]).startsWith("2026-10-02;Paciente Dos");
    }

    @Test
    @DisplayName("Los acentos van en UTF-8 y los importes con punto y dos decimales")
    void utf8YDecimales() {
        byte[] bytes = CsvLiquidacion.generar(List.of(
                linea(1L, "2026-10-09", "Paciente Uno", "Plan Base", "Extracción simple", "30000.00", 100, "N-0301")),
                new BigDecimal("30000.00"));

        String completo = texto(bytes);
        assertThat(completo).contains("Extracción simple;30000.00");
        // la "ó" ocupa dos bytes en UTF-8
        assertThat(bytes.length).isEqualTo(completo.getBytes(StandardCharsets.UTF_8).length);
        assertThat(completo.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(completo.length());
        assertThat(completo).doesNotContain("30000,00");
    }

    @Test
    @DisplayName("Un campo con punto y coma, comillas o salto de línea va entre comillas dobles")
    void escapado() {
        List<LineaLiquidacion> lineas = List.of(
                linea(1L, "2026-10-09", "Paciente; Uno", "Plan \"Base\"", "Consulta\r\no control", "100.00", 100, "N-0101"));

        assertThat(cuerpo(lineas, "100.00")).contains("\"Paciente; Uno\"")
                .contains("\"Plan \"\"Base\"\"\"")
                .contains("\"Consulta\r\no control\"");
    }

    @ParameterizedTest(name = "\"{0}\" se exporta como \"{1}\"")
    @DisplayName("Una celda que empieza con un signo de fórmula lleva una comilla simple delante")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "=Control|'=Control",
            "+Control|'+Control",
            "-Control|'-Control",
            "@Control|'@Control",
            "Control=1|Control=1",
            "Control -1|Control -1"})
    void inyeccionDeFormulas(String prestacion, String esperado) {
        List<LineaLiquidacion> lineas = List.of(
                linea(1L, "2026-10-09", "Paciente Uno", "Plan Base", prestacion, "100.00", 100, "N-0101"));

        assertThat(cuerpo(lineas, "100.00")).contains(";N-0101;" + esperado + ";100.00\r\n");
    }

    @Test
    @DisplayName("Un tabulador o un retorno de carro al principio también se neutralizan")
    void tabuladorYRetorno() {
        List<LineaLiquidacion> lineas = List.of(
                linea(1L, "2026-10-09", "\tPaciente Uno", "Plan Base", "\rConsulta", "100.00", 100, "N-0101"));

        String cuerpo = cuerpo(lineas, "100.00");

        assertThat(cuerpo).contains(";'\tPaciente Uno;").contains(";\"'\rConsulta\";");
        assertThat(cuerpo).doesNotContain(";\tPaciente");
    }

    @Test
    @DisplayName("La neutralización y el escapado se combinan")
    void prefijoYComillas() {
        List<LineaLiquidacion> lineas = List.of(
                linea(1L, "2026-10-09", "=A;B", "Plan Base", "Consulta o control", "100.00", 100, "N-0101"));

        assertThat(cuerpo(lineas, "100.00")).contains("\"'=A;B\"");
    }
}
