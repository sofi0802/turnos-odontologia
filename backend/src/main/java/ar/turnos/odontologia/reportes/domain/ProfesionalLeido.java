package ar.turnos.odontologia.reportes.domain;

/** Profesional con su sillón habitual, que puede ser {@code null}. */
public record ProfesionalLeido(long id, String nombre, String apellido, boolean activo, Long sillonHabitualId) {

    String nombreCompleto() {
        return nombre + " " + apellido;
    }
}
