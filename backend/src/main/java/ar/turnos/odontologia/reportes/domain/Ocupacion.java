package ar.turnos.odontologia.reportes.domain;

import java.util.List;

/** Con filtro de profesional, {@code sillones} va vacía (design D7). */
public record Ocupacion(List<FilaOcupacion> profesionales, List<FilaOcupacion> sillones) {
}
