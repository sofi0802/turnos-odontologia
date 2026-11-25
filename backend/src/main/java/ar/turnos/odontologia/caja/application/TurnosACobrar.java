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

    record TurnoACobrar(long turnoId, long pacienteId, long prestacionId, LocalDate fecha, BigDecimal precio,
                        Long afiliacionId, List<CodigoActivo> codigosActivos, Map<Long, Integer> porcentajesDelPlan) {}
}
