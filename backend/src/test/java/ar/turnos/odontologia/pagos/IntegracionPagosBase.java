package ar.turnos.odontologia.pagos;

import ar.turnos.odontologia.caja.IntegracionCajaBase;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base de los tests de integración de C-23 sobre la de C-22: los mismos datos ficticios (hoy es el lunes 2026-12-07 a
 * las 12:00; "Paciente Uno" con el 40 % de cobertura en "Consulta o control" y "Paciente Dos" particular) más las
 * tablas de las señas vacías.
 */
public abstract class IntegracionPagosBase extends IntegracionCajaBase {

    @BeforeEach
    void vaciarSenas() {
        jdbc.execute("TRUNCATE sena_movimiento, sena RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM configuracion WHERE clave LIKE 'senas_%'");
    }

    /** Inserta una seña directamente en la base, para preparar un escenario. */
    protected long senaDirecta(long turno, long paciente, String importe, String estado, Instant venceEn) {
        boolean pagada = estado.equals("PAGADA") || estado.equals("IMPUTADA");
        return id("INSERT INTO sena(turno_id, paciente_id, porcentaje, copago_base, importe, estado, vence_en, "
                        + "pagada_en, referencia, pago_externo_id, creada_en) VALUES (?, ?, 30, ?, ?, ?, ?, ?, ?, ?, now()) "
                        + "RETURNING id", turno, paciente, new BigDecimal(importe).multiply(BigDecimal.TEN).divide(
                        BigDecimal.valueOf(3), 2, java.math.RoundingMode.HALF_UP), new BigDecimal(importe), estado,
                venceEn.atOffset(ZoneOffset.UTC), pagada ? venceEn.atOffset(ZoneOffset.UTC) : null,
                "S-" + turno + "-" + System.nanoTime(), pagada ? "pago-" + turno : null);
    }

    /** PUT con la cookie y el encabezado CSRF, como el frontend. */
    protected org.springframework.test.web.servlet.ResultActions put(ar.turnos.odontologia.shared.security.Navegador
            navegador, String url, String cuerpo) throws Exception {
        return navegador.enviar(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(cuerpo)
                .header("X-XSRF-TOKEN", navegador.tokenCsrf() == null ? "" : navegador.tokenCsrf()));
    }

    protected String estadoDeSena(long sena) {
        return jdbc.queryForObject("SELECT estado FROM sena WHERE id = ?", String.class, sena);
    }
}
