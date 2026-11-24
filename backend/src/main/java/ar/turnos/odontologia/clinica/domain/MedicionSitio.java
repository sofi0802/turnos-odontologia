package ar.turnos.odontologia.clinica.domain;

import java.util.Objects;

/**
 * Medición de un sitio del periodontograma (RN-CL-05): profundidad de sondaje de 0 a 15 mm y recesión de −5 a
 * 15 mm, negativa si el margen de la encía queda por encima del límite amelocementario. Los mensajes de error
 * nunca repiten el valor medido (regla dura 6).
 */
public record MedicionSitio(SitioPeriodontal sitio, int profundidadSondaje, int recesion, boolean sangrado,
                            boolean placa) {

    public static final int PROFUNDIDAD_MINIMA = 0;
    public static final int PROFUNDIDAD_MAXIMA = 15;
    public static final int RECESION_MINIMA = -5;
    public static final int RECESION_MAXIMA = 15;

    public MedicionSitio {
        Objects.requireNonNull(sitio, "sitio");
        if (profundidadSondaje < PROFUNDIDAD_MINIMA || profundidadSondaje > PROFUNDIDAD_MAXIMA) {
            throw new ClinicaException(CodigoClinica.DATOS_INVALIDOS,
                    "La profundidad de sondaje tiene que estar entre 0 y 15 mm");
        }
        if (recesion < RECESION_MINIMA || recesion > RECESION_MAXIMA) {
            throw new ClinicaException(CodigoClinica.DATOS_INVALIDOS, "La recesión tiene que estar entre -5 y 15 mm");
        }
    }

    /** Pérdida de inserción del sitio: profundidad de sondaje + recesión (RN-CL-06). */
    public int perdidaInsercion() {
        return profundidadSondaje + recesion;
    }
}
