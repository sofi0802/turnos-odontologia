package ar.turnos.odontologia.coberturas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.coberturas.domain.CodigoCobertura.Estado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Estado HTTP de cada código nuevo de C-19 (tarea 2.1, design D9): los conflictos son 409 y los datos inválidos 422. */
class CodigoCoberturaPresupuestosTest {

    @ParameterizedTest(name = "{0} es {1}")
    @DisplayName("Cada código nuevo fija su estado")
    @CsvSource({
            "ARANCEL_INEXISTENTE,CONFLICTO",
            "PRESTACION_INACTIVA,CONFLICTO",
            "PRESUPUESTO_VENCIDO,CONFLICTO",
            "PRESUPUESTO_CERRADO,CONFLICTO",
            "CODIGO_NOMENCLADOR_REQUERIDO,INVALIDO",
            "CODIGO_NOMENCLADOR_INVALIDO,INVALIDO"})
    void estadoDeCadaCodigo(String codigo, Estado estado) {
        assertThat(CodigoCobertura.valueOf(codigo).estado()).isEqualTo(estado);
    }

    @Test
    @DisplayName("Los códigos de C-18 no cambian de estado")
    void losDeC18SiguenIgual() {
        assertThat(CodigoCobertura.PLAN_INACTIVO.estado()).isEqualTo(Estado.CONFLICTO);
        assertThat(CodigoCobertura.PRESTACION_INEXISTENTE.estado()).isEqualTo(Estado.INVALIDO);
        assertThat(CodigoCobertura.RECURSO_NO_ENCONTRADO.estado()).isEqualTo(Estado.NO_ENCONTRADO);
    }
}
