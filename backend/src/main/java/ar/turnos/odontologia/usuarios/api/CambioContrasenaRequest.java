package ar.turnos.odontologia.usuarios.api;

import jakarta.validation.constraints.NotBlank;

/** Cambio de la propia contraseña (C-41). */
public record CambioContrasenaRequest(
        @NotBlank(message = "Escribí la contraseña actual") String actual,
        @NotBlank(message = "Escribí la contraseña nueva") @ContrasenaValida String nueva) {

    /** Nunca incluye las contraseñas. */
    @Override
    public String toString() {
        return "CambioContrasenaRequest[actual=***, nueva=***]";
    }
}
