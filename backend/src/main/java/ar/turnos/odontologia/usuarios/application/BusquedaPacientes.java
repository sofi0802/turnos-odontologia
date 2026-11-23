package ar.turnos.odontologia.usuarios.application;

import ar.turnos.odontologia.usuarios.domain.PacienteResumen;

import java.util.List;

/** Puerto de lectura de la búsqueda de pacientes. Solo devuelve los datos mínimos de {@link PacienteResumen}. */
public interface BusquedaPacientes {

    /** Pacientes cuyo DNI empieza con {@code prefijo}, ordenados por DNI. */
    List<PacienteResumen> porDni(String prefijo, int limite);

    /**
     * Pacientes en los que cada palabra aparece en el nombre o el apellido, ordenados por apellido, nombre e id.
     * Las palabras vienen normalizadas y escapadas para un {@code LIKE ... ESCAPE '\'}.
     */
    List<PacienteResumen> porTexto(List<String> palabrasParaLike, int limite);
}
