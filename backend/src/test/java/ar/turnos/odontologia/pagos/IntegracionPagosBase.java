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

    protected static final String NOTIFICACIONES = "/api/v1/public/pagos/mercadopago/notificaciones";
    protected static final String CLAVE_FIRMA = "clave-de-prueba-no-secreta";

    @org.springframework.beans.factory.annotation.Autowired
    private ar.turnos.odontologia.pagos.application.PasarelaPago pasarelaPago;

    @BeforeEach
    void vaciarSenas() {
        jdbc.execute("TRUNCATE sena_movimiento, sena RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM configuracion WHERE clave LIKE 'senas_%'");
        pasarela().reiniciar();
    }

    /** La pasarela simulada del perfil de test, para cargarle pagos y programarle fallos. */
    protected ar.turnos.odontologia.pagos.infrastructure.PasarelaSimulada pasarela() {
        return (ar.turnos.odontologia.pagos.infrastructure.PasarelaSimulada) pasarelaPago;
    }

    protected String referenciaDe(long sena) {
        return jdbc.queryForObject("SELECT referencia FROM sena WHERE id = ?", String.class, sena);
    }

    /** Una notificación de Mercado Pago sin sesión ni token CSRF, firmada con la clave indicada (o sin firma si es nula). */
    protected org.springframework.test.web.servlet.ResultActions notificar(String idPago, String clave) throws Exception {
        var pedido = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(NOTIFICACIONES)
                .param("data.id", idPago).param("type", "payment").header("x-request-id", "req-" + idPago)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"type\":\"payment\",\"data\":{\"id\":\"" + idPago + "\"}}");
        if (clave != null) {
            pedido.header("x-signature", ar.turnos.odontologia.pagos.domain.FirmaMercadoPago.firmar(idPago,
                    "req-" + idPago, "1765113600", clave));
        }
        return navegador().enviar(pedido);
    }

    protected org.springframework.test.web.servlet.ResultActions notificar(String idPago) throws Exception {
        return notificar(idPago, CLAVE_FIRMA);
    }

    /** Horario de lunes a viernes de 8 a 20 para "Ana Ejemplo", sus prestaciones y la cuenta de "Paciente Dos". */
    protected void habilitarReservaOnline() {
        for (int dia = 1; dia <= 5; dia++) {
            jdbc.update("INSERT INTO horario_trabajo(profesional_id, dia_semana, hora_desde, hora_hasta) "
                    + "VALUES (?, ?, '08:00'::time, '20:00'::time)", ana, dia);
        }
        jdbc.update("INSERT INTO profesional_prestacion(profesional_id, prestacion_id) VALUES (?,?),(?,?),(?,?)",
                ana, consulta, ana, limpieza, ana, extraccion);
        jdbc.update("INSERT INTO usuario(username, password_hash, rol, paciente_id) VALUES (?,?,'PACIENTE',?)",
                "paciente.dos@example.com", passwordEncoder.encode(clave), pacienteDos);
    }

    protected void activarSenas(int porcentaje, int plazoMinutos) {
        jdbc.update("DELETE FROM configuracion WHERE clave LIKE 'senas_%'");
        jdbc.update("INSERT INTO configuracion(clave, valor) VALUES ('senas_activas', 'true'), ('senas_porcentaje', ?), "
                + "('senas_plazo_minutos', ?)", String.valueOf(porcentaje), String.valueOf(plazoMinutos));
    }

    /** Un turno de 30 minutos de "Ana Ejemplo" en el sillón 1, en la fecha y hora locales indicadas. */
    protected long turnoA(long paciente, long prestacion, String fecha, String hora, String estado) {
        java.time.OffsetDateTime desde = java.time.LocalDateTime.of(java.time.LocalDate.parse(fecha),
                java.time.LocalTime.parse(hora)).atZone(ZONA).toOffsetDateTime();
        return id("INSERT INTO turno(profesional_id, sillon_id, prestacion_id, paciente_id, inicio, fin, fin_con_margen, "
                        + "estado) VALUES (?,?,?,?,?,?,?,?) RETURNING id", ana, sillon, prestacion, paciente, desde,
                desde.plusMinutes(30), desde.plusMinutes(40), estado);
    }

    protected String numeroDe(long turno) {
        return jdbc.queryForObject("SELECT codigo_reserva FROM turno WHERE id = ?", String.class, turno);
    }

    protected long movimientos(long sena, String tipo) {
        return id("SELECT count(*) FROM sena_movimiento WHERE sena_id = ? AND tipo = ?", sena, tipo);
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

    protected String causaDeSena(long sena) {
        return jdbc.queryForObject("SELECT causa_cancelacion FROM sena WHERE id = ?", String.class, sena);
    }

    protected String estadoDeSena(long sena) {
        return jdbc.queryForObject("SELECT estado FROM sena WHERE id = ?", String.class, sena);
    }
}
