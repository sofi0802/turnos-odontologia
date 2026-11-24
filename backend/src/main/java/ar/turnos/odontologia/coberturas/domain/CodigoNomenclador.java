package ar.turnos.odontologia.coberturas.domain;

/** Código del nomenclador de prácticas, opcionalmente vinculado a una prestación del consultorio (RN-OS-06). */
public record CodigoNomenclador(Long id, String codigo, String descripcion, Long prestacionId, boolean activo) {

    public CodigoNomenclador conDatos(String codigoNuevo, String descripcionNueva, Long prestacionNueva) {
        return new CodigoNomenclador(id, codigoNuevo, descripcionNueva, prestacionNueva, activo);
    }

    public CodigoNomenclador conActivo(boolean estado) {
        return new CodigoNomenclador(id, codigo, descripcion, prestacionId, estado);
    }
}
