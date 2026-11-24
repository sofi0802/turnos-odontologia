package ar.turnos.odontologia.reportes.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PeriodoTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Test
    @DisplayName("Un período de un día es válido")
    void unDia() {
        Periodo periodo = Periodo.de(LocalDate.of(2026, 12, 7), LocalDate.of(2026, 12, 7));
        assertThat(periodo.dias()).containsExactly(LocalDate.of(2026, 12, 7));
    }

    @Test
    @DisplayName("Periodo acepta 92 días contando los dos extremos")
    void noventaYDosDias() {
        Periodo periodo = Periodo.de(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 3, 2));
        assertThat(periodo.dias()).hasSize(92);
    }

    @Test
    @DisplayName("Periodo rechaza 93 días en el campo hasta")
    void noventaYTresDias() {
        assertThatThrownBy(() -> Periodo.de(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 3, 3)))
                .isInstanceOf(PeriodoInvalidoException.class)
                .extracting(e -> ((PeriodoInvalidoException) e).campo())
                .isEqualTo("hasta");
    }

    @Test
    @DisplayName("Periodo rechaza un fin anterior al inicio en el campo hasta")
    void finAnteriorAlInicio() {
        assertThatThrownBy(() -> Periodo.de(LocalDate.of(2026, 12, 13), LocalDate.of(2026, 12, 7)))
                .isInstanceOf(PeriodoInvalidoException.class)
                .extracting(e -> ((PeriodoInvalidoException) e).campo())
                .isEqualTo("hasta");
    }

    @Test
    @DisplayName("El intervalo va de las 00:00 del primer día a las 00:00 del día siguiente al último")
    void intervaloEnLaZona() {
        Periodo periodo = Periodo.de(LocalDate.of(2026, 12, 7), LocalDate.of(2026, 12, 13));

        Intervalo intervalo = periodo.intervalo(ZONA);

        assertThat(intervalo.inicio()).isEqualTo(Instant.parse("2026-12-07T03:00:00Z"));
        assertThat(intervalo.fin()).isEqualTo(Instant.parse("2026-12-14T03:00:00Z"));
    }
}
