package ar.turnos.odontologia.caja.domain;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Precio y cobertura con que se calcula el cargo de un turno (RN-CA-03, design D3).
 *
 * @param afiliacionId afiliación vigente en la fecha del turno, o {@code null} si el paciente es particular
 */
public record Tarifa(BigDecimal precio, int porcentaje, Long afiliacionId) {

    /**
     * Elige el porcentaje como C-19: el del primer código activo en orden de código, según el plan de la afiliación; 0 si
     * es particular, si la prestación no tiene códigos o si el plan no tiene porcentaje para ese código.
     *
     * @param porcentajesDelPlan porcentaje por id de código del nomenclador para el plan de la afiliación
     */
    public static Tarifa elegir(BigDecimal precio, Long afiliacionId, List<CodigoActivo> codigosActivos,
                                Map<Long, Integer> porcentajesDelPlan) {
        if (afiliacionId == null) {
            return new Tarifa(precio, 0, null);
        }
        int porcentaje = codigosActivos.stream()
                .min(Comparator.comparing(CodigoActivo::codigo))
                .map(c -> porcentajesDelPlan.getOrDefault(c.id(), 0))
                .orElse(0);
        return new Tarifa(precio, porcentaje, afiliacionId);
    }
}
