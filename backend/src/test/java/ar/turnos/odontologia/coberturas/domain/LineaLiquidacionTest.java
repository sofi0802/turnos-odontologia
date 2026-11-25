package ar.turnos.odontologia.coberturas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion.Candidato;
import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion.CodigoCubierto;
import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion.Descarte;
import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion.Evaluacion;
import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion.OrigenPrecio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Reglas de una línea de liquidación (C-20, tarea 2.4, design D4 paso 3 y RN-LQ-01 y RN-LQ-03): qué turnos entran, con
 * qué código y por qué monto. Solo datos ficticios del seed de la KB.
 */
class LineaLiquidacionTest {

    private static final CodigoCubierto N0101_AL_100 = new CodigoCubierto(1L, "N-0101", 100);
    private static final CodigoCubierto N0102_AL_80 = new CodigoCubierto(2L, "N-0102", 80);
    private static final CodigoCubierto N0102_AL_0 = new CodigoCubierto(2L, "N-0102", 0);
    private static final CodigoCubierto N0201_AL_50 = new CodigoCubierto(3L, "N-0201", 50);
    private static final CodigoCubierto N0301_AL_0 = new CodigoCubierto(4L, "N-0301", 0);

    private static Candidato candidato(String arancel, CodigoCubierto... codigos) {
        return new Candidato(10L, 20L, 30L, 40L, LocalDate.of(2026, 10, 6), "Paciente Uno", "99000001", "OSE-000001",
                "Plan Base", "Consulta o control", arancel == null ? null : new BigDecimal(arancel), List.of(codigos));
    }

    @Test
    @DisplayName("Un solo código activo que el plan cubre es el código de la línea")
    void unSoloCodigo() {
        Evaluacion e = LineaLiquidacion.desde(candidato("15000.00", N0101_AL_100), null, null);

        assertThat(e.descarte()).isNull();
        LineaLiquidacion l = e.linea();
        assertThat(l.turnoId()).isEqualTo(10L);
        assertThat(l.pacienteId()).isEqualTo(20L);
        assertThat(l.planId()).isEqualTo(30L);
        assertThat(l.prestacionId()).isEqualTo(40L);
        assertThat(l.fecha()).isEqualTo(LocalDate.of(2026, 10, 6));
        assertThat(l.pacienteNombre()).isEqualTo("Paciente Uno");
        assertThat(l.pacienteDni()).isEqualTo("99000001");
        assertThat(l.numeroAfiliado()).isEqualTo("OSE-000001");
        assertThat(l.planNombre()).isEqualTo("Plan Base");
        assertThat(l.prestacionNombre()).isEqualTo("Consulta o control");
        assertThat(l.precio()).isEqualTo(new BigDecimal("15000.00"));
        assertThat(l.origenPrecio()).isEqualTo(OrigenPrecio.ARANCEL);
        assertThat(l.nomencladorId()).isEqualTo(1L);
        assertThat(l.codigo()).isEqualTo("N-0101");
        assertThat(l.porcentaje()).isEqualTo(100);
        assertThat(l.montoSolicitado()).isEqualTo(new BigDecimal("15000.00"));
        assertThat(l.codigoElegido()).isFalse();
        assertThat(l.tieneCodigo()).isTrue();
        assertThat(l.resultado()).isNull();
    }

    @Test
    @DisplayName("Un solo código que el plan cubre con 0 % descarta el turno")
    void unSoloCodigoAlCero() {
        Evaluacion e = LineaLiquidacion.desde(candidato("30000.00", N0301_AL_0), null, null);

        assertThat(e.linea()).isNull();
        assertThat(e.descarte()).isEqualTo(Descarte.NO_LIQUIDABLE);
    }

    @Test
    @DisplayName("Varios códigos y el plan cubre alguno deja la línea sin código ni monto")
    void variosCodigosConCobertura() {
        Evaluacion e = LineaLiquidacion.desde(candidato("15000.00", N0101_AL_100, N0102_AL_80), null, null);

        LineaLiquidacion l = e.linea();
        assertThat(l.tieneCodigo()).isFalse();
        assertThat(l.nomencladorId()).isNull();
        assertThat(l.codigo()).isNull();
        assertThat(l.porcentaje()).isNull();
        assertThat(l.montoSolicitado()).isNull();
        assertThat(l.codigoElegido()).isFalse();
        assertThat(l.precio()).isEqualTo(new BigDecimal("15000.00"));
    }

    @Test
    @DisplayName("Varios códigos y el plan no cubre ninguno descarta el turno")
    void variosCodigosSinCobertura() {
        Evaluacion e = LineaLiquidacion.desde(candidato("15000.00", N0102_AL_0, N0301_AL_0), null, null);

        assertThat(e.descarte()).isEqualTo(Descarte.NO_LIQUIDABLE);
    }

    @Test
    @DisplayName("Una prestación sin código activo descarta el turno")
    void sinCodigos() {
        Evaluacion e = LineaLiquidacion.desde(candidato("90000.00"), null, null);

        assertThat(e.descarte()).isEqualTo(Descarte.NO_LIQUIDABLE);
    }

    @Test
    @DisplayName("Sin arancel ni cargo el turno se cuenta como sin arancel")
    void sinArancel() {
        Evaluacion e = LineaLiquidacion.desde(candidato(null, N0201_AL_50), null, null);

        assertThat(e.linea()).isNull();
        assertThat(e.descarte()).isEqualTo(Descarte.SIN_ARANCEL);
    }

