package ar.turnos.odontologia.reportes.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IntervalosTest {

    private static Intervalo de(String desde, String hasta) {
        return new Intervalo(Instant.parse("2026-12-07T" + desde + ":00Z"), Instant.parse("2026-12-07T" + hasta + ":00Z"));
    }

    @Test
    @DisplayName("La unión junta los intervalos contiguos y los superpuestos")
    void unionDeContiguosYSuperpuestos() {
        List<Intervalo> union = Intervalos.union(List.of(de("10:00", "11:00"), de("09:00", "10:00"), de("10:30", "12:00")));

        assertThat(union).containsExactly(de("09:00", "12:00"));
    }

    @Test
    @DisplayName("La unión deja separados los intervalos disjuntos, ordenados")
    void unionDeDisjuntos() {
        List<Intervalo> union = Intervalos.union(List.of(de("14:00", "15:00"), de("09:00", "10:00")));

        assertThat(union).containsExactly(de("09:00", "10:00"), de("14:00", "15:00"));
    }

    @Test
    @DisplayName("Restar un intervalo del medio parte la franja en dos")
    void restarDelMedio() {
        List<Intervalo> resto = Intervalos.restar(List.of(de("09:00", "13:00")), List.of(de("10:00", "11:00")));

        assertThat(resto).containsExactly(de("09:00", "10:00"), de("11:00", "13:00"));
        assertThat(Intervalos.minutos(resto)).isEqualTo(180);
    }

    @Test
    @DisplayName("Restar un intervalo que contiene la franja la elimina")
    void restarQueContiene() {
        List<Intervalo> resto = Intervalos.restar(List.of(de("09:00", "13:00")), List.of(de("08:00", "14:00")));

        assertThat(resto).isEmpty();
        assertThat(Intervalos.minutos(resto)).isZero();
    }

    @Test
    @DisplayName("Restar un intervalo disjunto no cambia la franja")
    void restarDisjunto() {
        List<Intervalo> resto = Intervalos.restar(List.of(de("09:00", "13:00")), List.of(de("14:00", "15:00")));

        assertThat(resto).containsExactly(de("09:00", "13:00"));
    }

    @Test
    @DisplayName("Recortar deja solo la parte dentro del límite")
    void recortar() {
        assertThat(de("08:00", "10:00").recortar(de("09:00", "18:00"))).contains(de("09:00", "10:00"));
        assertThat(de("06:00", "07:00").recortar(de("09:00", "18:00"))).isEmpty();
    }
}
