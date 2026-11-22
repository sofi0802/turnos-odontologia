package ar.turnos.odontologia.reserva.domain;

import ar.turnos.odontologia.agenda.domain.Turno;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Asignación del profesional sin preferencia (RN-RO-05, design D3): el que tiene menos turnos activos ese día. Si hay
 * empate decide el {@link Desempate} inyectado. Devuelve todos los candidatos ordenados por preferencia, así la
 * reserva puede probar el siguiente si pierde una carrera.
 */
public final class AsignadorProfesional {

    private final Desempate desempate;

    public AsignadorProfesional(Desempate desempate) {
        this.desempate = Objects.requireNonNull(desempate, "desempate");
    }

    /**
     * @param turnosActivosPorProfesional id del profesional candidato y su cantidad de turnos activos del día
     * @return los ids: primero el elegido (menos turnos y, si hay empate, el que decide el desempate) y después el
     *     resto por cantidad e id
     */
    public List<Long> ordenar(Map<Long, Long> turnosActivosPorProfesional) {
        if (turnosActivosPorProfesional.isEmpty()) {
            return List.of();
        }
        long minimo = turnosActivosPorProfesional.values().stream().mapToLong(Long::longValue).min().orElseThrow();
        List<Long> empatados = turnosActivosPorProfesional.entrySet().stream()
                .filter(e -> e.getValue() == minimo)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        Long elegido = empatados.size() == 1 ? empatados.get(0) : desempate.elegir(empatados);

        List<Long> orden = new ArrayList<>();
        orden.add(elegido);
        turnosActivosPorProfesional.entrySet().stream()
                .filter(e -> !e.getKey().equals(elegido))
                .sorted(Map.Entry.<Long, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey(
                        Comparator.naturalOrder())))
                .forEach(e -> orden.add(e.getKey()));
        return List.copyOf(orden);
    }

    /**
     * Turnos activos del profesional que empiezan el día local indicado. En la reprogramación el turno original no
     * se cuenta.
     */
    public static long contarDelDia(Collection<Turno> turnos, Long profesionalId, LocalDate dia, ZoneId zona,
                                    Long turnoIgnoradoId) {
        return turnos.stream()
                .filter(Turno::esActivo)
                .filter(t -> t.profesionalId().equals(profesionalId))
                .filter(t -> turnoIgnoradoId == null || !turnoIgnoradoId.equals(t.id()))
                .filter(t -> t.intervalo().inicio().atZone(zona).toLocalDate().equals(dia))
                .count();
    }
}
