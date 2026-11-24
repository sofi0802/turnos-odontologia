package ar.turnos.odontologia.clinica.domain;

import java.time.LocalDate;

/**
 * Metadatos validados de una imagen clínica nueva (C-26, RN-CL-07) y las reglas de su anulación (RN-CL-08). Los
 * mensajes de error son fijos: nunca repiten la descripción, el motivo, la categoría ni la pieza (design D9).
 *
 * @param pieza número FDI validado, o {@code null} si la imagen no es de una pieza
 */
public record ImagenClinica(CategoriaImagen categoria, Integer pieza, LocalDate tomadaEl, String descripcion,
                            TipoArchivoClinico tipo, int tamanoBytes) {

    /** 10 MB. */
    public static final int TAMANO_MAXIMO = 10 * 1024 * 1024;
    public static final int LARGO_MAXIMO_TEXTO = 200;

    /**
     * Valida el pedido completo antes de guardar nada.
     *
     * @param hoy la fecha actual en la zona del consultorio
     * @throws ClinicaException {@code DATOS_INVALIDOS}, {@code ARCHIVO_DEMASIADO_GRANDE},
     *                          {@code TIPO_ARCHIVO_NO_PERMITIDO} o {@code PIEZA_INVALIDA}
     */
    public static ImagenClinica nueva(String categoria, Integer pieza, LocalDate tomadaEl, String descripcion,
                                      byte[] contenido, LocalDate hoy) {
        if (contenido == null || contenido.length == 0) {
            throw invalido("Falta el archivo o está vacío");
        }
        if (contenido.length > TAMANO_MAXIMO) {
            throw new ClinicaException(CodigoClinica.ARCHIVO_DEMASIADO_GRANDE, "El archivo supera los 10 MB");
        }
        TipoArchivoClinico tipo = TipoArchivoClinico.detectar(contenido);
        CategoriaImagen laCategoria = CategoriaImagen.de(categoria);
        if (tomadaEl == null || tomadaEl.isAfter(hoy)) {
            throw invalido("La fecha de la toma es obligatoria y no puede ser futura");
        }
        Integer laPieza = pieza == null ? null : piezaValida(pieza);
        String laDescripcion = textoOpcional(descripcion);
        if (laDescripcion != null && laDescripcion.length() > LARGO_MAXIMO_TEXTO) {
            throw invalido("La descripción supera los 200 caracteres");
        }
        return new ImagenClinica(laCategoria, laPieza, tomadaEl, laDescripcion, tipo, contenido.length);
    }

    /**
     * @return el motivo sin espacios en los extremos
     * @throws ClinicaException {@code DATOS_INVALIDOS} si falta, está en blanco o supera los 200 caracteres
     */
    public static String motivoDeAnulacion(String motivo) {
        String elMotivo = textoOpcional(motivo);
        if (elMotivo == null || elMotivo.length() > LARGO_MAXIMO_TEXTO) {
            throw invalido("El motivo de la anulación es obligatorio y tiene hasta 200 caracteres");
        }
        return elMotivo;
    }

    private static Integer piezaValida(int numero) {
        try {
            return PiezaDental.de(numero).numero();
        } catch (ClinicaException e) {
            // Mensaje fijo: la pieza de una imagen no sale en los errores (spec, privacidad).
            throw new ClinicaException(CodigoClinica.PIEZA_INVALIDA, "La pieza no existe en la notación FDI");
        }
    }

    private static String textoOpcional(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.strip();
    }

    private static ClinicaException invalido(String mensaje) {
        return new ClinicaException(CodigoClinica.DATOS_INVALIDOS, mensaje);
    }
}
