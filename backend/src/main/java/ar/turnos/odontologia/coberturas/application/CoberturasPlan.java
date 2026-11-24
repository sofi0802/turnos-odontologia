package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.domain.Porcentaje;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Puerto del porcentaje de cobertura de cada plan por código del nomenclador (C-19, design D6): una fila por plan y
 * código, que se reemplaza (upsert).
 */
public interface CoberturasPlan {

    /** La cobertura cargada del plan, ordenada por código sin distinguir mayúsculas. */
    List<CoberturaFila> delPlan(Long planId);

    /** Carga o reemplaza el porcentaje en una sola sentencia; vacío si el código del nomenclador no existe. */
    Optional<CoberturaFila> guardar(Long planId, Long nomencladorId, Porcentaje porcentaje);

    /** El porcentaje cargado del plan para cada código pedido; un código sin fila no aparece (0 % al presupuestar). */
    Map<Long, Integer> porcentajes(Long planId, Collection<Long> nomencladorIds);

    record CoberturaFila(Long nomencladorId, String codigo, String descripcion, boolean activo, int porcentaje) {}
}
