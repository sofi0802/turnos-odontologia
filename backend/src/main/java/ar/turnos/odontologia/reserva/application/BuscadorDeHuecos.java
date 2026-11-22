package ar.turnos.odontologia.reserva.application;

import ar.turnos.odontologia.agenda.application.DisponibilidadProfesional;
import ar.turnos.odontologia.agenda.application.TurnoRepository;
import ar.turnos.odontologia.agenda.domain.Agenda;
import ar.turnos.odontologia.agenda.domain.Intervalo;
import ar.turnos.odontologia.agenda.domain.Margen;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.agenda.domain.reglas.PeriodosProfesional;
import ar.turnos.odontologia.reserva.application.CatalogoReserva.Prestacion;
import ar.turnos.odontologia.reserva.application.CatalogoReserva.Profesional;
import ar.turnos.odontologia.reserva.domain.AsignadorProfesional;
import ar.turnos.odontologia.reserva.domain.CalculadoraHuecos;
import ar.turnos.odontologia.reserva.domain.Desempate;
import ar.turnos.odontologia.reserva.domain.GrillaHorarios;
import ar.turnos.odontologia.reserva.domain.OrdenSillones;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Cálculo de huecos compartido por la disponibilidad pública y la reserva (design D2), para que un horario ofrecido
 * sea uno que la reserva acepta. Hace una lectura de turnos por profesional y por sillón para el día entero, no una
 * por horario, y delega las reglas en {@link CalculadoraHuecos}.
 */
public class BuscadorDeHuecos {

    /** Un profesional con un sillón libre para el horario pedido. */
    public record Combinacion(Long profesionalId, Long sillonId) {}

    private final CatalogoReserva catalogo;
    private final TurnoRepository turnos;
    private final DisponibilidadProfesional disponibilidad;
    private final Margen margen;
    private final Clock reloj;
    private final ZoneId zona;
    private final int pasoMinutos;
    private final CalculadoraHuecos calculadora;
    private final AsignadorProfesional asignador;

    public BuscadorDeHuecos(CatalogoReserva catalogo, TurnoRepository turnos, DisponibilidadProfesional disponibilidad,
                            Margen margen, Clock reloj, ZoneId zona, int pasoMinutos, Desempate desempate) {
        this.catalogo = Objects.requireNonNull(catalogo, "catalogo");
        this.turnos = Objects.requireNonNull(turnos, "turnos");
        this.disponibilidad = Objects.requireNonNull(disponibilidad, "disponibilidad");
        this.margen = Objects.requireNonNull(margen, "margen");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
        this.zona = Objects.requireNonNull(zona, "zona");
        this.pasoMinutos = pasoMinutos;
        this.calculadora = new CalculadoraHuecos(new Agenda(margen), zona);
        this.asignador = new AsignadorProfesional(desempate);
    }

    /**
     * Horarios libres del día para alguno de los candidatos: ordenados por inicio y cada uno una sola vez.
     *
     * @param ignorarTurnoId el turno original de una reprogramación, o {@code null}
     */
    public List<Intervalo> horariosLibres(LocalDate fecha, Prestacion prestacion, List<Profesional> candidatos,
                                          Long ignorarTurnoId) {
        Dia dia = cargar(fecha, candidatos);
        TreeMap<Instant, Intervalo> libres = new TreeMap<>();
        for (Profesional profesional : candidatos) {
            for (Instant inicio : dia.grilla(profesional, prestacion)) {
                Intervalo intervalo = new Intervalo(inicio, inicio.plus(prestacion.duracion()));
                if (!dia.sillonesLibres(profesional, prestacion, intervalo, ignorarTurnoId).isEmpty()) {
                    libres.putIfAbsent(inicio, intervalo);
                }
            }
        }
        return List.copyOf(libres.values());
    }

