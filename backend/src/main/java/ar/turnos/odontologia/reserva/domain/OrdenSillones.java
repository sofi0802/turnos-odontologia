package ar.turnos.odontologia.reserva.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Orden en que se prueban los sillones (RN-RO-06, design D4): el habitual del profesional si está entre los activos y
 * después los demás activos por id ascendente.
 */
public final class OrdenSillones {

    private OrdenSillones() {}

    public static List<Long> ordenar(List<Long> sillonesActivos, Long habitual) {
        List<Long> porId = new ArrayList<>(sillonesActivos);
        porId.sort(Long::compare);
        if (habitual != null && porId.remove(habitual)) {
            porId.add(0, habitual);
        }
        return List.copyOf(porId);
    }
}
