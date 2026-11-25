package ar.turnos.odontologia.caja.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Importes y arqueo de la caja (RN-CA-04, RN-CA-06), sin base de datos. */
class DineroYArqueoTest {

    private static Dinero pesos(String valor) {
        return Dinero.de(new BigDecimal(valor));
    }

    @Nested
    class Importes {

        @Test
        @DisplayName("Dinero normaliza a dos decimales y suma y resta sin perder centavos")
        void normalizaYOpera() {
            Dinero inicial = pesos("5000");

            assertThat(inicial.valor()).isEqualByComparingTo("5000.00");
            assertThat(inicial.valor().scale()).isEqualTo(2);
            assertThat(inicial.mas(pesos("0.10")).menos(pesos("0.05"))).isEqualTo(new BigDecimal("5000.05"));
            assertThat(pesos("100").menos(pesos("150"))).isEqualTo(new BigDecimal("-50.00"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"-0.01", "10.005", "10000000000.00"})
        @DisplayName("Dinero rechaza negativos, más de dos decimales y montos fuera de rango")
        void rechazaInvalidos(String valor) {
            assertThatThrownBy(() -> pesos(valor)).isInstanceOf(ImporteInvalidoException.class);
        }

        @Test
        @DisplayName("Un importe de cobro tiene que ser mayor que cero")
        void cobroMayorQueCero() {
            assertThatThrownBy(() -> Dinero.positivo(new BigDecimal("0.00"))).isInstanceOf(ImporteInvalidoException.class);
            assertThat(Dinero.positivo(new BigDecimal("0.01")).valor()).isEqualByComparingTo("0.01");
        }

        @Test
        @DisplayName("Medio de pago por nombre, y uno desconocido se rechaza")
        void medioDePago() {
            assertThat(MedioPago.desde("DEBITO")).isEqualTo(MedioPago.DEBITO);
            assertThatThrownBy(() -> MedioPago.desde("CHEQUE")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ArqueoDeLaCaja {

        @Test
        @DisplayName("Arqueo cuadrado: diferencia cero y sin motivo")
        void cuadrado() {
            Arqueo arqueo = Arqueo.de(pesos("14000"), pesos("14000"), "  ");

            assertThat(arqueo.diferencia()).isEqualByComparingTo("0.00");
            assertThat(arqueo.motivo()).isNull();
        }

        @Test
        @DisplayName("Arqueo con faltante: diferencia negativa y el motivo sin espacios")
        void faltante() {
            Arqueo arqueo = Arqueo.de(pesos("14000"), pesos("13500"), "  Faltante sin explicar ");

            assertThat(arqueo.diferencia()).isEqualByComparingTo("-500.00");
            assertThat(arqueo.motivo()).isEqualTo("Faltante sin explicar");
        }

        @Test
        @DisplayName("Con diferencia el motivo es obligatorio")
        void motivoObligatorio() {
            assertThatThrownBy(() -> Arqueo.de(pesos("14000"), pesos("14500"), null))
                    .isInstanceOf(MotivoObligatorioException.class);
            assertThatThrownBy(() -> Arqueo.de(pesos("14000"), pesos("14500"), "   "))
                    .isInstanceOf(MotivoObligatorioException.class);
        }
    }
}
