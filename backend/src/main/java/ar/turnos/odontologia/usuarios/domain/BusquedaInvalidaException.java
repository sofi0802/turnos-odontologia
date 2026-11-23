package ar.turnos.odontologia.usuarios.domain;

/** El término de búsqueda de pacientes no cumple las reglas. El mensaje es fijo y nunca repite el término. */
public class BusquedaInvalidaException extends RuntimeException {

    public static final String MENSAJE_CORTO = "Escribí al menos 2 letras o 3 números del DNI.";
    public static final String MENSAJE_LARGO = "El texto es demasiado largo.";

    public BusquedaInvalidaException(String mensaje) {
        super(mensaje);
    }
}
