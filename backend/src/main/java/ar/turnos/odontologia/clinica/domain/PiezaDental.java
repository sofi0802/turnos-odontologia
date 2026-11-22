package ar.turnos.odontologia.clinica.domain;

/**
 * Pieza dental en notación FDI: el primer dígito es el cuadrante y el segundo, la posición. Permanentes en los
 * cuadrantes 1 a 4 (posiciones 1 a 8) y temporales en los 5 a 8 (posiciones 1 a 5).
 */
public record PiezaDental(int numero) {

    public PiezaDental {
        int cuadrante = numero / 10;
        int posicion = numero % 10;
        boolean permanente = cuadrante >= 1 && cuadrante <= 4 && posicion >= 1 && posicion <= 8;
        boolean temporal = cuadrante >= 5 && cuadrante <= 8 && posicion >= 1 && posicion <= 5;
        if (numero < 11 || numero > 85 || !(permanente || temporal)) {
            throw new ClinicaException(CodigoClinica.PIEZA_INVALIDA,
                    "La pieza " + numero + " no existe en la notación FDI");
        }
    }

    public static PiezaDental de(int numero) {
        return new PiezaDental(numero);
    }

    public int cuadrante() {
        return numero / 10;
    }

    /** Cuadrantes 1, 2, 5 y 6: maxilar superior. */
    public boolean esSuperior() {
        int cuadrante = cuadrante();
        return cuadrante == 1 || cuadrante == 2 || cuadrante == 5 || cuadrante == 6;
    }
}
