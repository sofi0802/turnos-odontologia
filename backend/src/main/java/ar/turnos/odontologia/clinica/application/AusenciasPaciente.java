package ar.turnos.odontologia.clinica.application;

import java.time.Instant;
import java.util.List;

/**
 * Puerto de lectura (C-13, design D7): los turnos en los que el paciente fue marcado como ausente. El estado del
 * turno ya es el registro; no hay una tabla de ausencias del paciente.
 */
public interface AusenciasPaciente {

    /** Del más nuevo al más viejo. */
    List<Ausencia> de(Long pacienteId);

    record Ausencia(Long turnoId, Instant inicio, Long profesionalId, String profesional) {}
}
