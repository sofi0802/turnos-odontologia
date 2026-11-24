package ar.turnos.odontologia.usuarios.api;

import ar.turnos.odontologia.usuarios.domain.Rol;
import ar.turnos.odontologia.usuarios.domain.Usuario;

/** Una cuenta del personal, sin contraseña ni hash (C-41, spec cuentas-personal). */
public record CuentaPersonalResponse(long id, String username, Rol rol, Long profesionalId, boolean activo,
                                     boolean debeCambiarContrasena) {

    static CuentaPersonalResponse de(Usuario usuario) {
        return new CuentaPersonalResponse(usuario.id(), usuario.username().valor(), usuario.rol(),
                usuario.profesionalId(), usuario.activo(), usuario.debeCambiarContrasena());
    }
}
