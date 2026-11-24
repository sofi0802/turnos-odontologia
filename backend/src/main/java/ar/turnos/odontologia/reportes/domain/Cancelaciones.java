package ar.turnos.odontologia.reportes.domain;

/** Turnos cancelados del período por quién canceló (RN-RE-07, design D5). */
public record Cancelaciones(int porPaciente, int porConsultorio, int porReprogramacion, int sinDato) {
}
