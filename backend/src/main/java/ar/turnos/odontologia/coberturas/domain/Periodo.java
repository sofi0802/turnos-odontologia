package ar.turnos.odontologia.coberturas.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Período de una liquidación: un año y un mes (RN-LQ-01). Está cerrado cuando su último día es anterior a hoy en la
 * zona del consultorio; el último instante del mes (el día 31 a las 23:59) todavía pertenece al período.
 */
public record Periodo(YearMonth mes) {

    public Periodo {
        if (mes == null) {
            throw CoberturaException.datosInvalidos("periodo", "El período no es válido");
        }
    }

    public static Periodo de(YearMonth mes) {
        return new Periodo(mes);
    }

    /** Acepta solo {@code yyyy-MM}; el mensaje de error es fijo y no repite lo recibido. */
    public static Periodo de(String texto) {
        if (texto == null) {
            throw CoberturaException.datosInvalidos("periodo", "El período no es válido");
        }
        try {
            return new Periodo(YearMonth.parse(texto));
        } catch (DateTimeParseException e) {
            throw CoberturaException.datosInvalidos("periodo", "El período no es válido");
        }
    }

    public LocalDate primerDia() {
        return mes.atDay(1);
    }

    public LocalDate ultimoDia() {
        return mes.atEndOfMonth();
    }

    public boolean estaCerrado(LocalDate hoy) {
        return hoy.isAfter(ultimoDia());
    }

    /** La medianoche del mes siguiente en la zona dada: los turnos entran si empiezan antes de este instante. */
    public Instant finExclusivo(ZoneId zona) {
        return mes.plusMonths(1).atDay(1).atStartOfDay(zona).toInstant();
    }

    @Override
    public String toString() {
        return mes.toString();
    }
}
