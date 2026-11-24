package ar.turnos.odontologia.coberturas.domain;

/** Plan de una obra social (RN-OS-02). Un plan no cambia de obra social. */
public record Plan(Long id, Long obraSocialId, String nombre, boolean activo) {

    public Plan conNombre(String nombreNuevo) {
        return new Plan(id, obraSocialId, nombreNuevo, activo);
    }

    public Plan conActivo(boolean estado) {
        return new Plan(id, obraSocialId, nombre, estado);
    }
}
