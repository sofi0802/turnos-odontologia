package ar.turnos.odontologia.usuarios.api;

import ar.turnos.odontologia.usuarios.domain.PacienteResumen;

/** Resultado de la búsqueda de pacientes: solo lo necesario para elegir uno (Ley 25.326). */
public record PacienteResumenResponse(Long id, String nombre, String apellido, String dni) {

    static PacienteResumenResponse de(PacienteResumen paciente) {
        return new PacienteResumenResponse(paciente.id(), paciente.nombre(), paciente.apellido(), paciente.dni());
    }

    /** Sin datos personales en el texto, por si un log lo imprime. */
    @Override
    public String toString() {
        return "PacienteResumenResponse[id=" + id + "]";
    }
}
