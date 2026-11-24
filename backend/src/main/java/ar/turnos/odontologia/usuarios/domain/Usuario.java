package ar.turnos.odontologia.usuarios.domain;

/**
 * Cuenta de acceso. {@code id} es nulo hasta que se guarda; {@code passwordHash} es siempre un hash BCrypt.
 * Un usuario PACIENTE tiene {@code pacienteId}; un ODONTOLOGO tiene {@code profesionalId}.
 * {@code debeCambiarContrasena} marca una contraseña temporal que el usuario tiene que reemplazar (C-41).
 */
public record Usuario(
        Long id,
        NombreUsuario username,
        String passwordHash,
        Rol rol,
        Long profesionalId,
        Long pacienteId,
        boolean activo,
        boolean debeCambiarContrasena) {

    /** Cuenta con una contraseña definitiva (sin marca de temporal). */
    public Usuario(Long id, NombreUsuario username, String passwordHash, Rol rol, Long profesionalId, Long pacienteId,
                   boolean activo) {
        this(id, username, passwordHash, rol, profesionalId, pacienteId, activo, false);
    }

    public Usuario conActivo(boolean nuevoActivo) {
        return new Usuario(id, username, passwordHash, rol, profesionalId, pacienteId, nuevoActivo, debeCambiarContrasena);
    }

    public Usuario conContrasena(String nuevoHash, boolean temporal) {
        return new Usuario(id, username, nuevoHash, rol, profesionalId, pacienteId, activo, temporal);
    }
}
