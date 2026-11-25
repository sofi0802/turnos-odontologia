package ar.turnos.odontologia.pagos.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnos.odontologia.pagos.application.PasarelaNoDisponibleException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La pasarela simulada de los tests y el desarrollo (design D6). */
class PasarelaSimuladaTest {

    private final PasarelaSimulada pasarela = new PasarelaSimulada();

    @Test
    @DisplayName("registra los links pedidos y devuelve un link a Mis turnos")
    void links() {
        String link = pasarela.crearLink("S-1", new BigDecimal("4500.00"), "Seña de turno", Instant.EPOCH);
        assertThat(link).contains("#/mis-turnos?pago=simulado");
        assertThat(pasarela.links()).singleElement().satisfies(l -> {
            assertThat(l.referencia()).isEqualTo("S-1");
            assertThat(l.importe()).isEqualByComparingTo("4500.00");
            assertThat(l.descripcion()).isEqualTo("Seña de turno");
        });
    }

    @Test
    @DisplayName("puede fallar al pedir el link")
    void falloDeLink() {
        pasarela.fallarLinks(true);
        assertThatThrownBy(() -> pasarela.crearLink("S-1", BigDecimal.ONE, "Seña de turno", Instant.EPOCH))
                .isInstanceOf(PasarelaNoDisponibleException.class);
    }

    @Test
    @DisplayName("informa los pagos que se le cargan")
    void pagos() {
        pasarela.cargarPago("p-1", "approved", "S-1", new BigDecimal("4500.00"));
        assertThat(pasarela.consultarPago("p-1")).hasValueSatisfying(p -> assertThat(p.aprobado()).isTrue());
        assertThat(pasarela.consultarPago("p-2")).isEmpty();
    }

    @Test
    @DisplayName("rechaza los primeros reintegros que se le pidan y después los acepta una sola vez por clave")
    void reintegros() {
        pasarela.rechazarReintegros(1);
        assertThatThrownBy(() -> pasarela.reintegrar("p-1", BigDecimal.TEN, "k-1"))
                .isInstanceOf(PasarelaNoDisponibleException.class);
        pasarela.reintegrar("p-1", BigDecimal.TEN, "k-1");
        pasarela.reintegrar("p-1", BigDecimal.TEN, "k-1");
        assertThat(pasarela.reintegros()).singleElement().satisfies(r -> {
            assertThat(r.idPago()).isEqualTo("p-1");
            assertThat(r.importe()).isEqualByComparingTo("10");
        });
    }
}
