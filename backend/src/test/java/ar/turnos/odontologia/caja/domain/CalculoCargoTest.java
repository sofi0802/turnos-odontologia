package ar.turnos.odontologia.caja.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Copago del cargo de un turno atendido (RN-CA-03, design D3): mismo cálculo que C-19. Datos ficticios: "Consulta o
 * control" a $15.000 con el código 01.01, que el "Plan 40" cubre al 40 %.
 */
class CalculoCargoTest {

    private static final BigDecimal PRECIO = new BigDecimal("15000.00");
    private static final long AFILIACION = 31L;
    private static final CodigoActivo CODIGO_0101 = new CodigoActivo(5L, "01.01");
    private static final CodigoActivo CODIGO_0102 = new CodigoActivo(6L, "01.02");

    @Test
    @DisplayName("Con cobertura del 40 %: cobertura 6.000 y copago 9.000")
    void conCobertura() {
        Tarifa tarifa = Tarifa.elegir(PRECIO, AFILIACION, List.of(CODIGO_0101), Map.of(5L, 40));

        CargoCalculado cargo = CalculoCargo.calcular(tarifa);

        assertThat(cargo.precio()).isEqualByComparingTo("15000.00");
        assertThat(cargo.porcentajeCobertura()).isEqualTo(40);
        assertThat(cargo.cobertura()).isEqualByComparingTo("6000.00");
        assertThat(cargo.copago()).isEqualByComparingTo("9000.00");
        assertThat(cargo.afiliacionId()).isEqualTo(AFILIACION);
    }

    @Test
    @DisplayName("Un paciente particular paga el precio completo")
    void particular() {
        Tarifa tarifa = Tarifa.elegir(PRECIO, null, List.of(CODIGO_0101), Map.of());

        CargoCalculado cargo = CalculoCargo.calcular(tarifa);

        assertThat(cargo.porcentajeCobertura()).isZero();
        assertThat(cargo.copago()).isEqualByComparingTo("15000.00");
        assertThat(cargo.afiliacionId()).isNull();
    }

    @Test
    @DisplayName("Sin código del nomenclador el cargo es particular aunque tenga cobertura")
    void sinCodigo() {
        Tarifa tarifa = Tarifa.elegir(new BigDecimal("30000.00"), AFILIACION, List.of(), Map.of());

        CargoCalculado cargo = CalculoCargo.calcular(tarifa);

        assertThat(cargo.porcentajeCobertura()).isZero();
        assertThat(cargo.copago()).isEqualByComparingTo("30000.00");
    }

    @Test
    @DisplayName("Con varios códigos activos se usa el primero en orden de código")
    void variosCodigos() {
        Tarifa tarifa = Tarifa.elegir(PRECIO, AFILIACION, List.of(CODIGO_0102, CODIGO_0101), Map.of(5L, 40, 6L, 80));

        assertThat(tarifa.porcentaje()).isEqualTo(40);
    }

    @Test
    @DisplayName("Un código sin porcentaje cargado en el plan no cubre nada")
    void codigoSinPorcentaje() {
        Tarifa tarifa = Tarifa.elegir(PRECIO, AFILIACION, List.of(CODIGO_0101), Map.of());

        assertThat(CalculoCargo.calcular(tarifa).copago()).isEqualByComparingTo("15000.00");
    }

    @Test
    @DisplayName("Con cobertura del 100 % el cargo queda en cero, ya saldado")
    void coberturaTotal() {
        Tarifa tarifa = Tarifa.elegir(PRECIO, AFILIACION, List.of(CODIGO_0101), Map.of(5L, 100));

        CargoCalculado cargo = CalculoCargo.calcular(tarifa);

        assertThat(cargo.copago()).isEqualByComparingTo("0.00");
        assertThat(cargo.cobertura()).isEqualByComparingTo("15000.00");
    }

    @Test
    @DisplayName("La cobertura redondea a centavos hacia arriba desde 0,5, como C-19")
    void redondeo() {
        Tarifa tarifa = Tarifa.elegir(new BigDecimal("100.01"), AFILIACION, List.of(CODIGO_0101), Map.of(5L, 50));

        CargoCalculado cargo = CalculoCargo.calcular(tarifa);

        assertThat(cargo.cobertura()).isEqualByComparingTo("50.01");
        assertThat(cargo.copago()).isEqualByComparingTo("50.00");
    }
}
