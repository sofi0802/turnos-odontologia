package ar.turnos.odontologia.reportes.domain;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Período de un reporte (RN-RE-03): fechas del consultorio, con el primer y el último día incluidos, de hasta
 * {@value #MAXIMO_DIAS} días.
 */
public record Periodo(LocalDate desde, LocalDate hasta) {

    public static final int MAXIMO_DIAS = 92;

    public Periodo {
        Objects.requireNonNull(desde, "desde");
        Objects.requireNonNull(hasta, "hasta");
        if (hasta.isBefore(desde)) {
            throw new PeriodoInvalidoException("hasta", "La fecha final tiene que ser igual o posterior a la inicial.");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAXIMO_DIAS) {
            throw new PeriodoInvalidoException("hasta", "El período no puede superar los " + MAXIMO_DIAS + " días.");
        }
    }

    public static Periodo de(LocalDate desde, LocalDate hasta) {
        return new Periodo(desde, hasta);
    }

    /** Los días del período, en orden. */
    public List<LocalDate> dias() {
        return desde.datesUntil(hasta.plusDays(1)).toList();
    }

    /** {@code [desde 00:00, hasta + 1 día 00:00)} en la zona del consultorio. */
    public Intervalo intervalo(ZoneId zona) {
        return new Intervalo(desde.atStartOfDay(zona).toInstant(), hasta.plusDays(1).atStartOfDay(zona).toInstant());
    }
}
