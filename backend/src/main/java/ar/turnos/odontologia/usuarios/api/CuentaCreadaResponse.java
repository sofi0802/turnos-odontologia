package ar.turnos.odontologia.usuarios.api;

import ar.turnos.odontologia.usuarios.application.CuentasPersonal.CuentaConTemporal;
import ar.turnos.odontologia.usuarios.domain.Rol;

/**
 * Respuesta del alta y del restablecimiento: la cuenta más la contraseña temporal, que solo viaja acá
 * (C-41 design D2). Se envía con {@code Cache-Control: no-store}.
 */
public record CuentaCreadaResponse(long id, String username, Rol rol, Long profesionalId, boolean activo,
                                   boolean debeCambiarContrasena, String contrasenaTemporal) {

    static CuentaCreadaResponse de(CuentaConTemporal creada) {
        var cuenta = creada.cuenta();
        return new CuentaCreadaResponse(cuenta.id(), cuenta.username().valor(), cuenta.rol(), cuenta.profesionalId(),
                cuenta.activo(), cuenta.debeCambiarContrasena(), creada.contrasenaTemporal());
    }

    /** Nunca incluye la contraseña temporal. */
    @Override
    public String toString() {
        return "CuentaCreadaResponse[username=" + username + ", rol=" + rol + ", contrasenaTemporal=***]";
    }
}