    @Test
    @DisplayName("Sin arancel pero sin cobertura el turno no cuenta como sin arancel")
    void sinArancelNiCobertura() {
        Evaluacion e = LineaLiquidacion.desde(candidato(null, N0301_AL_0), null, null);

        assertThat(e.descarte()).isEqualTo(Descarte.NO_LIQUIDABLE);
    }

    @Test
    @DisplayName("Con cargo de caja la línea usa el precio y el porcentaje del cargo")
    void conCargo() {
        CargoTurno cargo = new CargoTurno(new BigDecimal("14000.00"), 100, new BigDecimal("14000.00"));

        Evaluacion e = LineaLiquidacion.desde(candidato("15000.00", N0101_AL_100), cargo, null);

        LineaLiquidacion l = e.linea();
        assertThat(l.origenPrecio()).isEqualTo(OrigenPrecio.CARGO);
        assertThat(l.precio()).isEqualTo(new BigDecimal("14000.00"));
        assertThat(l.porcentaje()).isEqualTo(100);
        assertThat(l.montoSolicitado()).isEqualTo(new BigDecimal("14000.00"));
    }

    @Test
    @DisplayName("Con cargo no hace falta el arancel")
    void conCargoSinArancel() {
        CargoTurno cargo = new CargoTurno(new BigDecimal("14000.00"), 100, new BigDecimal("14000.00"));

        Evaluacion e = LineaLiquidacion.desde(candidato(null, N0101_AL_100), cargo, null);

        assertThat(e.linea().precio()).isEqualTo(new BigDecimal("14000.00"));
    }

    @Test
    @DisplayName("Un cargo con porcentaje 0 descarta el turno")
    void cargoAlCero() {
        CargoTurno cargo = new CargoTurno(new BigDecimal("25000.00"), 0, new BigDecimal("0.00"));

        Evaluacion e = LineaLiquidacion.desde(candidato("25000.00", N0201_AL_50), cargo, null);

        assertThat(e.descarte()).isEqualTo(Descarte.NO_LIQUIDABLE);
    }

    @Test
    @DisplayName("Con cargo del 50 % el monto es la mitad del precio del cargo")
    void cargoParcial() {
        CargoTurno cargo = new CargoTurno(new BigDecimal("24000.00"), 50, new BigDecimal("12000.00"));

        Evaluacion e = LineaLiquidacion.desde(candidato("25000.00", N0201_AL_50), cargo, null);

        assertThat(e.linea().montoSolicitado()).isEqualTo(new BigDecimal("12000.00"));
    }

    @Test
    @DisplayName("El código elegido por el administrador se conserva si el plan lo sigue cubriendo")
    void codigoElegidoConservado() {
        Evaluacion e = LineaLiquidacion.desde(candidato("15000.00", N0101_AL_100, N0102_AL_80), null, 2L);

        LineaLiquidacion l = e.linea();
        assertThat(l.codigo()).isEqualTo("N-0102");
        assertThat(l.porcentaje()).isEqualTo(80);
        assertThat(l.montoSolicitado()).isEqualTo(new BigDecimal("12000.00"));
        assertThat(l.codigoElegido()).isTrue();
    }

    @Test
    @DisplayName("El código elegido que el plan ya no cubre deja la línea sin código")
    void codigoElegidoYaNoCubierto() {
        Evaluacion e = LineaLiquidacion.desde(candidato("15000.00", N0101_AL_100, N0102_AL_0), null, 2L);

        LineaLiquidacion l = e.linea();
        assertThat(l.tieneCodigo()).isFalse();
        assertThat(l.codigoElegido()).isFalse();
    }

    @Test
    @DisplayName("Un código elegido que ya no existe entre los de la prestación se ignora")
    void codigoElegidoInexistente() {
        Evaluacion e = LineaLiquidacion.desde(candidato("15000.00", N0101_AL_100, N0102_AL_80), null, 99L);

        assertThat(e.linea().tieneCodigo()).isFalse();
    }

    @ParameterizedTest(name = "precio {0} al {1} % = {2}")
    @DisplayName("Montos del seed de la KB")
    @CsvSource({
            "15000.00,100,15000.00",
            "25000.00,50,12500.00",
            "15000.00,80,12000.00",
            "25000.00,80,20000.00",
            "30000.00,80,24000.00",
            "25000.15,50,12500.08",
            "0.05,10,0.01"})
    void montosDelSeed(String precio, int porcentaje, String esperado) {
        CodigoCubierto codigo = new CodigoCubierto(1L, "N-0101", porcentaje);

        LineaLiquidacion l = LineaLiquidacion.desde(candidato(precio, codigo), null, null).linea();

        assertThat(l.montoSolicitado()).isEqualTo(new BigDecimal(esperado));
        // el dominio usa la misma cuenta que los presupuestos (C-19)
        assertThat(l.montoSolicitado()).isEqualTo(CalculoItem.de(Precio.de(new BigDecimal(precio)), 1,
                Porcentaje.guardado(porcentaje)).cobertura());
    }

    @Test
    @DisplayName("Un monto que redondea a cero no se liquida")
    void montoCero() {
        CodigoCubierto codigo = new CodigoCubierto(1L, "N-0101", 1);

        Evaluacion e = LineaLiquidacion.desde(candidato("0.04", codigo), null, null);

        assertThat(e.descarte()).isEqualTo(Descarte.NO_LIQUIDABLE);
    }
}
