package ar.turnos.odontologia.reportes.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Calcula los cuatro bloques de un reporte a partir de las filas ya leídas (RN-RE-03 a RN-RE-07, design D3 a D7). Es
 * código puro: no lee la base ni el reloj.
 */
public class CalculoReportes {

    /**
     * Prefijo del actor de las operaciones online del paciente en {@code turno_auditoria}. Lo define
     * {@code reserva/application/ActorOnline}; si cambia, hay que actualizar RN-RE-05 y DD-16.
     */
    public static final String PREFIJO_ACTOR_ONLINE = "paciente-online:";

    private static final Set<String> NO_CANCELADOS = Set.of("RESERVADO", "CONFIRMADO", "ATENDIDO", "AUSENTE");
    private static final Set<String> ACTIVOS = Set.of("RESERVADO", "CONFIRMADO");
    private static final String CANCELADO = "CANCELADO";
    private static final Comparator<ProfesionalLeido> POR_APELLIDO =
            Comparator.comparing(ProfesionalLeido::apellido).thenComparing(ProfesionalLeido::nombre)
                    .thenComparingLong(ProfesionalLeido::id);

    private final ZoneId zona;

    public CalculoReportes(ZoneId zona) {
        this.zona = Objects.requireNonNull(zona, "zona");
    }

    public Reporte calcular(Periodo periodo, Long profesionalId, DatosReporte datos, Instant ahora) {
        Intervalo limite = periodo.intervalo(zona);
        Predicate<Long> delFiltro = id -> profesionalId == null || profesionalId.equals(id);
        List<TurnoLeido> turnos = datos.turnos().stream()
                .filter(t -> delFiltro.test(t.profesionalId()))
                .filter(t -> !t.inicio().isBefore(limite.inicio()) && t.inicio().isBefore(limite.fin()))
                .toList();
        Map<Long, Long> atencion = minutosDeAtencion(periodo, limite, datos, delFiltro);
        List<FilaOcupacion> profesionales = ocupacionPorProfesional(datos.profesionales(), turnos, atencion, delFiltro);
        List<FilaOcupacion> sillones = profesionalId == null
                ? ocupacionPorSillon(datos.sillones(), datos.profesionales(), turnos, atencion)
                : List.of();
        List<AusenciaProfesional> ausenciasProfesionales =
                ausenciasDeProfesionales(datos.ausencias(), datos.profesionales(), limite, delFiltro);
        return new Reporte(periodo, profesionalId, new Ocupacion(profesionales, sillones), origen(turnos),
                new Ausencias(ausenciasDePacientes(turnos, ahora), ausenciasProfesionales), cancelaciones(turnos));
    }

