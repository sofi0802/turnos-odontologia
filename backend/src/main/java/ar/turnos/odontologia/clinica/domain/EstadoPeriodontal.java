package ar.turnos.odontologia.clinica.domain;

import java.util.List;

/**
 * Estado periodontal de una pieza presente (RN-CL-06, DD-13). Se calcula por el peor sitio y no se guarda. Es
 * orientativo: no reemplaza el diagnóstico del profesional.
 */
public enum EstadoPeriodontal {
    SANO,
    LEVE,
    MODERADO,
    SEVERO;

    private static final int PROFUNDIDAD_SANA_MAXIMA = 3;

    /** @param sitios los 6 sitios de la pieza, ya validados */
    public static EstadoPeriodontal de(List<MedicionSitio> sitios) {
        int perdidaMaxima = sitios.stream().mapToInt(MedicionSitio::perdidaInsercion).max().orElseThrow();
        if (perdidaMaxima >= 5) {
            return SEVERO;
        }
        if (perdidaMaxima >= 3) {
            return MODERADO;
        }
        if (perdidaMaxima >= 1) {
            return LEVE;
        }
        boolean sangrado = sitios.stream().anyMatch(MedicionSitio::sangrado);
        boolean bolsa = sitios.stream().anyMatch(s -> s.profundidadSondaje() > PROFUNDIDAD_SANA_MAXIMA);
        return sangrado || bolsa ? LEVE : SANO;
    }
}
