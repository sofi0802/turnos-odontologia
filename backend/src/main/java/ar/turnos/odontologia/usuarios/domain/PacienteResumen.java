package ar.turnos.odontologia.usuarios.domain;

/** Datos mínimos de un paciente para elegirlo en la agenda: sin contacto, consentimiento ni salud (Ley 25.326). */
public record PacienteResumen(Long id, String nombre, String apellido, String dni) {

    /** Sin datos: un resumen que termine en un log por accidente no filtra al paciente. */
    @Override
    public String toString() {
        return "PacienteResumen[id=" + id + "]";
    }
}
