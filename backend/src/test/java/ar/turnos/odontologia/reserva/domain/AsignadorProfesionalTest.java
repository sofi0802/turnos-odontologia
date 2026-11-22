package ar.turnos.odontologia.reserva.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.agenda.domain.Intervalo;
import ar.turnos.odontologia.agenda.domain.NumeroReserva;
import ar.turnos.odontologia.agenda.domain.Turno;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AsignadorProfesionalTest {

    private static final ZoneId BA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate DIA = LocalDate.of(2026, 12, 1);

    private static Instant en(LocalDate dia, int h) {
        return LocalDateTime.of(dia, LocalTime.of(h, 0)).atZone(BA).toInstant();
    }

    private static Turno turno(Long id, long profesional, LocalDate dia, int h, boolean cancelado) {
        Turno t = Turno.reservar(1L, profesional, 1L, 1L, new Intervalo(en(dia, h), en(dia, h).plusSeconds(1800)),
                NumeroReserva.deSimbolos("AAAAAAAA")).conId(id);
        return cancelado ? t.cancelado() : t;
    }

    private static Map<Long, Long> conteos(long a, long b) {
        Map<Long, Long> m = new LinkedHashMap<>();
        m.put(10L, a);
        m.put(20L, b);
        return m;
    }

    @Test
    @DisplayName("Gana el profesional con menos turnos activos")
    void ganaElDeMenosTurnos() {
        AsignadorProfesional asignador = new AsignadorProfesional(lista -> lista.get(0));
        assertThat(asignador.ordenar(conteos(2, 1))).containsExactly(20L, 10L);
        assertThat(asignador.ordenar(conteos(0, 3))).containsExactly(10L, 20L);
    }

    @Test
    @DisplayName("El empate lo resuelve el desempate inyectado: primero")
    void empateElPrimero() {
        AsignadorProfesional asignador = new AsignadorProfesional(lista -> lista.get(0));
        assertThat(asignador.ordenar(conteos(1, 1))).containsExactly(10L, 20L);
    }

    @Test
    @DisplayName("El empate lo resuelve el desempate inyectado: último")
    void empateElUltimo() {
        AsignadorProfesional asignador = new AsignadorProfesional(lista -> lista.get(lista.size() - 1));
        assertThat(asignador.ordenar(conteos(1, 1))).containsExactly(20L, 10L);
    }

    @Test
    @DisplayName("El desempate recibe solo los empatados ordenados por id")
    void desempateRecibeSoloEmpatados() {
        List<List<Long>> recibidos = new ArrayList<>();
        AsignadorProfesional asignador = new AsignadorProfesional(lista -> {
            recibidos.add(lista);
            return lista.get(0);
        });
        Map<Long, Long> m = new LinkedHashMap<>();
        m.put(30L, 1L);
        m.put(10L, 1L);
        m.put(20L, 5L);
        assertThat(asignador.ordenar(m)).containsExactly(10L, 30L, 20L);
        assertThat(recibidos).containsExactly(List.of(10L, 30L));
    }

    @Test
    @DisplayName("Los turnos cancelados, de otro día o de otro profesional no cuentan")
    void canceladosNoCuentan() {
        List<Turno> turnos = List.of(
                turno(1L, 10, DIA, 8, true), turno(2L, 10, DIA, 9, true), turno(3L, 10, DIA, 12, false),
                turno(4L, 10, DIA.plusDays(1), 9, false), turno(5L, 20, DIA, 9, false));
        assertThat(AsignadorProfesional.contarDelDia(turnos, 10L, DIA, BA, null)).isEqualTo(1);
        assertThat(AsignadorProfesional.contarDelDia(turnos, 20L, DIA, BA, null)).isEqualTo(1);
    }

    @Test
    @DisplayName("En la reprogramación el turno original no se cuenta")
    void originalExcluido() {
        List<Turno> turnos = List.of(turno(1L, 10, DIA, 8, false), turno(2L, 10, DIA, 9, false));
        assertThat(AsignadorProfesional.contarDelDia(turnos, 10L, DIA, BA, 2L)).isEqualTo(1);
    }
}
