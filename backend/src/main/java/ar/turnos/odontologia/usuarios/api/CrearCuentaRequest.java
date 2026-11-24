package ar.turnos.odontologia.usuarios.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta de una cuenta del personal (C-41). El rol llega como texto para responder 422 con el campo cuando es
 * desconocido o es PACIENTE. No lleva contraseña: la genera el servidor.
 */
public record CrearCuentaRequest(
        @NotBlank(message = "Escribí el usuario") @Size(min = 3, max = 150, message = "El usuario debe tener entre 3 y 150 caracteres") String username,
        String rol,
        Long profesionalId) {
}
