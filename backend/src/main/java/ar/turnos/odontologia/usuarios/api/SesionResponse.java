package ar.turnos.odontologia.usuarios.api;

import ar.turnos.odontologia.usuarios.domain.Rol;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Estado de la sesión. Sin sesión, {@code username} y {@code rol} no se incluyen. {@code profesionalId}
 * solo se informa para el rol {@code ODONTOLOGO} (RN-AG-18, C-35).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SesionResponse(boolean autenticado, String username, Rol rol, Long profesionalId) {

    public static SesionResponse sinSesion() {
        return new SesionResponse(false, null, null, null);
    }

    public static SesionResponse de(String username, Rol rol, Long profesionalId) {
        return new SesionResponse(true, username, rol, rol == Rol.ODONTOLOGO ? profesionalId : null);
    }
}
