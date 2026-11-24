package ar.turnos.odontologia.reportes.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Operaciones sobre listas de intervalos para los minutos de atención (design D3). */
public final class Intervalos {

    private Intervalos() {
    }

    /** Unión ordenada: junta los intervalos superpuestos y los contiguos. */
    public static List<Intervalo> union(List<Intervalo> intervalos) {
        List<Intervalo> ordenados = intervalos.stream().sorted(Comparator.comparing(Intervalo::inicio)).toList();
        List<Intervalo> resultado = new ArrayList<>();
        Instant desde = null;
        Instant hasta = null;
        for (Intervalo intervalo : ordenados) {
            if (hasta != null && !intervalo.inicio().isAfter(hasta)) {
                if (intervalo.fin().isAfter(hasta)) {
                    hasta = intervalo.fin();
                }
                continue;
            }
            if (desde != null) {
                resultado.add(new Intervalo(desde, hasta));
            }
            desde = intervalo.inicio();
            hasta = intervalo.fin();
        }
        if (desde != null) {
            resultado.add(new Intervalo(desde, hasta));
        }
        return resultado;
    }

    /** Lo que queda de {@code base} después de quitarle {@code quitar}, ordenado y sin superposiciones. */
    public static List<Intervalo> restar(List<Intervalo> base, List<Intervalo> quitar) {
        List<Intervalo> resto = union(base);
        for (Intervalo hueco : union(quitar)) {
            List<Intervalo> siguiente = new ArrayList<>();
            for (Intervalo tramo : resto) {
                if (!hueco.inicio().isBefore(tramo.fin()) || !hueco.fin().isAfter(tramo.inicio())) {
                    siguiente.add(tramo);
                    continue;
                }
                if (hueco.inicio().isAfter(tramo.inicio())) {
                    siguiente.add(new Intervalo(tramo.inicio(), hueco.inicio()));
                }
                if (hueco.fin().isBefore(tramo.fin())) {
                    siguiente.add(new Intervalo(hueco.fin(), tramo.fin()));
                }
            }
            resto = siguiente;
        }
        return resto;
    }

    public static long minutos(List<Intervalo> intervalos) {
        return intervalos.stream().mapToLong(Intervalo::minutos).sum();
    }
}
