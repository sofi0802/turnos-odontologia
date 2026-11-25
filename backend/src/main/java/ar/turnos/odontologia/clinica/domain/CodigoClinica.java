package ar.turnos.odontologia.clinica.domain;

/**
 * Códigos estables de los errores del módulo clínico (C-13, C-25, C-26 y C-27). Todos son 422 salvo
 * {@code RECURSO_NO_ENCONTRADO} (404), {@code ARCHIVO_DEMASIADO_GRANDE} (413), {@code IMAGEN_YA_ANULADA} (409) y los
 * conflictos de los consentimientos (409).
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
    IMAGEN_YA_ANULADA,
    /** C-27: ya hay una plantilla con ese título, sin distinguir mayúsculas (RN-CL-09). 409. */
    PLANTILLA_DUPLICADA,
    /** C-27: la versión indicada al editar o al firmar no es la última de la plantilla (RN-CL-09). 409. */
    VERSION_DESACTUALIZADA,
    /** C-27: firma sobre una plantilla desactivada (RN-CL-09). 409. */
    PLANTILLA_INACTIVA,
    /** C-27: el consentimiento ya tiene su revocación (RN-CL-11). 409. */
    CONSENTIMIENTO_YA_REVOCADO,
    /** C-27: el DNI escrito por el firmante no es el del paciente (RN-CL-10). 422. */
    FIRMANTE_NO_COINCIDE
}
