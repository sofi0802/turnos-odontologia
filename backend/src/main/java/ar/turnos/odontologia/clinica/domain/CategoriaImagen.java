package ar.turnos.odontologia.clinica.domain;

/** Categoría de una imagen clínica (C-26, RN-CL-07). */
public enum CategoriaImagen {
    RADIOGRAFIA,
    FOTOGRAFIA,
    ESTUDIO;

    /**
     * @throws ClinicaException {@code DATOS_INVALIDOS} si falta o no es una categoría conocida, sin repetir el valor
     */
    public static CategoriaImagen de(String valor) {
        if (valor != null) {
            for (CategoriaImagen categoria : values()) {
                if (categoria.name().equals(valor)) {
                    return categoria;
                }
            }
        }
        throw new ClinicaException(CodigoClinica.DATOS_INVALIDOS, "La categoría de la imagen no es válida");
    }
}
