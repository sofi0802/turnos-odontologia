package ar.turnos.odontologia.coberturas.domain;

import java.math.BigDecimal;

/**
 * El cargo de caja de un turno (C-22, RN-CA-03) tal como lo necesita una liquidación: el precio cobrado, el porcentaje
 * de cobertura y el importe que cubre la obra social. Con cargo, la línea usa estos valores y no el arancel vigente.
 */
public record CargoTurno(BigDecimal precio, int porcentaje, BigDecimal cobertura) {
}
