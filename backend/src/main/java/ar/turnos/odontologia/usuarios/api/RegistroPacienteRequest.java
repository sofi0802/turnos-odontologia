package ar.turnos.odontologia.usuarios.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistroPacienteRequest(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 100) String apellido,
        @NotBlank @Pattern(regexp = "\\d{7,8}", message = "El DNI debe tener 7 u 8 dígitos") String dni,
        @NotBlank @Email @Size(max = 150) String email,
        @Size(max = 30) String telefono,
        @NotBlank @ContrasenaValida String password,
        Boolean aceptaMensajes) {

    /** La casilla de mensajes (C-17): si no viene, no acepta. */
    public boolean aceptaLosMensajes() {
        return Boolean.TRUE.equals(aceptaMensajes);
    }

    /** Nunca incluye la contraseña. */
    @Override
    public String toString() {
        return "RegistroPacienteRequest[dni=" + dni + ", email=" + email + ", password=***]";
    }
}
