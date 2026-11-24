package ar.turnos.odontologia.clinica.domain;

/**
 * Códigos estables de los errores del módulo clínico (C-13, C-25 y C-26). Todos son 422 salvo
 * {@code RECURSO_NO_ENCONTRADO} (404), {@code ARCHIVO_DEMASIADO_GRANDE} (413) e {@code IMAGEN_YA_ANULADA} (409).
 */
public enum CodigoClinica {
    PIEZA_INVALIDA,
    CARA_INVALIDA,
    DATOS_INVALIDOS,
    RECURSO_NO_ENCONTRADO,
    /** C-25: una pieza presente del periodontograma sin sus 6 sitios únicos o sin movilidad (RN-CL-05). */
    SITIOS_INCOMPLETOS,
    /** C-25: furcación en una pieza que no es multirradicular (RN-CL-05). */
    FURCACION_NO_APLICA,
    /** C-26: el contenido del archivo no es JPEG, PNG ni PDF (RN-CL-07). 422. */
    TIPO_ARCHIVO_NO_PERMITIDO,
    /** C-26: el archivo supera los 10 MB (RN-CL-07). 413. */
    ARCHIVO_DEMASIADO_GRANDE,
    /** C-26: la imagen ya estaba anulada (RN-CL-08). 409. */
    IMAGEN_YA_ANULADA
}
