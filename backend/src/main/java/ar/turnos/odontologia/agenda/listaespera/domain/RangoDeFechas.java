package ar.turnos.odontologia.agenda.listaespera.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Rango de fechas preferido de una inscripción (RN-LE-01): ambos días incluidos. */
public record RangoDeFechas(LocalDate desde, LocalDate hasta) {

    /** Días que puede abarcar un rango, contados desde {@code desde}. */
    public static final int MAXIMO_DIAS = 60;

    public RangoDeFechas {
        Objects.requireNonNull(desde, "desde");
        Objects.requireNonNull(hasta, "hasta");
    }

    /**
     * Valida un rango nuevo: desde hoy o después, que no termine antes de empezar y de 60 días como máximo.
     *
     * @throws ListaEsperaException {@code DATOS_INVALIDOS}
     */
    public static RangoDeFechas validado(LocalDate desde, LocalDate hasta, LocalDate hoy) {
        if (desde == null || hasta == null) {
            throw invalido("Las fechas son obligatorias");
        }
        if (desde.isBefore(hoy)) {
            throw invalido("El rango no puede empezar antes de hoy");
        }
        if (hasta.isBefore(desde)) {
            throw invalido("El rango no puede terminar antes de empezar");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) > MAXIMO_DIAS) {
            throw invalido("El rango puede ser de hasta 60 días");
        }
        return new RangoDeFechas(desde, hasta);
    }

    public boolean contiene(LocalDate dia) {
        return !dia.isBefore(desde) && !dia.isAfter(hasta);
    }

    private static ListaEsperaException invalido(String mensaje) {
        return new ListaEsperaException(CodigoListaEspera.DATOS_INVALIDOS, mensaje);
    }
}
