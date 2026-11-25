package ar.turnos.odontologia.caja.domain;

import ar.turnos.odontologia.coberturas.domain.CalculoItem;
import ar.turnos.odontologia.coberturas.domain.Porcentaje;
import ar.turnos.odontologia.coberturas.domain.Precio;

/**
 * Copago del cargo de un turno: la misma fórmula del presupuesto de C-19 ({@link CalculoItem}, RN-PR-03) con cantidad
 * 1, para que el paciente pague lo que se le presupuestó (design D3).
 */
public final class CalculoCargo {

    private CalculoCargo() {
    }

    public static CargoCalculado calcular(Tarifa tarifa) {
        CalculoItem item = CalculoItem.de(Precio.guardado(tarifa.precio()), 1, Porcentaje.guardado(tarifa.porcentaje()));
        return new CargoCalculado(item.importe(), tarifa.porcentaje(), item.cobertura(), item.copago(),
                tarifa.afiliacionId());
    }
}
