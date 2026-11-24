package ar.turnos.odontologia.coberturas.application;

/**
 * Puerto de lectura propio sobre los pacientes (C-18, design D1): el módulo de coberturas solo necesita saber si el
 * paciente existe, sin depender del módulo de usuarios.
 */
public interface PacientesExistentes {

    boolean existe(Long pacienteId);
}
