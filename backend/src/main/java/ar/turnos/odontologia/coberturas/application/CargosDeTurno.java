package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.domain.CargoTurno;
import java.util.Collection;
import java.util.Map;

/**
 * Puerto de lectura del cargo de caja de cada turno (C-22, RN-CA-03; C-20 design D5). C-22 todavía no está en
 * {@code master}: hoy el adaptador es {@code SinCargosDeCaja} y todas las líneas usan el arancel. Cuando C-22 se
 * mergee, un {@code CargosDeTurnoJdbc} lo reemplaza sin tocar el resto.
 */
public interface CargosDeTurno {

    /** El cargo de cada turno que lo tiene; los turnos sin cargo no aparecen en el mapa. */
    Map<Long, CargoTurno> de(Collection<Long> turnoIds);
}
