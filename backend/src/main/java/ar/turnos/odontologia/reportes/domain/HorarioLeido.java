package ar.turnos.odontologia.reportes.domain;

/** Franja de un horario de trabajo: {@code diaSemana} va de 1 (lunes) a 7 (domingo), como {@code DayOfWeek}. */
public record HorarioLeido(long profesionalId, int diaSemana, java.time.LocalTime desde, java.time.LocalTime hasta) {
}
