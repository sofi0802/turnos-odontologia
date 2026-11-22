package ar.turnos.odontologia.reserva.domain;

import ar.turnos.odontologia.agenda.domain.reglas.FranjaHoraria;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.TreeSet;

/**
 * Inicios candidatos de un día (design D2): cada {@code paso} minutos desde el comienzo de cada franja del día de la
 * semana, mientras el turno completo quepa en la franja, sin inicios anteriores al momento actual.
 */
public final class GrillaHorarios {

    private GrillaHorarios() {}

    public static List<Instant> inicios(LocalDate fecha, ZoneId zona, List<FranjaHoraria> franjas, Duration duracion,
                                        Instant ahora, int pasoMinutos) {
        if (pasoMinutos <= 0) {
            throw new IllegalArgumentException("El paso de la grilla debe ser positivo");
        }
        TreeSet<Instant> inicios = new TreeSet<>();
        for (FranjaHoraria franja : franjas) {
            if (franja.dia() != fecha.getDayOfWeek()) {
                continue;
            }
            // en minutos del día para no desbordar la medianoche al sumar la duración
            long hasta = franja.hasta().toSecondOfDay() / 60L;
            long minutos = franja.desde().toSecondOfDay() / 60L;
            for (; minutos + duracion.toMinutes() <= hasta; minutos += pasoMinutos) {
                Instant inicio = fecha.atTime(LocalTime.ofSecondOfDay(minutos * 60L)).atZone(zona).toInstant();
                if (!inicio.isBefore(ahora)) {
                    inicios.add(inicio);
                }
            }
        }
        return List.copyOf(inicios);
    }
}
