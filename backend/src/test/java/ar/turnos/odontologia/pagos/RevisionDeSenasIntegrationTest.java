package ar.turnos.odontologia.pagos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnos.odontologia.notificaciones.domain.Consultorio;
import ar.turnos.odontologia.notificaciones.domain.DatosDelTurno;
import ar.turnos.odontologia.notificaciones.domain.MensajeRecordatorio;
import ar.turnos.odontologia.pagos.application.RevisionDeSenas;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Escenarios de la revisión periódica de las señas (spec {@code senas}: vencimiento, cancelación, reprogramación,
 * imputación, reintegros y privacidad) y del delta de {@code cobros-pacientes}. Hoy es el lunes 2026-12-07 a las 12:00.
 */
@ExtendWith(OutputCaptureExtension.class)
class RevisionDeSenasIntegrationTest extends IntegracionPagosBase {

    private static final Instant VENCE_12_30 = Instant.parse("2026-12-07T15:30:00Z");

    @Autowired
    private RevisionDeSenas revision;

    private long senaPagada(long turno, long paciente, String importe) {
        return senaDirecta(turno, paciente, importe, "PAGADA", VENCE_12_30);
    }

    private String estadoDelTurno(long turno) {
        return jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, turno);
    }

    private void cancelarComoRecepcion(long turno, String causa) throws Exception {
        como("recepcion").post("/api/v1/turnos/" + turno + "/cancelar", cuerpoJson(Map.of("dni", "99000002",
                "numeroReserva", numeroDe(turno), "causa", causa))).andExpect(status().isOk());
    }

    private long cobroDeLaSena(long sena) {
        return id("SELECT cobro_id FROM sena WHERE id = ?", sena);
    }

    private long senaImputadaDeDos() {
        long turno = turno(pacienteDos, consulta, "ATENDIDO");
        long sena = senaPagada(turno, pacienteDos, "4500.00");
        revision.ejecutar();
        return sena;
    }

    @Nested
    class VencimientoDeLaSena {

        @Test
        @DisplayName("Seña sin pagar a tiempo")
        void senaSinPagarATiempo() {
            long turno = turnoA(pacienteDos, consulta, "2026-12-09", "10:00", "RESERVADO");
            long sena = senaDirecta(turno, pacienteDos, "4500.00", "PENDIENTE", VENCE_12_30);
            reloj.ahoraEs(instante("2026-12-07", "12:31"));

            revision.ejecutar();

            assertThat(estadoDeSena(sena)).isEqualTo("VENCIDA");
            assertThat(estadoDelTurno(turno)).isEqualTo("CANCELADO");
            assertThat(jdbc.queryForObject("SELECT actor FROM turno_auditoria WHERE turno_id = ? AND accion = 'CANCELAR'",
                    String.class, turno)).isEqualTo("sistema");
            // El horario quedó libre: otro turno activo entra en el mismo sillón y profesional.
            assertThatCode(() -> turnoA(pacienteUno, consulta, "2026-12-09", "10:00", "RESERVADO"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Seña pagada no vence")
        void senaPagadaNoVence() {
            long turno = turnoA(pacienteDos, consulta, "2026-12-09", "10:00", "RESERVADO");
            long sena = senaPagada(turno, pacienteDos, "4500.00");
            reloj.ahoraEs(instante("2026-12-07", "12:31"));

            revision.ejecutar();

            assertThat(estadoDeSena(sena)).isEqualTo("PAGADA");
            assertThat(estadoDelTurno(turno)).isEqualTo("RESERVADO");
        }
    }

    @Nested
    class CancelacionDeUnTurnoConSenaPagada {

        @Test
        @DisplayName("El paciente cancela online a tiempo")
        void elPacienteCancelaOnlineATiempo() throws Exception {
            habilitarReservaOnline();
            long turno = turnoA(pacienteDos, consulta, "2026-12-09", "10:00", "RESERVADO");
            long sena = senaPagada(turno, pacienteDos, "4500.00");
            iniciarSesion("paciente.dos@example.com", clave)
                    .post("/api/v1/paciente/reservas/" + numeroDe(turno) + "/cancelar", null)
                    .andExpect(status().isOk());

            revision.ejecutar();

            assertThat(pasarela().reintegros()).singleElement()
                    .satisfies(r -> assertThat(r.importe()).isEqualByComparingTo("4500.00"));
            assertThat(estadoDeSena(sena)).isEqualTo("DEVUELTA");
        }

        @Test
        @DisplayName("El personal cancela con anticipación")
        void elPersonalCancelaConAnticipacion() throws Exception {
            long turno = turnoA(pacienteDos, consulta, "2026-12-09", "10:00", "RESERVADO");
            long sena = senaPagada(turno, pacienteDos, "4500.00");
            cancelarComoRecepcion(turno, "PACIENTE");

            revision.ejecutar();

            assertThat(estadoDeSena(sena)).isEqualTo("DEVUELTA");
        }

        @Test
        @DisplayName("El consultorio cancela tarde")
        void elConsultorioCancelaTarde() throws Exception {
            long turno = turnoA(pacienteDos, consulta, "2026-12-07", "16:00", "RESERVADO");
            long sena = senaPagada(turno, pacienteDos, "4500.00");
            cancelarComoRecepcion(turno, "CONSULTORIO");

            revision.ejecutar();

            assertThat(estadoDeSena(sena)).isEqualTo("DEVUELTA");
        }

        @Test
        @DisplayName("El paciente pide cancelar tarde")
        void elPacientePideCancelarTarde() throws Exception {
            long turno = turnoA(pacienteDos, consulta, "2026-12-07", "16:00", "RESERVADO");
            long sena = senaPagada(turno, pacienteDos, "4500.00");
            cancelarComoRecepcion(turno, "PACIENTE");

            revision.ejecutar();

            assertThat(estadoDeSena(sena)).isEqualTo("RETENIDA");
            assertThat(pasarela().reintegros()).isEmpty();
        }

        @Test
        @DisplayName("El paciente no viene")
        void elPacienteNoViene() {
            long turno = turno(pacienteDos, consulta, "AUSENTE");
            long sena = senaPagada(turno, pacienteDos, "4500.00");

            revision.ejecutar();

            assertThat(estadoDeSena(sena)).isEqualTo("RETENIDA");
        }
    }

    @Nested
    class ReprogramacionDeUnTurnoConSena {

        @Test
        @DisplayName("Reprogramar con la seña pagada")
        void reprogramarConLaSenaPagada() throws Exception {
            habilitarReservaOnline();
            long turno = turnoA(pacienteDos, consulta, "2026-12-09", "10:00", "RESERVADO");
            long sena = senaPagada(turno, pacienteDos, "4500.00");
            iniciarSesion("paciente.dos@example.com", clave)
                    .post("/api/v1/paciente/reservas/" + numeroDe(turno) + "/reprogramar",
                            cuerpoJson(Map.of("inicio", "2026-12-10T10:00:00-03:00", "profesionalId", ana)))
                    .andExpect(status().isOk());

            revision.ejecutar();

            long turnoNuevo = id("SELECT turno_id FROM sena WHERE id = ?", sena);
            assertThat(turnoNuevo).isNotEqualTo(turno);
            assertThat(jdbc.queryForObject("SELECT CAST(inicio AT TIME ZONE 'America/Argentina/Buenos_Aires' AS DATE) "
                    + "FROM turno WHERE id = ?", java.time.LocalDate.class, turnoNuevo)).hasToString("2026-12-10");
            assertThat(estadoDeSena(sena)).isEqualTo("PAGADA");
            assertThat((BigDecimal) jdbc.queryForObject("SELECT importe FROM sena WHERE id = ?", BigDecimal.class, sena))
                    .isEqualByComparingTo("4500.00");
            assertThat(id("SELECT count(*) FROM sena WHERE turno_id = ?", turno)).isZero();
        }
    }

    @Nested
    class ImputacionDeLaSenaAlCargo {

        @Test
        @DisplayName("Seña imputada")
        void senaImputada() {
            long sena = senaImputadaDeDos();

            assertThat(estadoDeSena(sena)).isEqualTo("IMPUTADA");
            assertThat(jdbc.queryForMap("SELECT medio, importe FROM cobro WHERE id = ?", cobroDeLaSena(sena)))
                    .containsEntry("medio", "MERCADO_PAGO");
            assertThat(jdbc.queryForObject("SELECT c.copago - coalesce(sum(cb.importe), 0) FROM cargo c "
                    + "LEFT JOIN cobro cb ON cb.cargo_id = c.id AND NOT cb.anulado WHERE c.paciente_id = ? GROUP BY c.copago",
                    BigDecimal.class, pacienteDos)).isEqualByComparingTo("10500.00");
        }

        @Test
        @DisplayName("Seña mayor que el copago")
        void senaMayorQueElCopago() {
            long control = id("INSERT INTO prestacion(nombre, duracion_minutos) VALUES ('Control breve', 15) RETURNING id");
            arancel(control, "3000.00");
            long turno = turno(pacienteDos, control, "ATENDIDO");
            long sena = senaPagada(turno, pacienteDos, "4500.00");

            revision.ejecutar();

            assertThat(jdbc.queryForObject("SELECT importe FROM cobro WHERE id = ?", BigDecimal.class,
                    cobroDeLaSena(sena))).isEqualByComparingTo("3000.00");
            assertThat(pasarela().reintegros()).singleElement()
                    .satisfies(r -> assertThat(r.importe()).isEqualByComparingTo("1500.00"));
            assertThat(estadoDeSena(sena)).isEqualTo("IMPUTADA");
        }

        @Test
        @DisplayName("Copago final cero")
        void copagoFinalCero() {
            jdbc.update("UPDATE cobertura_plan SET porcentaje = 100");
            long turno = turno(pacienteUno, consulta, "ATENDIDO");
            long sena = senaPagada(turno, pacienteUno, "2700.00");

            revision.ejecutar();

            assertThat(id("SELECT count(*) FROM cobro")).isZero();
            assertThat(pasarela().reintegros()).singleElement()
                    .satisfies(r -> assertThat(r.importe()).isEqualByComparingTo("2700.00"));
            assertThat(estadoDeSena(sena)).isEqualTo("DEVUELTA");
        }
    }

    /** Delta de {@code cobros-pacientes}: el cobro de la seña sin caja. */
    @Nested
    class CobroDeLaSenaSinCaja {

        @Test
        @DisplayName("Seña en la cuenta corriente")
        void senaEnLaCuentaCorriente() throws Exception {
            senaImputadaDeDos();

            como("admin").get("/api/v1/pacientes/" + pacienteDos + "/cuenta-corriente")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cargos[0].pagado").value(4500.00))
                    .andExpect(jsonPath("$.cargos[0].saldo").value(10500.00))
                    .andExpect(jsonPath("$.cargos[0].cobros[0].medio").value("MERCADO_PAGO"))
                    .andExpect(jsonPath("$.cargos[0].cobros[0].importe").value(4500.00));
        }

        @Test
        @DisplayName("La seña no entra en la caja")
        void laSenaNoEntraEnLaCaja() throws Exception {
            cajaAbiertaHoy("5000.00");
            senaImputadaDeDos();

            como("recepcion").get(CAJAS + "/abierta")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.efectivoEsperado").value(5000.00))
                    .andExpect(jsonPath("$.totales.MERCADO_PAGO").doesNotExist())
                    .andExpect(jsonPath("$.cobros.length()").value(0));
        }

        @Test
        @DisplayName("Imputar sin caja abierta")
        void imputarSinCajaAbierta() {
            long sena = senaImputadaDeDos();

            assertThat(jdbc.queryForMap("SELECT caja_id, medio, importe FROM cobro WHERE id = ?", cobroDeLaSena(sena)))
                    .containsEntry("caja_id", null).containsEntry("medio", "MERCADO_PAGO");
        }

        @Test
        @DisplayName("La recepción no cobra con Mercado Pago")
        void laRecepcionNoCobraConMercadoPago() throws Exception {
            cajaAbiertaHoy("5000.00");
            long cargo = cargoAtendido(pacienteDos, consulta);

            como("recepcion").post("/api/v1/cargos/" + cargo + "/cobros",
                            cuerpoJson(Map.of("importe", "100.00", "medio", "MERCADO_PAGO")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                    .andExpect(jsonPath("$.errores[0].campo").value("medio"));
        }

        @Test
        @DisplayName("El cobro de la seña no se anula desde la caja")
        void elCobroDeLaSenaNoSeAnula() throws Exception {
            long sena = senaImputadaDeDos();
            long cobro = cobroDeLaSena(sena);

            como("admin").post("/api/v1/cobros/" + cobro + "/anulacion", cuerpoJson(Map.of("motivo", "Prueba ficticia")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("COBRO_SIN_CAJA"));
            assertThat(jdbc.queryForObject("SELECT anulado FROM cobro WHERE id = ?", Boolean.class, cobro)).isFalse();
        }
    }

    @Nested
    class ReintegrosSinDuplicar {

        @Test
        @DisplayName("Reintegro rechazado")
        void reintegroRechazado() throws Exception {
            long turno = turnoA(pacienteDos, consulta, "2026-12-09", "10:00", "RESERVADO");
            long sena = senaPagada(turno, pacienteDos, "4500.00");
            cancelarComoRecepcion(turno, "CONSULTORIO");
            pasarela().rechazarReintegros(1);

            revision.ejecutar();
            revision.ejecutar();

            assertThat(movimientos(sena, "REINTEGRO_FALLIDO")).isEqualTo(1);
            assertThat(pasarela().reintegros()).singleElement()
                    .satisfies(r -> assertThat(r.importe()).isEqualByComparingTo("4500.00"));
            assertThat(estadoDeSena(sena)).isEqualTo("DEVUELTA");
        }
    }

    @Nested
    class PrivacidadDeLasSenas {

        @Test
        @DisplayName("Logs sin importes")
        void logsSinImportes(CapturedOutput salida) throws Exception {
            long turno = turnoA(pacienteDos, consulta, "2026-12-09", "10:00", "RESERVADO");
            long sena = senaDirecta(turno, pacienteDos, "4500.00", "PENDIENTE", VENCE_12_30);
            pasarela().cargarPago("p-log", "approved", referenciaDe(sena), new BigDecimal("4500.00"));
            int desde = salida.getOut().length();

            notificar("p-log").andExpect(status().isOk());
            cancelarComoRecepcion(turno, "CONSULTORIO");
            revision.ejecutar();

            assertThat(estadoDeSena(sena)).isEqualTo("DEVUELTA");
            String logs = salida.getOut().substring(desde);
            assertThat(logs).contains("Seña " + sena);
            assertThat(logs).doesNotContain("4500").doesNotContain("4.500").doesNotContain("Paciente")
                    .doesNotContain("Dos").doesNotContain("Consulta");
        }

        @Test
        @DisplayName("Aviso de reintegro sin importe")
        void avisoDeReintegroSinImporte() throws Exception {
            jdbc.update("UPDATE paciente SET consentimiento_comunicaciones = true, consentimiento_fecha = now() WHERE id = ?",
                    pacienteDos);
            long turno = turnoA(pacienteDos, consulta, "2026-12-09", "10:00", "RESERVADO");
            senaPagada(turno, pacienteDos, "4500.00");
            cancelarComoRecepcion(turno, "CONSULTORIO");

            revision.ejecutar();

            assertThat(id("SELECT count(*) FROM notificacion WHERE turno_id = ? AND tipo = 'REINTEGRO_SENA'", turno))
                    .isEqualTo(1);
            String mensaje = MensajeRecordatorio.reintegroSena(new DatosDelTurno(instante("2026-12-09", "10:00")),
                    new Consultorio("Consultorio ficticio", "Calle Ficticia 123")).texto();
            assertThat(mensaje).contains("09/12/2026").doesNotContain("4500").doesNotContain("4.500")
                    .doesNotContain("Consulta");
        }
    }
}
