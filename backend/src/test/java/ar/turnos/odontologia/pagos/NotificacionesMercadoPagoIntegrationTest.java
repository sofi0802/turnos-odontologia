package ar.turnos.odontologia.pagos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Escenarios de "Pago confirmado por la notificación de Mercado Pago" y "Notificaciones inválidas o repetidas" (spec
 * {@code senas}), y del delta de {@code seguridad-autorizacion}.
 */
class NotificacionesMercadoPagoIntegrationTest extends IntegracionPagosBase {

    private long senaPendiente() {
        long turno = turno(pacienteDos, consulta, "RESERVADO");
        return senaDirecta(turno, pacienteDos, "4500.00", "PENDIENTE", instante("2026-12-07", "12:30"));
    }

    private String estadoDelTurnoDe(long sena) {
        return jdbc.queryForObject("SELECT t.estado FROM sena s JOIN turno t ON t.id = s.turno_id WHERE s.id = ?",
                String.class, sena);
    }

    @Nested
    class PagoConfirmadoPorLaNotificacion {

        @Test
        @DisplayName("Pago aprobado")
        void pagoAprobado() throws Exception {
            long sena = senaPendiente();
            pasarela().cargarPago("p-1", "approved", referenciaDe(sena), new BigDecimal("4500.00"));

            notificar("p-1").andExpect(status().isOk());

            assertThat(estadoDeSena(sena)).isEqualTo("PAGADA");
            assertThat(estadoDelTurnoDe(sena)).isEqualTo("RESERVADO");
        }

        @Test
        @DisplayName("Pago no aprobado")
        void pagoNoAprobado() throws Exception {
            long sena = senaPendiente();
            pasarela().cargarPago("p-2", "rejected", referenciaDe(sena), new BigDecimal("4500.00"));

            notificar("p-2").andExpect(status().isOk());

            assertThat(estadoDeSena(sena)).isEqualTo("PENDIENTE");
        }

        @Test
        @DisplayName("Importe distinto")
        void importeDistinto() throws Exception {
            long sena = senaPendiente();
            pasarela().cargarPago("p-3", "approved", referenciaDe(sena), new BigDecimal("100.00"));

            notificar("p-3").andExpect(status().isOk());

            assertThat(estadoDeSena(sena)).isEqualTo("PENDIENTE");
            assertThat(movimientos(sena, "NOTIFICACION")).isEqualTo(1);
        }

        @Test
        @DisplayName("Pago tardío")
        void pagoTardio() throws Exception {
            long turno = turno(pacienteDos, consulta, "CANCELADO");
            long sena = senaDirecta(turno, pacienteDos, "4500.00", "VENCIDA", instante("2026-12-07", "11:30"));
            pasarela().cargarPago("p-4", "approved", referenciaDe(sena), new BigDecimal("4500.00"));

            notificar("p-4").andExpect(status().isOk());

            assertThat(estadoDelTurnoDe(sena)).isEqualTo("CANCELADO");
            assertThat(pasarela().reintegros()).singleElement().satisfies(r -> {
                assertThat(r.idPago()).isEqualTo("p-4");
                assertThat(r.importe()).isEqualByComparingTo("4500.00");
            });
            assertThat(estadoDeSena(sena)).isEqualTo("DEVUELTA");
        }
    }

    @Nested
    class NotificacionesInvalidasORepetidas {

        @Test
        @DisplayName("Firma inválida")
        void firmaInvalida() throws Exception {
            long sena = senaPendiente();
            pasarela().cargarPago("p-5", "approved", referenciaDe(sena), new BigDecimal("4500.00"));

            notificar("p-5", "otra-clave")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("FIRMA_INVALIDA"));

            assertThat(estadoDeSena(sena)).isEqualTo("PENDIENTE");
        }

        @Test
        @DisplayName("Notificación repetida")
        void notificacionRepetida() throws Exception {
            long sena = senaPendiente();
            pasarela().cargarPago("p-6", "approved", referenciaDe(sena), new BigDecimal("4500.00"));
            notificar("p-6").andExpect(status().isOk());

            notificar("p-6").andExpect(status().isOk());

            assertThat(estadoDeSena(sena)).isEqualTo("PAGADA");
            assertThat(movimientos(sena, "PAGO")).isEqualTo(1);
        }
    }

    /** Delta de {@code seguridad-autorizacion}: la única ruta fuera de CSRF (PA-64). */
    @Nested
    class NotificacionFueraDeCsrf {

        @Test
        @DisplayName("Notificación sin sesión ni token CSRF")
        void sinSesionNiCsrf() throws Exception {
            notificar("p-desconocido").andExpect(status().isOk());
        }

        @Test
        @DisplayName("Notificación sin firma")
        void sinFirma() throws Exception {
            notificar("p-7", null)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("FIRMA_INVALIDA"));
        }

        @Test
        @DisplayName("Otra ruta pública sigue con CSRF")
        void otraRutaPublica() throws Exception {
            navegador().postSinEncabezadoCsrf("/api/v1/public/confirmaciones/token-inexistente", null)
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Una ruta parecida no queda exceptuada")
        void rutaParecida() throws Exception {
            navegador().postSinEncabezadoCsrf("/api/v1/public/pagos/otra-cosa", "{}")
                    .andExpect(status().isForbidden());
        }
    }
}
