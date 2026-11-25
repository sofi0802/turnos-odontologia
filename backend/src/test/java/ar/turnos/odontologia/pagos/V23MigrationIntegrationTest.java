package ar.turnos.odontologia.pagos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Restricciones de la migración V23 (design D8): las garantías de las señas también en la base. */
class V23MigrationIntegrationTest extends IntegracionPagosBase {

    private static final String INSERT_SENA = "INSERT INTO sena(turno_id, paciente_id, porcentaje, copago_base, importe, "
            + "estado, vence_en, referencia, creada_en) VALUES (?, ?, ?, 15000, ?, ?, now(), ?, now())";

    @Test
    @DisplayName("V23 permite una sola seña vigente por turno")
    void unaSenaVigentePorTurno() {
        long turno = turno(pacienteDos, consulta, "RESERVADO");
        jdbc.update(INSERT_SENA, turno, pacienteDos, 30, new BigDecimal("4500.00"), "PENDIENTE", "S-A");
        assertThatThrownBy(() -> jdbc.update(INSERT_SENA, turno, pacienteDos, 30, new BigDecimal("4500.00"),
                "PENDIENTE", "S-B")).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE sena SET estado = 'VENCIDA' WHERE referencia = 'S-A'");
        jdbc.update(INSERT_SENA, turno, pacienteDos, 30, new BigDecimal("4500.00"), "PENDIENTE", "S-B");
        assertThat(id("SELECT count(*) FROM sena WHERE turno_id = ?", turno)).isEqualTo(2);
    }

    @Test
    @DisplayName("V23 rechaza porcentajes, importes y reintegros fuera de rango")
    void rangos() {
        long turno = turno(pacienteDos, consulta, "RESERVADO");
        assertThatThrownBy(() -> jdbc.update(INSERT_SENA, turno, pacienteDos, 0, new BigDecimal("4500.00"),
                "PENDIENTE", "S-C")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(INSERT_SENA, turno, pacienteDos, 30, BigDecimal.ZERO, "PENDIENTE",
                "S-D")).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(INSERT_SENA, turno, pacienteDos, 30, new BigDecimal("4500.00"), "PENDIENTE", "S-E");
        assertThatThrownBy(() -> jdbc.update("UPDATE sena SET importe_a_reintegrar = 5000 WHERE referencia = 'S-E'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE sena SET importe_reintegrado = 100 WHERE referencia = 'S-E'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE sena SET estado = 'PAGADA' WHERE referencia = 'S-E'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("V23 no aplica dos veces el mismo movimiento externo")
    void movimientoExternoUnico() {
        String insert = "INSERT INTO sena_movimiento(tipo, id_externo, registrado_en) VALUES ('NOTIFICACION', ?, now())";
        jdbc.update(insert, "pago-1");
        assertThatThrownBy(() -> jdbc.update(insert, "pago-1")).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO sena_movimiento(tipo, registrado_en) VALUES ('VENCIMIENTO', now())");
        jdbc.update("INSERT INTO sena_movimiento(tipo, registrado_en) VALUES ('VENCIMIENTO', now())");
    }

    @Test
    @DisplayName("V23 deja el cobro sin caja solo con el medio MERCADO_PAGO")
    void cobroSinCajaSoloConMercadoPago() {
        long cargo = cargoAtendido(pacienteDos, consulta);
        long caja = cajaAbiertaHoy("1000.00");
        String sinCaja = "INSERT INTO cobro(cargo_id, caja_id, importe, medio, registrado_por, registrado_en) "
                + "VALUES (?, NULL, 100, ?, 'sistema', now())";
        jdbc.update(sinCaja, cargo, "MERCADO_PAGO");
        assertThatThrownBy(() -> jdbc.update(sinCaja, cargo, "EFECTIVO"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> cobroDirecto(cargo, caja, "100.00", "MERCADO_PAGO", false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
