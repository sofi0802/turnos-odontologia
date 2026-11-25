package ar.turnos.odontologia.clinica.domain;

import java.text.Normalizer;

/**
 * Título y texto plano de una versión de plantilla de consentimiento, ya normalizados (C-27, RN-CL-09, design D3): los
 * saltos de línea {@code \r\n} y {@code \r} pasan a {@code \n}, todo queda en Unicode NFC y sin espacios en los
 * extremos. Así el texto que se copia al firmar y el que entra al hash son idénticos byte a byte. Los largos se cuentan
 * en caracteres (puntos de código), igual que {@code char_length} de PostgreSQL. Los mensajes de error son fijos:
 * nunca repiten el título ni el texto (design D11).
 */
public record VersionPlantilla(String titulo, String texto) {

    public static final int LARGO_MAXIMO_TITULO = 120;
    public static final int LARGO_MAXIMO_TEXTO = 20_000;

    /**
     * @throws ClinicaException {@code DATOS_INVALIDOS} si el título no tiene de 1 a 120 caracteres o el texto de 1 a
     *                          20 000, una vez normalizados
     */
    public static VersionPlantilla nueva(String titulo, String texto) {
        String elTitulo = normalizar(titulo);
        if (elTitulo.isEmpty() || largo(elTitulo) > LARGO_MAXIMO_TITULO) {
            throw new ClinicaException(CodigoClinica.DATOS_INVALIDOS,
                    "El título es obligatorio y tiene hasta 120 caracteres");
        }
        String elTexto = normalizar(texto);
        if (elTexto.isEmpty() || largo(elTexto) > LARGO_MAXIMO_TEXTO) {
            throw new ClinicaException(CodigoClinica.DATOS_INVALIDOS,
                    "El texto es obligatorio y tiene hasta 20 000 caracteres");
        }
        return new VersionPlantilla(elTitulo, elTexto);
    }

    /** Saltos de línea a {@code \n}, NFC y recorte de los extremos. {@code null} queda vacío. */
    static String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        String saltos = valor.replace("\r\n", "\n").replace('\r', '\n');
        return Normalizer.normalize(saltos, Normalizer.Form.NFC).strip();
    }

    static int largo(String valor) {
        return valor.codePointCount(0, valor.length());
    }
}
