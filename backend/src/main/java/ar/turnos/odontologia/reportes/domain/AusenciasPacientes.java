package ar.turnos.odontologia.reportes.domain;

/** @param tasa ausentes / (atendidos + ausentes) en entero; {@code null} si no hay ninguno (RN-RE-06) */
public record AusenciasPacientes(int ausentes, int atendidos, Integer tasa, int sinRegistrar) {
}
