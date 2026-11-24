package ar.turnos.odontologia.clinica.domain;

/**
 * Códigos estables de los errores del módulo clínico (C-13 y C-25). Todos son 422 salvo
 * {@code RECURSO_NO_ENCONTRADO}, que es 404.
 */
public enum CodigoClinica {
    PIEZA_INVALIDA,
    CARA_INVALIDA,
    DATOS_INVALIDOS,
    RECURSO_NO_ENCONTRADO,
    /** C-25: una pieza presente del periodontograma sin sus 6 sitios únicos o sin movilidad (RN-CL-05). */
    SITIOS_INCOMPLETOS,
    /** C-25: furcación en una pieza que no es multirradicular (RN-CL-05). */
    FURCACION_NO_APLICA
}
