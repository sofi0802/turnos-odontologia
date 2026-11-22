package ar.turnos.odontologia.clinica.domain;

/**
 * Cara de una pieza: oclusal, mesial, distal, vestibular, lingual o palatina. La lingual solo existe en las piezas
 * inferiores y la palatina solo en las superiores.
 */
public enum CaraDental {
    O,
    M,
    D,
    V,
    L,
    P;

    /**
     * @param cara la letra recibida, o {@code null} para la pieza completa
     * @return la cara validada contra la pieza, o {@code null} si no se indicó
     * @throws ClinicaException {@code CARA_INVALIDA} si la letra no existe o no corresponde al cuadrante
     */
    public static CaraDental para(String cara, PiezaDental pieza) {
        if (cara == null) {
            return null;
        }
        CaraDental valor;
        try {
            valor = CaraDental.valueOf(cara);
        } catch (IllegalArgumentException e) {
            throw invalida(pieza);
        }
        if ((valor == L && pieza.esSuperior()) || (valor == P && !pieza.esSuperior())) {
            throw invalida(pieza);
        }
        return valor;
    }

    private static ClinicaException invalida(PiezaDental pieza) {
        return new ClinicaException(CodigoClinica.CARA_INVALIDA,
                "La cara no es válida para la pieza " + pieza.numero());
    }
}
