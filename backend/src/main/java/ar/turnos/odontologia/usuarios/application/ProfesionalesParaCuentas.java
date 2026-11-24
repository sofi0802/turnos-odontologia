package ar.turnos.odontologia.usuarios.application;

/**
 * Puerto de solo lectura sobre los profesionales, para validar el vínculo de una cuenta (C-41 design D6).
 * Evita depender del módulo configuracion/.
 */
public interface ProfesionalesParaCuentas {

    /** {@code true} si el profesional existe y está activo. */
    boolean estaActivo(long profesionalId);
}
