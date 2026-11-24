package ar.turnos.odontologia.coberturas.domain;

/** Obra social o prepaga del catálogo (RN-OS-01). Se desactiva, nunca se borra. */
public record ObraSocial(Long id, String nombre, String sigla, TipoCobertura tipo, boolean activa) {

    public ObraSocial conDatos(String nombreNuevo, String siglaNueva, TipoCobertura tipoNuevo) {
        return new ObraSocial(id, nombreNuevo, siglaNueva, tipoNuevo, activa);
    }

    public ObraSocial conActiva(boolean estado) {
        return new ObraSocial(id, nombre, sigla, tipo, estado);
    }
}