    /**
     * Las combinaciones de profesional y sillón libres para ese inicio, de la más a la menos preferida (RN-RO-05 y
     * RN-RO-06). Vacía si el inicio no está en la grilla de ningún candidato o no queda ninguna libre.
     */
    public List<Combinacion> combinaciones(Instant inicio, Prestacion prestacion, List<Profesional> candidatos,
                                           Long ignorarTurnoId) {
        LocalDate fecha = inicio.atZone(zona).toLocalDate();
        Dia dia = cargar(fecha, candidatos);
        Intervalo intervalo = new Intervalo(inicio, inicio.plus(prestacion.duracion()));

        Map<Long, List<Long>> sillonesPorProfesional = new LinkedHashMap<>();
        Map<Long, Long> turnosDelDia = new LinkedHashMap<>();
        for (Profesional profesional : candidatos) {
            if (!dia.grilla(profesional, prestacion).contains(inicio)) {
                continue;
            }
            List<Long> sillones = dia.sillonesLibres(profesional, prestacion, intervalo, ignorarTurnoId);
            if (!sillones.isEmpty()) {
                sillonesPorProfesional.put(profesional.id(), sillones);
                turnosDelDia.put(profesional.id(),
                        AsignadorProfesional.contarDelDia(dia.turnos, profesional.id(), fecha, zona, ignorarTurnoId));
            }
        }

        List<Combinacion> combinaciones = new ArrayList<>();
        for (Long profesionalId : asignador.ordenar(turnosDelDia)) {
            for (Long sillonId : sillonesPorProfesional.get(profesionalId)) {
                combinaciones.add(new Combinacion(profesionalId, sillonId));
            }
        }
        return List.copyOf(combinaciones);
    }

    private Dia cargar(LocalDate fecha, List<Profesional> candidatos) {
        Intervalo ventana = new Intervalo(fecha.atStartOfDay(zona).toInstant(),
                fecha.plusDays(1).atStartOfDay(zona).toInstant());
        Intervalo ampliada = ventana.ampliadoPor(margen);

        Map<Long, PeriodosProfesional> periodos = new HashMap<>();
        Map<Long, Turno> delDia = new LinkedHashMap<>();
        for (Profesional profesional : candidatos) {
            periodos.put(profesional.id(), disponibilidad.periodos(profesional.id(), ventana));
            turnos.buscarQueIntersecan(profesional.id(), null, ampliada).forEach(t -> delDia.put(t.id(), t));
        }
        List<Long> sillones = catalogo.sillonesActivos();
        for (Long sillonId : sillones) {
            turnos.buscarQueIntersecan(null, sillonId, ampliada).forEach(t -> delDia.put(t.id(), t));
        }
        return new Dia(fecha, periodos, List.copyOf(delDia.values()), sillones);
    }

    /** Los datos leídos de un día, para consultar varios horarios sin volver a la base. */
    private final class Dia {
        private final LocalDate fecha;
        private final Map<Long, PeriodosProfesional> periodos;
        private final List<Turno> turnos;
        private final List<Long> sillonesActivos;

        private Dia(LocalDate fecha, Map<Long, PeriodosProfesional> periodos, List<Turno> turnos,
                    List<Long> sillonesActivos) {
            this.fecha = fecha;
            this.periodos = periodos;
            this.turnos = turnos;
            this.sillonesActivos = sillonesActivos;
        }

        List<Instant> grilla(Profesional profesional, Prestacion prestacion) {
            return GrillaHorarios.inicios(fecha, zona, periodos.get(profesional.id()).horario().franjas(),
                    prestacion.duracion(), reloj.instant(), pasoMinutos);
        }

        List<Long> sillonesLibres(Profesional profesional, Prestacion prestacion, Intervalo intervalo,
                                  Long ignorarTurnoId) {
            return calculadora.sillonesLibres(intervalo, profesional.id(), prestacion.id(),
                    periodos.get(profesional.id()),
                    OrdenSillones.ordenar(sillonesActivos, profesional.sillonHabitualId()), turnos, ignorarTurnoId);
        }
    }
}
