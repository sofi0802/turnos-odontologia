package ar.turnos.odontologia.clinica.application;

/**
 * Puerto de salida: dónde vive el contenido de las imágenes clínicas (C-26, DD-17, design D2). La clave la genera el
 * adaptador y no contiene datos del paciente. Hoy hay un adaptador en disco; uno compatible con S3 es una extensión.
 */
public interface AlmacenArchivos {

    /** Guarda el contenido y devuelve su clave. */
    String guardar(byte[] contenido);

    /**
     * @throws java.io.UncheckedIOException si la clave no existe o no se puede leer
     */
    byte[] leer(String clave);

    /** Borra el contenido si existe. Solo para compensar una carga que no llegó a guardarse en la base (design D3). */
    void borrar(String clave);
}
