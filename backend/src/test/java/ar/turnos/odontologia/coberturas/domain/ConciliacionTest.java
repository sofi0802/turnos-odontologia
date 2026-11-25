package ar.turnos.odontologia.coberturas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Monto cobrado, resultado y motivo de la conciliación (C-20, tarea 2.3, RN-LQ-06). Solo datos ficticios. */
class ConciliacionTest {

    private static final BigDecimal SOLICITADO = new BigDecimal("15000.00");

    @ParameterizedTest(name = "cobrado {0} de 15000.00 = {1}")
    @DisplayName("Registrar lo cobrado de cada línea")
    @CsvSource({
            "15000.00,COBRADA",
            "10000.00,PARCIAL",
            "0.01,PARCIAL",
            "14999.99,PARCIAL",
            "0.00,RECHAZADA",
            "0,RECHAZADA",
            "15000,COBRADA"})
    void resultadoSegunElMonto(String cobrado, ResultadoConciliacion esperado) {
        assertThat(ResultadoConciliacion.de(new BigDecimal(cobrado), SOLICITADO)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "{0} es un monto cobrado inválido")
    @DisplayName("Monto cobrado y motivo válidos: montos inválidos")
    @CsvSource({"15000.01", "-1.00", "100.005", "-0.01", "99999999999.00"})
    void montoInvalido(String cobrado) {
        assertThatThrownBy(() -> MontoCobrado.de(new BigDecimal(cobrado), SOLICITADO))
                .isInstanceOfSatisfying(CoberturaException.class, e -> {
                    assertThat(e.codigo()).isEqualTo(CodigoCobertura.MONTO_COBRADO_INVALIDO);
                    assertThat(e.getMessage()).doesNotContain(cobrado);
                });
    }

    @Test
    @DisplayName("Un monto cobrado nulo es inválido")
    void montoNulo() {
        assertThatThrownBy(() -> MontoCobrado.de(null, SOLICITADO))
                .isInstanceOfSatisfying(CoberturaException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoCobertura.MONTO_COBRADO_INVALIDO));
    }

    @Test
    @DisplayName("Un monto con ceros de más se normaliza a dos decimales")
    void seNormalizaLaEscala() {
        assertThat(MontoCobrado.de(new BigDecimal("10000.0"), SOLICITADO).valor()).isEqualTo(new BigDecimal("10000.00"));
        assertThat(MontoCobrado.de(new BigDecimal("100.500"), SOLICITADO).valor()).isEqualTo(new BigDecimal("100.50"));
        assertThat(MontoCobrado.de(new BigDecimal("0"), SOLICITADO).valor()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("Una línea rechazada lleva motivo sin los espacios de los extremos")
    void rechazoConMotivo() {
        Conciliacion c = Conciliacion.de(new BigDecimal("0.00"), "  Falta la orden de la práctica ", SOLICITADO);
        assertThat(c.resultado()).isEqualTo(ResultadoConciliacion.RECHAZADA);
        assertThat(c.motivo()).isEqualTo("Falta la orden de la práctica");
        assertThat(c.montoCobrado()).isEqualTo(new BigDecimal("0.00"));
    }

    @ParameterizedTest
    @DisplayName("Rechazo sin motivo")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void rechazoSinMotivo(String motivo) {
        assertThatThrownBy(() -> Conciliacion.de(new BigDecimal("0.00"), motivo, SOLICITADO))
                .isInstanceOfSatisfying(CoberturaException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoCobertura.MOTIVO_REQUERIDO));
    }

    @Test
    @DisplayName("El motivo tiene a lo sumo 200 caracteres")
    void motivoLargo() {
        assertThat(Conciliacion.de(BigDecimal.ZERO, "x".repeat(200), SOLICITADO).motivo()).hasSize(200);
        assertThatThrownBy(() -> Conciliacion.de(BigDecimal.ZERO, "x".repeat(201), SOLICITADO))
                .isInstanceOfSatisfying(CoberturaException.class, e -> {
                    assertThat(e.codigo()).isEqualTo(CodigoCobertura.DATOS_INVALIDOS);
                    assertThat(e.campo()).isEqualTo("motivo");
                });
    }

    @Test
    @DisplayName("Un motivo en una línea que no queda rechazada es DATOS_INVALIDOS con el campo motivo")
    void motivoEnUnaCobrada() {
        for (String cobrado : new String[] {"15000.00", "10000.00"}) {
            assertThatThrownBy(() -> Conciliacion.de(new BigDecimal(cobrado), "Un motivo", SOLICITADO))
                    .isInstanceOfSatisfying(CoberturaException.class, e -> {
                        assertThat(e.codigo()).isEqualTo(CodigoCobertura.DATOS_INVALIDOS);
                        assertThat(e.campo()).isEqualTo("motivo");
                    });
        }
        assertThat(Conciliacion.de(new BigDecimal("15000.00"), "   ", SOLICITADO).motivo()).isNull();
        assertThat(Conciliacion.de(new BigDecimal("15000.00"), null, SOLICITADO).resultado())
                .isEqualTo(ResultadoConciliacion.COBRADA);
    }

    @Test
    @DisplayName("El monto inválido se informa antes que el motivo")
    void primeroElMonto() {
        assertThatThrownBy(() -> Conciliacion.de(new BigDecimal("-1.00"), null, SOLICITADO))
                .isInstanceOfSatisfying(CoberturaException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoCobertura.MONTO_COBRADO_INVALIDO));
    }
}
