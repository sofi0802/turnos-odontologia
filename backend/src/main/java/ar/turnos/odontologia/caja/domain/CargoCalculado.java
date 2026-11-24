package ar.turnos.odontologia.caja.domain;

import java.math.BigDecimal;

/** Foto del cálculo del cargo de un turno (RN-CA-03, DD-18). */
public record CargoCalculado(BigDecimal precio, int porcentajeCobertura, BigDecimal cobertura, BigDecimal copago,
                             Long afiliacionId) {
}
