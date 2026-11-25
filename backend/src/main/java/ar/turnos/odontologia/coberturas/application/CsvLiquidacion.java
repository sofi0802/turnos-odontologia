package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

/**
 * CSV de una liquidación para presentar a la obra social (RN-LQ-05, C-20 design D10): separador {@code ;}, importes con
 * punto y dos decimales, fechas ISO, fin de línea {@code \r\n}, UTF-8 con BOM (así Excel en español muestra los acentos
 * y separa las columnas), una fila por línea ordenada por plan, fecha y turno, y una fila final con el total.
 *
 * <p>Escapado: un campo con {@code ;}, {@code "} o salto de línea va entre comillas dobles (la comilla se duplica). Una
 * celda de texto que empieza con {@code =}, {@code +}, {@code -}, {@code @}, tabulador o retorno de carro lleva una
 * comilla simple delante para que la planilla no la interprete como fórmula (inyección de fórmulas, OWASP). Los importes
 * son números positivos y no se escapan. Se arma en memoria: nada se escribe a disco ni a logs (RN-LQ-08).
 */
public final class CsvLiquidacion {

    private static final String SEPARADOR = ";";
    private static final String FIN_DE_LINEA = "\r\n";
    private static final String BOM = String.valueOf((char) 0xFEFF);
    private static final String ENCABEZADO =
            "fecha;paciente;dni;numero_afiliado;plan;codigo;prestacion;monto_solicitado";

    private CsvLiquidacion() {
    }

    /** Las líneas deben tener código; {@code total} es el total solicitado de la liquidación. */
    public static byte[] generar(List<LineaLiquidacion> lineas, BigDecimal total) {
        StringBuilder csv = new StringBuilder(BOM).append(ENCABEZADO).append(FIN_DE_LINEA);
        lineas.stream()
                .sorted(Comparator.comparing(LineaLiquidacion::planNombre)
                        .thenComparing(LineaLiquidacion::planId)
                        .thenComparing(LineaLiquidacion::fecha)
                        .thenComparing(LineaLiquidacion::turnoId))
                .forEach(l -> csv
                        .append(l.fecha()).append(SEPARADOR)
                        .append(celda(l.pacienteNombre())).append(SEPARADOR)
                        .append(celda(l.pacienteDni())).append(SEPARADOR)
                        .append(celda(l.numeroAfiliado())).append(SEPARADOR)
                        .append(celda(l.planNombre())).append(SEPARADOR)
                        .append(celda(l.codigo())).append(SEPARADOR)
                        .append(celda(l.prestacionNombre())).append(SEPARADOR)
                        .append(l.montoSolicitado().toPlainString()).append(FIN_DE_LINEA));
        csv.append("Total;;;;;;;").append(total.toPlainString()).append(FIN_DE_LINEA);
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String celda(String texto) {
        String seguro = empiezaConSignoDeFormula(texto) ? "'" + texto : texto;
        boolean necesitaComillas = seguro.contains(SEPARADOR) || seguro.contains("\"") || seguro.contains("\n")
                || seguro.contains("\r");
        return necesitaComillas ? "\"" + seguro.replace("\"", "\"\"") + "\"" : seguro;
    }

    private static boolean empiezaConSignoDeFormula(String texto) {
        if (texto.isEmpty()) {
            return false;
        }
        return switch (texto.charAt(0)) {
            case '=', '+', '-', '@', '\t', '\r' -> true;
            default -> false;
        };
    }
}
