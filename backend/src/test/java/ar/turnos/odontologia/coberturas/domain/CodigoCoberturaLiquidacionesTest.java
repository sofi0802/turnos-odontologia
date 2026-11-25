package ar.turnos.odontologia.coberturas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.coberturas.domain.CodigoCobertura.Estado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Estado HTTP de cada código nuevo de C-20 (tarea 2.1, design D11): los conflictos son 409 y los datos inválidos 422. */
class CodigoCoberturaLiquidacionesTest {

    @ParameterizedTest(name = "{0} es {1}")
    @DisplayName("Cada código nuevo de liquidaciones fija su estado")
    @CsvSource({
            "LIQUIDACION_EXISTENTE,CONFLICTO",
            "PERIODO_NO_CERRADO,CONFLICTO",
            "LIQUIDACION_PRESENTADA,CONFLICTO",
            "LIQUIDACION_CERRADA,CONFLICTO",
            "LIQUIDACION_NO_PRESENTADA,CONFLICTO",
            "LIQUIDACION_VACIA,CONFLICTO",
            "LINEA_SIN_CODIGO,CONFLICTO",
            "CONCILIACION_INCOMPLETA,CONFLICTO",
            "MONTO_COBRADO_INVALIDO,INVALIDO",
            "MOTIVO_REQUERIDO,INVALIDO"})
    void estadoDeCadaCodigo(String codigo, Estado estado) {
        assertThat(CodigoCobertura.valueOf(codigo).estado()).isEqualTo(estado);
    }

    @Test
    @DisplayName("Los códigos de C-18 y C-19 no cambian de estado")
    void losAnterioresSiguenIgual() {
        assertThat(CodigoCobertura.PRESUPUESTO_CERRADO.estado()).isEqualTo(Estado.CONFLICTO);
        assertThat(CodigoCobertura.CODIGO_NOMENCLADOR_INVALIDO.estado()).isEqualTo(Estado.INVALIDO);
        assertThat(CodigoCobertura.RECURSO_NO_ENCONTRADO.estado()).isEqualTo(Estado.NO_ENCONTRADO);
    }
}
