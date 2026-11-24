package ar.turnos.odontologia.agenda.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Caso de uso (C-43, design D2): pasa a {@code ATENDIDO} los turnos activos con la llegada registrada cuyo fin con
 * margen ya pasó. Hasta ese momento siguen activos, para que el margen de limpieza del sillón siga reservado. Un turno
 * sin la llegada registrada nunca cambia solo: ni a atendido ni a ausente (RN-AG-17).
 */
public class PasarAAtendidos {

    /** Puerto de salida: el cambio de estado en un solo paso, que devuelve los ids de los turnos que cambió. */
    public interface TurnosConLlegada {
        List<Long> pasarAAtendidos(Instant ahora);
    }

    private final TurnosConLlegada turnos;
    private final Clock reloj;

    public PasarAAtendidos(TurnosConLlegada turnos, Clock reloj) {
        this.turnos = Objects.requireNonNull(turnos, "turnos");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    public List<Long> ejecutar() {
        return turnos.pasarAAtendidos(reloj.instant());
    }
}
