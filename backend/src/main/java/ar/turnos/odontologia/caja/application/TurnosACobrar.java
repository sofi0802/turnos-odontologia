package ar.turnos.odontologia.caja.application;

import ar.turnos.odontologia.caja.domain.CodigoActivo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Puerto de lectura de los turnos {@code ATENDIDO} sin cargo y con arancel, con lo que hace falta para su tarifa
 * (design D2 y D3). Solo lee tablas de {@code agenda/} y {@code coberturas/}.
 */
public interface TurnosACobrar {

    List<TurnoACobrar> atendidosSinCargo();

    /**
     * C-23: la tarifa de un turno en cualquier estado, para calcular su seña al reservar (RN-SE-03). Vacío si el turno
     * no existe o su prestación no tiene arancel.
     */
    java.util.Optional<TurnoACobrar> tarifaDe(long turnoId);

    record TurnoACobrar(long turnoId, long pacienteId, long prestacionId, LocalDate fecha, BigDecimal precio,
                        Long afiliacionId, List<CodigoActivo> codigosActivos, Map<Long, Integer> porcentajesDelPlan) {}
}
