package ar.turnos.odontologia.reportes.domain;

import java.util.List;

public record Ausencias(AusenciasPacientes pacientes, List<AusenciaProfesional> profesionales) {
}
