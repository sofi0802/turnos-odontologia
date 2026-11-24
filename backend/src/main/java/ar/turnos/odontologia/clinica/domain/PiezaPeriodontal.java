package ar.turnos.odontologia.clinica.domain;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Una pieza del periodontograma (RN-CL-05): permanente, y ausente sin datos o presente con sus 6 sitios, movilidad
 * de 0 a 3 y, solo si es multirradicular, furcación opcional de 0 a 3. El constructor valida.
 */
public record PiezaPeriodontal(PiezaDental pieza, boolean ausente, Integer movilidad, Integer furcacion,
                               List<MedicionSitio> sitios) {

    private static final int GRADO_MINIMO = 0;
    private static final int GRADO_MAXIMO = 3;

    public PiezaPeriodontal {
        Objects.requireNonNull(pieza, "pieza");
        sitios = List.copyOf(sitios == null ? List.of() : sitios);
        if (!pieza.esPermanente()) {
            throw new ClinicaException(CodigoClinica.PIEZA_INVALIDA,
                    "La pieza " + pieza.numero() + " no es permanente: el periodontograma solo acepta 11 a 48");
        }
        if (ausente) {
            validarAusente(movilidad, furcacion, sitios);
        } else {
            validarPresente(pieza, movilidad, furcacion, sitios);
        }
    }

    /**
     * @throws ClinicaException {@code PIEZA_INVALIDA}, {@code SITIOS_INCOMPLETOS}, {@code FURCACION_NO_APLICA} o
     *                          {@code DATOS_INVALIDOS}
     */
    public static PiezaPeriodontal de(PiezaDental pieza, boolean ausente, Integer movilidad, Integer furcacion,
                                      List<MedicionSitio> sitios) {
        return new PiezaPeriodontal(pieza, ausente, movilidad, furcacion, sitios);
    }

    /** Estado calculado por el peor sitio, o {@code null} si la pieza está ausente (RN-CL-06). */
    public EstadoPeriodontal estado() {
        return ausente ? null : EstadoPeriodontal.de(sitios);
    }

    private static void validarAusente(Integer movilidad, Integer furcacion, List<MedicionSitio> sitios) {
        if (movilidad != null || furcacion != null || !sitios.isEmpty()) {
            throw new ClinicaException(CodigoClinica.DATOS_INVALIDOS,
                    "Una pieza ausente no lleva sitios, movilidad ni furcación");
        }
    }

    private static void validarPresente(PiezaDental pieza, Integer movilidad, Integer furcacion,
                                        List<MedicionSitio> sitios) {
        Set<SitioPeriodontal> distintos = EnumSet.noneOf(SitioPeriodontal.class);
        sitios.forEach(s -> distintos.add(s.sitio()));
        int cantidad = SitioPeriodontal.values().length;
        if (movilidad == null || sitios.size() != cantidad || distintos.size() != cantidad) {
            throw new ClinicaException(CodigoClinica.SITIOS_INCOMPLETOS,
                    "La pieza " + pieza.numero() + " tiene que llevar los 6 sitios una vez cada uno y la movilidad");
        }
        if (fueraDeGrado(movilidad)) {
            throw new ClinicaException(CodigoClinica.DATOS_INVALIDOS, "La movilidad tiene que estar entre 0 y 3");
        }
        if (furcacion != null && !pieza.esMultirradicular()) {
            throw new ClinicaException(CodigoClinica.FURCACION_NO_APLICA,
                    "La pieza " + pieza.numero() + " no es multirradicular: no lleva furcación");
        }
        if (furcacion != null && fueraDeGrado(furcacion)) {
            throw new ClinicaException(CodigoClinica.DATOS_INVALIDOS, "La furcación tiene que estar entre 0 y 3");
        }
    }

    private static boolean fueraDeGrado(int grado) {
        return grado < GRADO_MINIMO || grado > GRADO_MAXIMO;
    }
}
