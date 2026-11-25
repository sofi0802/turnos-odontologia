package ar.turnos.odontologia.usuarios.application;

/**
 * Puerto de salida (C-17, design D3): el registro avisa que el paciente marcó la casilla de mensajes. Lo implementa
 * el módulo de notificaciones, que guarda el consentimiento con su historial dentro de la misma transacción.
 */
public interface ConsentimientoInicial {

    void otorgadoAlRegistrarse(long pacienteId);
}
