package ar.turnos.odontologia.coberturas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnos.odontologia.coberturas.domain.Presupuesto.CoberturaUsada;
import ar.turnos.odontologia.coberturas.domain.Presupuesto.Item;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Agregado {@link Presupuesto} (C-19, tarea 2.5): totales, particular, vencimiento y transiciones de estado. */
class PresupuestoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 24);
    private static final CoberturaUsada PLAN_BASE = new CoberturaUsada(7L, "Obra Social Ejemplo", "Plan Base", "OSE-000001");

    private static Item item(String nombre, String precio, int cantidad, Long nomenclador, String codigo, int porcentaje) {
        return Item.de(1L, nombre, nomenclador, codigo, PiezaCara.NINGUNA, cantidad,
                Precio.de(new BigDecimal(precio)), Porcentaje.de(BigDecimal.valueOf(porcentaje)));
    }

    private static Presupuesto emitir(CoberturaUsada cobertura, Item... items) {
        return Presupuesto.emitir(1L, 2L, HOY, cobertura, List.of(items), Instant.parse("2026-11-24T15:00:00Z"));
    }

    private static Presupuesto pendienteConVencimiento(LocalDate venceEl) {
        return Presupuesto.reconstruir(1L, 1L, 2L, venceEl.minusDays(30), venceEl, null, EstadoPresupuesto.PENDIENTE,
                List.of(item("Consulta o control", "15000.00", 1, null, null, 0)), Instant.parse("2026-11-24T15:00:00Z"));
    }

    @Test
    @DisplayName("Los totales son la suma de los ítems")
    void totales() {
        Presupuesto p = emitir(PLAN_BASE,
                item("Consulta o control", "15000.00", 1, 1L, "N-0101", 100),
                item("Limpieza (profilaxis)", "25000.00", 1, 2L, "N-0201", 50));
        assertThat(p.totalPrecio()).isEqualTo("40000.00");
        assertThat(p.totalCobertura()).isEqualTo("27500.00");
        assertThat(p.totalCopago()).isEqualTo("12500.00");
        assertThat(p.estado()).isEqualTo(EstadoPresupuesto.PENDIENTE);
        assertThat(p.fecha()).isEqualTo(HOY);
        assertThat(p.venceEl()).isEqualTo(HOY.plusDays(30)).isEqualTo(LocalDate.of(2026, 12, 24));
        assertThat(p.particular()).isFalse();
    }

    @Test
    @DisplayName("Sin cobertura el presupuesto es particular")
    void particular() {
        Presupuesto p = emitir(null, item("Consulta o control", "15000.00", 1, null, null, 0));
        assertThat(p.particular()).isTrue();
        assertThat(p.cobertura()).isNull();
        assertThat(p.totalCobertura()).isEqualTo("0.00");
        assertThat(p.totalCopago()).isEqualTo("15000.00");
    }

    @Test
    @DisplayName("Un presupuesto lleva de 1 a 50 ítems")
    void cantidadDeItems() {
        assertThatThrownBy(() -> emitir(null)).isInstanceOfSatisfying(CoberturaException.class, e -> {
            assertThat(e.codigo()).isEqualTo(CodigoCobertura.DATOS_INVALIDOS);
            assertThat(e.campo()).isEqualTo("items");
        });
        List<Item> cincuentaYUno = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            cincuentaYUno.add(item("Consulta o control", "100.00", 1, null, null, 0));
        }
        assertThatThrownBy(() -> Presupuesto.emitir(1L, 2L, HOY, null, cincuentaYUno, Instant.EPOCH))
                .isInstanceOfSatisfying(CoberturaException.class, e -> assertThat(e.campo()).isEqualTo("items"));
        assertThat(Presupuesto.emitir(1L, 2L, HOY, null, cincuentaYUno.subList(0, 50), Instant.EPOCH).items()).hasSize(50);
    }

    @Test
    @DisplayName("Una cantidad fuera de 1 a 99 es inválida")
    void cantidad() {
        assertThatThrownBy(() -> item("Consulta o control", "100.00", 0, null, null, 0))
                .isInstanceOfSatisfying(CoberturaException.class, e -> assertThat(e.campo()).isEqualTo("cantidad"));
        assertThatThrownBy(() -> item("Consulta o control", "100.00", 100, null, null, 0))
                .isInstanceOfSatisfying(CoberturaException.class, e -> assertThat(e.campo()).isEqualTo("cantidad"));
    }

    @Test
    @DisplayName("Un total que no entra en NUMERIC(12,2) es inválido")
    void totalDesbordado() {
        assertThatThrownBy(() -> emitir(null, item("Consulta o control", "9999999999.99", 99, null, null, 0)))
                .isInstanceOfSatisfying(CoberturaException.class, e -> assertThat(e.campo()).isEqualTo("items"));
    }

    @Test
    @DisplayName("El día del vencimiento todavía no está vencido y el siguiente sí")
    void vencimiento() {
        Presupuesto p = pendienteConVencimiento(LocalDate.of(2026, 12, 24));
        assertThat(p.vencido(LocalDate.of(2026, 12, 24))).isFalse();
        assertThat(p.vencido(LocalDate.of(2026, 12, 25))).isTrue();
        assertThat(p.vencido(LocalDate.of(2026, 11, 24))).isFalse();
    }

    @Test
    @DisplayName("Un presupuesto aceptado o rechazado nunca está vencido")
    void cerradoNuncaVencido() {
        for (EstadoPresupuesto estado : List.of(EstadoPresupuesto.ACEPTADO, EstadoPresupuesto.RECHAZADO)) {
            Presupuesto p = Presupuesto.reconstruir(1L, 1L, 2L, HOY, HOY.plusDays(30), null, estado,
                    List.of(item("Consulta o control", "15000.00", 1, null, null, 0)), Instant.EPOCH);
            assertThat(p.vencido(HOY.plusDays(400))).isFalse();
        }
    }

    @Test
    @DisplayName("Un pendiente se acepta el día del vencimiento y se rechaza aunque esté vencido")
    void transicionesDeUnPendiente() {
        Presupuesto p = pendienteConVencimiento(LocalDate.of(2026, 12, 24));
        assertThat(p.aceptar(LocalDate.of(2026, 12, 24))).isEqualTo(EstadoPresupuesto.ACEPTADO);
        assertThat(p.rechazar(LocalDate.of(2026, 12, 24))).isEqualTo(EstadoPresupuesto.RECHAZADO);
        assertThat(p.rechazar(LocalDate.of(2026, 12, 30))).isEqualTo(EstadoPresupuesto.RECHAZADO);
    }

    @Test
    @DisplayName("Un presupuesto vencido no se acepta")
    void vencidoNoSeAcepta() {
        Presupuesto p = pendienteConVencimiento(LocalDate.of(2026, 12, 24));
        assertThatThrownBy(() -> p.aceptar(LocalDate.of(2026, 12, 25))).isInstanceOfSatisfying(CoberturaException.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoCobertura.PRESUPUESTO_VENCIDO));
    }

    @Test
    @DisplayName("Un presupuesto aceptado o rechazado no cambia de estado")
    void cerradoNoCambia() {
        for (EstadoPresupuesto estado : List.of(EstadoPresupuesto.ACEPTADO, EstadoPresupuesto.RECHAZADO)) {
            Presupuesto p = Presupuesto.reconstruir(1L, 1L, 2L, HOY, HOY.plusDays(30), null, estado,
                    List.of(item("Consulta o control", "15000.00", 1, null, null, 0)), Instant.EPOCH);
            assertThatThrownBy(() -> p.aceptar(HOY)).isInstanceOfSatisfying(CoberturaException.class,
                    e -> assertThat(e.codigo()).isEqualTo(CodigoCobertura.PRESUPUESTO_CERRADO));
            assertThatThrownBy(() -> p.rechazar(HOY)).isInstanceOfSatisfying(CoberturaException.class,
                    e -> assertThat(e.codigo()).isEqualTo(CodigoCobertura.PRESUPUESTO_CERRADO));
        }
    }
}
