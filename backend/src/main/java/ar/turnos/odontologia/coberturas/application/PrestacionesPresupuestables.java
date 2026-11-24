package ar.turnos.odontologia.coberturas.application;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Puerto de lectura de lo que hace falta para presupuestar cada prestación (C-19, design D4): su nombre, si está
 * activa, su precio particular y sus códigos activos del nomenclador. Solo lee {@code prestacion} (de
 * {@code configuracion/}), {@code arancel} y {@code nomenclador}.
 */
public interface PrestacionesPresupuestables {

    /** Las prestaciones que existen; los ids que no existen no aparecen. */
    Map<Long, Presupuestable> de(Collection<Long> prestacionIds);

    /** {@code precio} es nulo si la prestación no tiene arancel; {@code codigosActivos} va ordenada por código. */
    record Presupuestable(Long id, String nombre, boolean activa, BigDecimal precio, List<CodigoActivo> codigosActivos) {}

    record CodigoActivo(Long id, String codigo) {}
}
