package ar.turnos.odontologia.clinica.domain;

/**
 * Los 6 sitios de sondaje de una pieza (RN-CL-05): tres vestibulares y tres linguales o palatinos. En las piezas
 * superiores {@code ML}, {@code L} y {@code DL} son los sitios palatinos.
 */
public enum SitioPeriodontal {
    MV,
    V,
    DV,
    ML,
    L,
    DL;

    /** @throws ClinicaException {@code DATOS_INVALIDOS} si el sitio no es uno de los 6 */
    public static SitioPeriodontal de(String sitio) {
        if (sitio != null) {
            for (SitioPeriodontal valor : values()) {
                if (valor.name().equals(sitio)) {
                    return valor;
                }
            }
        }
        throw new ClinicaException(CodigoClinica.DATOS_INVALIDOS, "El sitio no es uno de los 6 del periodontograma");
    }
}
