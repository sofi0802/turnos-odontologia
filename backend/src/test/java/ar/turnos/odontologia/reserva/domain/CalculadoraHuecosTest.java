package ar.turnos.odontologia.reserva.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.agenda.domain.Agenda;
import ar.turnos.odontologia.agenda.domain.Intervalo;
import ar.turnos.odontologia.agenda.domain.Margen;
import ar.turnos.odontologia.agenda.domain.NumeroReserva;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.agenda.domain.reglas.FranjaHoraria;
import ar.turnos.odontologia.agenda.domain.reglas.HorarioSemanal;
import ar.turnos.odontologia.agenda.domain.reglas.PeriodoSinAtencion;
import ar.turnos.odontologia.agenda.domain.reglas.PeriodosProfesional;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CalculadoraHuecosTest {

    private static final ZoneId BA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final long DRA_A = 1L;
    private static final long DR_B = 2L;
    private static final long CONSULTA = 1L;
    private static final List<Long> SILLONES = List.of(1L, 2L);

    private final CalculadoraHuecos calculadora = new CalculadoraHuecos(new Agenda(Margen.PREDETERMINADO), BA);
    private final PeriodosProfesional jornada = PeriodosProfesional.sinRestricciones();

    private static Instant hora(int h, int m) {
        return LocalDateTime.of(LocalDate.of(2026, 12, 1), LocalTime.of(h, m)).atZone(BA).toInstant();
    }

    private static Intervalo intervalo(int h, int m, int minutos) {
        Instant inicio = hora(h, m);
        return new Intervalo(inicio, inicio.plusSeconds(minutos * 60L));
    }

    private static Turno turno(long id, long profesional, long sillon, Intervalo intervalo, boolean cancelado) {
        Turno t = Turno.reservar(9L, profesional, sillon, CONSULTA, intervalo, NumeroReserva.deSimbolos("AAAAAAAA"))
                .conId(id);
        return cancelado ? t.cancelado() : t;
    }

    private List<Long> libres(Intervalo pedido, long profesional, PeriodosProfesional periodos, List<Long> sillones,
                              List<Turno> turnos, Long ignorar) {
        return calculadora.sillonesLibres(pedido, profesional, CONSULTA, periodos, sillones, turnos, ignorar);
    }

    @Test
    @DisplayName("Con la agenda vacía todos los sillones están libres, en el orden recibido")
    void agendaVacia() {
        assertThat(libres(intervalo(10, 0, 30), DRA_A, jornada, SILLONES, List.of(), null)).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("Un turno cancelado no ocupa")
    void canceladoNoOcupa() {
        List<Turno> turnos = List.of(turno(1, DRA_A, 1, intervalo(10, 0, 30), true));
        assertThat(libres(intervalo(10, 0, 30), DRA_A, jornada, SILLONES, turnos, null)).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("El margen de 10 minutos excluye las 10:30 tras un turno de 10:00 a 10:30 y deja las 10:45")
    void margenExcluye() {
        List<Turno> turnos = List.of(turno(1, DRA_A, 1, intervalo(10, 0, 30), false));
        assertThat(libres(intervalo(10, 30, 30), DRA_A, jornada, SILLONES, turnos, null)).isEmpty();
        assertThat(libres(intervalo(10, 45, 30), DRA_A, jornada, SILLONES, turnos, null)).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("Un bloqueo de 10:00 a 10:30 excluye las 10:00 y 10:15 y deja las 10:30")
    void bloqueo() {
        PeriodosProfesional bloqueado = new PeriodosProfesional(HorarioSemanal.sinLimites(),
                List.of(PeriodoSinAtencion.de(PeriodoSinAtencion.Tipo.BLOQUEO, hora(10, 0), hora(10, 30))));
        assertThat(libres(intervalo(10, 0, 30), DRA_A, bloqueado, SILLONES, List.of(), null)).isEmpty();
        assertThat(libres(intervalo(10, 15, 30), DRA_A, bloqueado, SILLONES, List.of(), null)).isEmpty();
        assertThat(libres(intervalo(10, 30, 30), DRA_A, bloqueado, SILLONES, List.of(), null))
                .containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("Una licencia excluye todo el día")
    void licencia() {
        PeriodosProfesional licencia = new PeriodosProfesional(HorarioSemanal.sinLimites(),
                List.of(PeriodoSinAtencion.de(PeriodoSinAtencion.Tipo.LICENCIA, hora(0, 0), hora(23, 59))));
        assertThat(libres(intervalo(10, 0, 30), DRA_A, licencia, SILLONES, List.of(), null)).isEmpty();
    }

    @Test
    @DisplayName("Fuera de la franja de trabajo no hay horario")
    void fueraDeLaFranja() {
        PeriodosProfesional franja = new PeriodosProfesional(
                HorarioSemanal.de(List.of(FranjaHoraria.de(2, LocalTime.of(10, 0), LocalTime.of(11, 0)))), List.of());
        assertThat(libres(intervalo(10, 30, 30), DRA_A, franja, SILLONES, List.of(), null)).containsExactly(1L, 2L);
        assertThat(libres(intervalo(10, 45, 30), DRA_A, franja, SILLONES, List.of(), null)).isEmpty();
    }

    @Test
    @DisplayName("Un profesional libre sin sillón libre no aporta el horario")
    void sinSillonLibre() {
        List<Turno> turnos = List.of(turno(1, DR_B, 1, intervalo(10, 0, 30), false));
        assertThat(libres(intervalo(10, 0, 30), DRA_A, jornada, List.of(1L), turnos, null)).isEmpty();
        assertThat(libres(intervalo(10, 0, 30), DRA_A, jornada, List.of(1L, 2L), turnos, null)).containsExactly(2L);
    }

    @Test
    @DisplayName("El turno original de una reprogramación no cuenta como conflicto")
    void originalIgnorado() {
        List<Turno> turnos = List.of(turno(7, DRA_A, 1, intervalo(10, 0, 30), false));
        assertThat(libres(intervalo(10, 15, 30), DRA_A, jornada, SILLONES, turnos, null)).isEmpty();
        assertThat(libres(intervalo(10, 15, 30), DRA_A, jornada, SILLONES, turnos, 7L)).containsExactly(1L, 2L);
    }
}
