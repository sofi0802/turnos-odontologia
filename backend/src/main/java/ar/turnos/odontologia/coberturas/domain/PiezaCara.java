package ar.turnos.odontologia.coberturas.domain;

import java.util.Set;

/**
 * Pieza dental FDI y cara de un ítem de presupuesto (RN-PR-06): ambas opcionales, pero una cara exige pieza. Las reglas
 * son las de {@code odontograma_registro} de V8 (RN-CL-03), copiadas acá para no acoplar este módulo al de la ficha
 * clínica; un test de migración compara ambas tablas. Los mensajes no repiten el valor recibido (datos de salud).
 */
public record PiezaCara(Integer pieza, String cara) {

    public static final PiezaCara NINGUNA = new PiezaCara(null, null);

    private static final Set<String> CARAS = Set.of("O", "M", "D", "V", "L", "P");
    private static final Set<Integer> CUADRANTES_INFERIORES = Set.of(3, 4, 7, 8);
    private static final Set<Integer> CUADRANTES_SUPERIORES = Set.of(1, 2, 5, 6);

    public static PiezaCara de(Integer pieza, String cara) {
        if (pieza != null && !esFdi(pieza)) {
            throw CoberturaException.datosInvalidos("pieza", "La pieza no está en la notación FDI");
        }
        if (cara != null) {
            if (pieza == null) {
                throw CoberturaException.datosInvalidos("cara", "La cara exige una pieza");
            }
            int cuadrante = pieza / 10;
            boolean valida = CARAS.contains(cara)
                    && !("L".equals(cara) && !CUADRANTES_INFERIORES.contains(cuadrante))
                    && !("P".equals(cara) && !CUADRANTES_SUPERIORES.contains(cuadrante));
            if (!valida) {
                throw CoberturaException.datosInvalidos("cara", "La cara no es válida para la pieza");
            }
        }
        return pieza == null && cara == null ? NINGUNA : new PiezaCara(pieza, cara);
    }

    /** Permanentes 11-18, 21-28, 31-38 y 41-48; temporales 51-55, 61-65, 71-75 y 81-85. */
    private static boolean esFdi(int pieza) {
        int cuadrante = pieza / 10;
        int diente = pieza % 10;
        if (pieza < 11 || cuadrante > 8 || diente < 1) {
            return false;
        }
        return cuadrante <= 4 ? diente <= 8 : diente <= 5;
    }
}
