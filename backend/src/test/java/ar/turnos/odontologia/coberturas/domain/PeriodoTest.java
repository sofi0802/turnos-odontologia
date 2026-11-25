package ar.turnos.odontologia.coberturas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Período de una liquidación (C-20, tarea 2.2, RN-LQ-01): mes cerrado, bordes y fin exclusivo en Buenos Aires. */
class PeriodoTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");

    @ParameterizedTest(name = "2026-10 con hoy {0}: cerrado = {1}")
    @DisplayName("Un período está cerrado cuando su último día es anterior a hoy")
    @CsvSource({
            "2026-10-31,false",
            "2026-11-01,true",
            "2026-11-24,true",
            "2026-10-01,false"})
    void cerradoONo(String hoy, boolean cerrado) {
        assertThat(Periodo.de("2026-10").estaCerrado(LocalDate.parse(hoy))).isEqualTo(cerrado);
    }

    @Test
    @DisplayName("El mes en curso no está cerrado")
    void elMesEnCursoNoEstaCerrado() {
        assertThat(Periodo.de("2026-11").estaCerrado(LocalDate.of(2026, 11, 24))).isFalse();
    }

    @Test
    @DisplayName("Primer y último día del período, también en febrero bisiesto")
    void primerYUltimoDia() {
        assertThat(Periodo.de("2026-10").primerDia()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(Periodo.de("2026-10").ultimoDia()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(Periodo.de("2028-02").ultimoDia()).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(Periodo.de("2026-12").ultimoDia()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    @DisplayName("El fin exclusivo es la medianoche de Buenos Aires del mes siguiente")
    void finExclusivo() {
        assertThat(Periodo.de("2026-10").finExclusivo(BUENOS_AIRES)).isEqualTo(Instant.parse("2026-11-01T03:00:00Z"));
        assertThat(Periodo.de("2026-12").finExclusivo(BUENOS_AIRES)).isEqualTo(Instant.parse("2027-01-01T03:00:00Z"));
    }

    @Test
    @DisplayName("El formato es yyyy-MM")
    void formato() {
        assertThat(Periodo.de("2026-09").toString()).isEqualTo("2026-09");
        assertThat(Periodo.de(YearMonth.of(2026, 10)).toString()).isEqualTo("2026-10");
        assertThat(Periodo.de("2026-10")).isEqualTo(Periodo.de(YearMonth.of(2026, 10)));
    }

    @ParameterizedTest
    @DisplayName("Un período mal formado es DATOS_INVALIDOS con el campo periodo")
    @NullSource
    @ValueSource(strings = {"2026-13", "2026-1", "2026/10", "octubre", "", "2026-10-01", "26-10"})
    void malFormado(String texto) {
        assertThatThrownBy(() -> Periodo.de(texto))
                .isInstanceOfSatisfying(CoberturaException.class, e -> {
                    assertThat(e.codigo()).isEqualTo(CodigoCobertura.DATOS_INVALIDOS);
                    assertThat(e.campo()).isEqualTo("periodo");
                });
    }
}
