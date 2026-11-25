package ar.turnos.odontologia.agenda.listaespera.domain;

import java.util.Objects;

/** Rechazo de una operación de la lista de espera. El mensaje nunca lleva el token, la prestación ni datos de salud. */
public class ListaEsperaException extends RuntimeException {

    private final CodigoListaEspera codigo;

    public ListaEsperaException(CodigoListaEspera codigo, String mensaje) {
        super(mensaje);
        this.codigo = Objects.requireNonNull(codigo, "codigo");
    }

    public CodigoListaEspera codigo() {
        return codigo;
    }
}
