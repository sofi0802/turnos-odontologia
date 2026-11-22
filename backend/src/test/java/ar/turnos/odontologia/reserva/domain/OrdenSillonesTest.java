package ar.turnos.odontologia.reserva.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrdenSillonesTest {

    @Test
    @DisplayName("El sillón habitual activo va primero y después los demás por id")
    void habitualPrimero() {
        assertThat(OrdenSillones.ordenar(List.of(3L, 1L, 2L), 2L)).containsExactly(2L, 1L, 3L);
    }

    @Test
    @DisplayName("Con el habitual ya primero el orden es por id")
    void habitualYaPrimero() {
        assertThat(OrdenSillones.ordenar(List.of(2L, 1L), 1L)).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("Un habitual inactivo (fuera de la lista de activos) se ignora")
    void habitualInactivo() {
        assertThat(OrdenSillones.ordenar(List.of(1L, 2L), 9L)).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("Sin sillón habitual el orden es por id")
    void sinHabitual() {
        assertThat(OrdenSillones.ordenar(List.of(2L, 1L), null)).containsExactly(1L, 2L);
    }
}
