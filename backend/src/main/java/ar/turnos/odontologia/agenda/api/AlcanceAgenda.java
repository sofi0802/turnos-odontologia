package ar.turnos.odontologia.agenda.api;

import ar.turnos.odontologia.usuarios.domain.Rol;

/**
 * Qué parte de la agenda puede ver la sesión (RN-AG-18, C-35).
 *
 * <p>El odontólogo solo ve los turnos de su profesional, sin importar el {@code profesionalId} que pida.
 * Si no tuviera un profesional vinculado, no ve ninguno (denegación por defecto, DD-10). Recepción y
 * administración usan el filtro del pedido; manda el rol, no el vínculo.
 *
 * @param sinTurnos     {@code true} si la sesión no puede ver ningún turno
 * @param profesionalId el profesional por el que filtrar, o {@code null} para todos
 */
record AlcanceAgenda(boolean sinTurnos, Long profesionalId) {

    static AlcanceAgenda para(Rol rol, Long profesionalDelUsuario, Long profesionalPedido) {
        if (rol != Rol.ODONTOLOGO) {
            return new AlcanceAgenda(false, profesionalPedido);
        }
        if (profesionalDelUsuario == null) {
            return new AlcanceAgenda(true, null);
        }
        return new AlcanceAgenda(false, profesionalDelUsuario);
    }
}
