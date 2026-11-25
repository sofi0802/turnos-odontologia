package ar.turnos.odontologia.caja;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Restricciones de la migración V15 (design D5): las garantías de la caja también en la base. */
class V15MigrationIntegrationTest extends IntegracionCajaBase {

    @Test
    @DisplayName("V15 no deja dos cajas abiertas")
    void dosCajasAbiertas() {
        cajaAbiertaHoy("1000.00");
        assertThatThrownBy(() -> cajaAbiertaHoy("2000.00")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("V15 exige el motivo de una diferencia de arqueo")
    void diferenciaSinMotivo() {
        assertThatThrownBy(() -> cajaCerradaEl("2026-12-04", "1000.00", "900.00", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("V15 permite un solo cargo por turno y exige precio igual a cobertura más copago")
    void cargoUnicoYCuentas() {
        long turno = turno(pacienteDos, consulta, "ATENDIDO");
        String insert = "INSERT INTO cargo(paciente_id, turno_id, prestacion_id, fecha, precio, porcentaje_cobertura, "
                + "cobertura, copago, creado_en) VALUES (?, ?, ?, DATE '2026-12-07', ?, 0, ?, ?, now())";
        jdbc.update(insert, pacienteDos, turno, consulta, new BigDecimal("100.00"), BigDecimal.ZERO,
                new BigDecimal("100.00"));
        assertThatThrownBy(() -> jdbc.update(insert, pacienteDos, turno, consulta, new BigDecimal("100.00"),
                BigDecimal.ZERO, new BigDecimal("100.00"))).isInstanceOf(DataIntegrityViolationException.class);
        long otro = turno(pacienteDos, consulta, "ATENDIDO");
        assertThatThrownBy(() -> jdbc.update(insert, pacienteDos, otro, consulta, new BigDecimal("100.00"),
                BigDecimal.ZERO, new BigDecimal("90.00"))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("V15 exige quién, cuándo y el motivo de una anulación, y un importe mayor que cero")
    void anulacionCompletaEImportePositivo() {
        long caja = cajaAbiertaHoy("1000.00");
        long cargo = cargoAtendido(pacienteDos, consulta);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO cobro(cargo_id, caja_id, importe, medio, registrado_por, "
                + "registrado_en, anulado) VALUES (?, ?, 10, 'EFECTIVO', 'recepcion', now(), true)", cargo, caja))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> cobroDirecto(cargo, caja, "0.00", "EFECTIVO", false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
