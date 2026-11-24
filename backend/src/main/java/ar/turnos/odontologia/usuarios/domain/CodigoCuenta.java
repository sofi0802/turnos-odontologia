package ar.turnos.odontologia.usuarios.domain;

/**
 * Códigos estables de rechazo de las operaciones sobre cuentas del personal (C-41 design D7). Van en el
 * ProblemDetail como {@code codigo}, con el estado HTTP y el detalle de cada uno.
 */
public enum CodigoCuenta {
    USUARIO_EXISTENTE(409, "Ya existe una cuenta con ese usuario"),
    PROFESIONAL_CON_CUENTA(409, "Ese profesional ya tiene una cuenta"),
    PROFESIONAL_NO_DISPONIBLE(409, "El profesional no existe o está inactivo"),
    OPERACION_SOBRE_CUENTA_PROPIA(409, "No podés hacer esto con tu propia cuenta"),
    USUARIO_INEXISTENTE(404, "No existe esa cuenta del personal"),
    CONTRASENA_ACTUAL_INCORRECTA(422, "La contraseña actual no es correcta");

    private final int estado;
    private final String detalle;

    CodigoCuenta(int estado, String detalle) {
        this.estado = estado;
        this.detalle = detalle;
    }

    public int estado() {
        return estado;
    }

    public String detalle() {
        return detalle;
    }
}
