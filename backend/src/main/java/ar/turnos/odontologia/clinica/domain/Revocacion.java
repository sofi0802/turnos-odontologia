package ar.turnos.odontologia.clinica.domain;

/**
 * Reglas de la revocación de un consentimiento (C-27, RN-CL-11). La revocación se guarda aparte del documento firmado,
 * que no cambia y sigue verificando (design D5). El mensaje de error es fijo: nunca repite el motivo (design D11).
 */
public final class Revocacion {

    public static final int LARGO_MAXIMO_MOTIVO = 200;

    private Revocacion() {
    }

    /**
     * @return el motivo sin espacios en los extremos
     * @throws ClinicaException {@code DATOS_INVALIDOS} si falta, está en blanco o supera los 200 caracteres
     */
    public static String motivo(String motivo) {
        String elMotivo = motivo == null ? "" : motivo.strip();
        if (elMotivo.isEmpty() || elMotivo.codePointCount(0, elMotivo.length()) > LARGO_MAXIMO_MOTIVO) {
            throw new ClinicaException(CodigoClinica.DATOS_INVALIDOS,
                    "El motivo de la revocación es obligatorio y tiene hasta 200 caracteres");
        }
        return elMotivo;
    }
}