    /** Porcentaje entero de {@code parte} sobre {@code total}, redondeado hacia arriba desde 0,5; {@code null} con total 0. */
    public static Integer porcentaje(long parte, long total) {
        if (total == 0) {
            return null;
        }
        return BigDecimal.valueOf(parte).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP).intValueExact();
    }

    private static boolean esOnline(String actor) {
        return actor != null && actor.startsWith(PREFIJO_ACTOR_ONLINE);
    }

    private Map<Long, Long> minutosDeAtencion(Periodo periodo, Intervalo limite, DatosReporte datos,
                                              Predicate<Long> delFiltro) {
        Map<Long, List<Intervalo>> franjas = new LinkedHashMap<>();
        for (LocalDate dia : periodo.dias()) {
            int diaSemana = dia.getDayOfWeek().getValue();
            for (HorarioLeido horario : datos.horarios()) {
                if (horario.diaSemana() == diaSemana && horario.hasta().isAfter(horario.desde())
                        && delFiltro.test(horario.profesionalId())) {
                    franjas.computeIfAbsent(horario.profesionalId(), k -> new ArrayList<>()).add(new Intervalo(
                            dia.atTime(horario.desde()).atZone(zona).toInstant(),
                            dia.atTime(horario.hasta()).atZone(zona).toInstant()));
                }
            }
        }
        Map<Long, List<Intervalo>> sinAtencion = new LinkedHashMap<>();
        datos.ausencias().forEach(a -> agregarRecortado(sinAtencion, a.profesionalId(), a.inicio(), a.fin(), limite));
        datos.bloqueos().forEach(b -> agregarRecortado(sinAtencion, b.profesionalId(), b.inicio(), b.fin(), limite));
        Map<Long, Long> minutos = new LinkedHashMap<>();
        franjas.forEach((profesional, propias) -> minutos.put(profesional, Intervalos.minutos(
                Intervalos.restar(propias, sinAtencion.getOrDefault(profesional, List.of())))));
        return minutos;
    }

    private static void agregarRecortado(Map<Long, List<Intervalo>> destino, long profesional, Instant inicio,
                                         Instant fin, Intervalo limite) {
        if (!fin.isAfter(inicio)) {
            return;
        }
        new Intervalo(inicio, fin).recortar(limite)
                .ifPresent(i -> destino.computeIfAbsent(profesional, k -> new ArrayList<>()).add(i));
    }

    private static long minutosOcupados(List<TurnoLeido> turnos) {
        return turnos.stream()
                .filter(t -> NO_CANCELADOS.contains(t.estado()))
                .mapToLong(t -> java.time.Duration.between(t.inicio(), t.fin()).toMinutes())
                .sum();
    }

    private static List<FilaOcupacion> ocupacionPorProfesional(List<ProfesionalLeido> profesionales,
                                                               List<TurnoLeido> turnos, Map<Long, Long> atencion,
                                                               Predicate<Long> delFiltro) {
        Set<Long> conTurnos = turnos.stream().map(TurnoLeido::profesionalId).collect(Collectors.toSet());
        return profesionales.stream()
                .filter(p -> delFiltro.test(p.id()))
                .filter(p -> p.activo() || conTurnos.contains(p.id()))
                .sorted(POR_APELLIDO)
                .map(p -> fila(p.id(), p.nombreCompleto(),
                        minutosOcupados(turnos.stream().filter(t -> t.profesionalId() == p.id()).toList()),
                        atencion.getOrDefault(p.id(), 0L)))
                .toList();
    }

    private static List<FilaOcupacion> ocupacionPorSillon(List<SillonLeido> sillones,
                                                          List<ProfesionalLeido> profesionales,
                                                          List<TurnoLeido> turnos, Map<Long, Long> atencion) {
        Set<Long> conTurnos = turnos.stream().map(TurnoLeido::sillonId).collect(Collectors.toSet());
        return sillones.stream()
                .filter(s -> s.activo() || conTurnos.contains(s.id()))
                .sorted(Comparator.comparing(SillonLeido::nombre).thenComparingLong(SillonLeido::id))
                .map(s -> fila(s.id(), s.nombre(),
                        minutosOcupados(turnos.stream().filter(t -> t.sillonId() == s.id()).toList()),
                        profesionales.stream()
                                .filter(p -> Objects.equals(p.sillonHabitualId(), s.id()))
                                .mapToLong(p -> atencion.getOrDefault(p.id(), 0L))
                                .sum()))
                .toList();
    }

    private static FilaOcupacion fila(long id, String nombre, long ocupados, long atencion) {
        return new FilaOcupacion(id, nombre, ocupados, atencion, porcentaje(ocupados, atencion));
    }

    private static Origen origen(List<TurnoLeido> turnos) {
        int online = 0;
        int consultorio = 0;
        int sinDato = 0;
        for (TurnoLeido turno : turnos) {
            if (CANCELADO.equals(turno.estado()) && turno.reprogramado()) {
                continue;
            }
            if (turno.actorCreacion() == null) {
                sinDato++;
            } else if (esOnline(turno.actorCreacion())) {
                online++;
            } else {
                consultorio++;
            }
        }
        return new Origen(online, consultorio, sinDato);
    }

    private static AusenciasPacientes ausenciasDePacientes(List<TurnoLeido> turnos, Instant ahora) {
        int ausentes = (int) turnos.stream().filter(t -> "AUSENTE".equals(t.estado())).count();
        int atendidos = (int) turnos.stream().filter(t -> "ATENDIDO".equals(t.estado())).count();
        int sinRegistrar = (int) turnos.stream()
                .filter(t -> ACTIVOS.contains(t.estado()) && t.fin().isBefore(ahora))
                .count();
        return new AusenciasPacientes(ausentes, atendidos, porcentaje(ausentes, (long) ausentes + atendidos), sinRegistrar);
    }

    private static List<AusenciaProfesional> ausenciasDeProfesionales(List<AusenciaLeida> ausencias,
                                                                      List<ProfesionalLeido> profesionales,
                                                                      Intervalo limite, Predicate<Long> delFiltro) {
        Map<Long, ProfesionalLeido> porId = profesionales.stream()
                .collect(Collectors.toMap(ProfesionalLeido::id, p -> p, (a, b) -> a));
        Map<List<Object>, long[]> grupos = new LinkedHashMap<>();
        for (AusenciaLeida ausencia : ausencias) {
            if (!delFiltro.test(ausencia.profesionalId()) || !ausencia.fin().isAfter(ausencia.inicio())) {
                continue;
            }
            new Intervalo(ausencia.inicio(), ausencia.fin()).recortar(limite).ifPresent(dentro -> {
                long[] acumulado = grupos.computeIfAbsent(List.of(ausencia.profesionalId(), ausencia.tipo()),
                        k -> new long[2]);
                acumulado[0]++;
                acumulado[1] += dentro.minutos();
            });
        }
        Comparator<AusenciaProfesional> orden = Comparator
                .comparing((AusenciaProfesional a) -> porId.get(a.profesionalId()), Comparator.nullsLast(POR_APELLIDO))
                .thenComparing(AusenciaProfesional::tipo);
        return grupos.entrySet().stream()
                .map(e -> {
                    long profesional = (Long) e.getKey().get(0);
                    ProfesionalLeido datos = porId.get(profesional);
                    String nombre = datos == null ? "" : datos.nombreCompleto();
                    return new AusenciaProfesional(profesional, nombre, (String) e.getKey().get(1),
                            (int) e.getValue()[0], e.getValue()[1]);
                })
                .sorted(orden)
                .toList();
    }

    private static Cancelaciones cancelaciones(List<TurnoLeido> turnos) {
        int porPaciente = 0;
        int porConsultorio = 0;
        int porReprogramacion = 0;
        int sinDato = 0;
        for (TurnoLeido turno : turnos) {
            if (!CANCELADO.equals(turno.estado())) {
                continue;
            }
            if (turno.reprogramado()) {
                porReprogramacion++;
            } else if (turno.actorCancelacion() == null) {
                sinDato++;
            } else if (esOnline(turno.actorCancelacion())) {
                porPaciente++;
            } else {
                porConsultorio++;
            }
        }
        return new Cancelaciones(porPaciente, porConsultorio, porReprogramacion, sinDato);
    }
}
