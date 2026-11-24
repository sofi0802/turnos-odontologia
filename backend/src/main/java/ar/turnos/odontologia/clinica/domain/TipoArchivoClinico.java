package ar.turnos.odontologia.clinica.domain;

/**
 * Tipos de archivo que acepta la historia clínica (C-26, RN-CL-07). Se reconocen por la firma de los primeros bytes,
 * nunca por la extensión ni por el tipo que declara el cliente (design D4).
 */
public enum TipoArchivoClinico {

    JPEG("image/jpeg", "jpg", new int[] {0xFF, 0xD8, 0xFF}),
    PNG("image/png", "png", new int[] {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
    PDF("application/pdf", "pdf", new int[] {0x25, 0x50, 0x44, 0x46, 0x2D});

    private final String tipoContenido;
    private final String extension;
    private final int[] firma;

    TipoArchivoClinico(String tipoContenido, String extension, int[] firma) {
        this.tipoContenido = tipoContenido;
        this.extension = extension;
        this.firma = firma;
    }

    public String tipoContenido() {
        return tipoContenido;
    }

    public String extension() {
        return extension;
    }

    /**
     * @throws ClinicaException {@code TIPO_ARCHIVO_NO_PERMITIDO} si el contenido no empieza con ninguna firma conocida
     */
    public static TipoArchivoClinico detectar(byte[] contenido) {
        for (TipoArchivoClinico tipo : values()) {
            if (tipo.coincide(contenido)) {
                return tipo;
            }
        }
        throw new ClinicaException(CodigoClinica.TIPO_ARCHIVO_NO_PERMITIDO,
                "Solo se aceptan archivos JPEG, PNG o PDF");
    }

    /** El tipo guardado en la base, por su {@code Content-Type}. */
    public static TipoArchivoClinico deTipoContenido(String tipoContenido) {
        for (TipoArchivoClinico tipo : values()) {
            if (tipo.tipoContenido.equals(tipoContenido)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de contenido desconocido");
    }

    private boolean coincide(byte[] contenido) {
        if (contenido == null || contenido.length < firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if ((contenido[i] & 0xFF) != firma[i]) {
                return false;
            }
        }
        return true;
    }
}
