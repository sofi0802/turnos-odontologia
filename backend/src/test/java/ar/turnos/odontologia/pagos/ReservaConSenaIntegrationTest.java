package ar.turnos.odontologia.pagos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnos.odontologia.shared.security.Navegador;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Escenarios de la seña al reservar online: "Seña al reservar online", "Turnos que no piden seña" y "Link de pago y
 * datos para Mercado Pago" (spec {@code senas}), y los deltas de {@code reserva-online} y {@code turnos-paciente}.
 * Hoy es el lunes 2026-12-07 a las 12:00; las reservas son para el miércoles 2026-12-09.
 */
class ReservaConSenaIntegrationTest extends IntegracionPagosBase {

    private static final String RESERVAS = "/api/v1/paciente/reservas";
    private static final String MIS_TURNOS = "/api/v1/paciente/turnos";
    private static final String MIERCOLES_10 = "2026-12-09T10:00:00-03:00";

    @BeforeEach
    void prepararReserva() {
        for (int dia = 1; dia <= 5; dia++) {
            jdbc.update("INSERT INTO horario_trabajo(profesional_id, dia_semana, hora_desde, hora_hasta) "
                    + "VALUES (?, ?, '08:00'::time, '20:00'::time)", ana, dia);
        }
        jdbc.update("INSERT INTO profesional_prestacion(profesional_id, prestacion_id) VALUES (?,?),(?,?),(?,?)",
                ana, consulta, ana, limpieza, ana, extraccion);
        jdbc.update("INSERT INTO usuario(username, password_hash, rol, paciente_id) VALUES (?,?,'PACIENTE',?)",
                "paciente.dos@example.com", passwordEncoder.encode(clave), pacienteDos);
        senas(true, 30, 30);
    }

    private void senas(boolean activas, int porcentaje, int plazo) {
        jdbc.update("DELETE FROM configuracion WHERE clave LIKE 'senas_%'");
        jdbc.update("INSERT INTO configuracion(clave, valor) VALUES ('senas_activas', ?), ('senas_porcentaje', ?), "
                + "('senas_plazo_minutos', ?)", String.valueOf(activas), String.valueOf(porcentaje), String.valueOf(plazo));
    }

    private Navegador pacienteDos() throws Exception {
        return iniciarSesion("paciente.dos@example.com", clave);
    }

    private Navegador pacienteUno() throws Exception {
        return iniciarSesion("paciente.uno@example.com", clave);
    }

    private org.springframework.test.web.servlet.ResultActions reservar(Navegador navegador, long prestacion,
                                                                        String inicio) throws Exception {
        return navegador.post(RESERVAS, cuerpoJson(Map.of("prestacionId", prestacion, "inicio", inicio,
                "profesionalId", ana)));
    }

    private Map<String, Object> senaDelUnicoTurno(long paciente) {
        List<Map<String, Object>> filas = jdbc.queryForList("SELECT s.* FROM sena s JOIN turno t ON t.id = s.turno_id "
                + "WHERE t.paciente_id = ?", paciente);
        assertThat(filas).hasSize(1);
        return filas.get(0);
    }

    private long senasDe(long paciente) {
        return id("SELECT count(*) FROM sena WHERE paciente_id = ?", paciente);
    }

    private String numeroDelTurno(long turno) {
        return jdbc.queryForObject("SELECT codigo_reserva FROM turno WHERE id = ?", String.class, turno);
    }

    @Nested
    class SenaAlReservarOnline {

        @Test
        @DisplayName("Paciente particular")
        void pacienteParticular() throws Exception {
            reservar(pacienteDos(), consulta, MIERCOLES_10).andExpect(status().isCreated());

            Map<String, Object> sena = senaDelUnicoTurno(pacienteDos);
            assertThat((BigDecimal) sena.get("importe")).isEqualByComparingTo("4500.00");
            assertThat(sena.get("estado")).isEqualTo("PENDIENTE");
            assertThat(((java.sql.Timestamp) sena.get("vence_en")).toInstant())
                    .isEqualTo(instante("2026-12-07", "12:30"));
        }

        @Test
        @DisplayName("Paciente con cobertura")
        void pacienteConCobertura() throws Exception {
            reservar(pacienteUno(), consulta, MIERCOLES_10).andExpect(status().isCreated());

            assertThat((BigDecimal) senaDelUnicoTurno(pacienteUno).get("importe")).isEqualByComparingTo("2700.00");
        }
    }

    @Nested
    class TurnosQueNoPidenSena {

        @Test
        @DisplayName("Señas desactivadas")
        void senasDesactivadas() throws Exception {
            senas(false, 30, 30);
            reservar(pacienteDos(), consulta, MIERCOLES_10).andExpect(status().isCreated());
            assertThat(senasDe(pacienteDos)).isZero();
        }

        @Test
        @DisplayName("Copago cero")
        void copagoCero() throws Exception {
            jdbc.update("UPDATE cobertura_plan SET porcentaje = 100");
            reservar(pacienteUno(), consulta, MIERCOLES_10).andExpect(status().isCreated());
            assertThat(senasDe(pacienteUno)).isZero();
        }

        @Test
        @DisplayName("Prestación sin arancel")
        void prestacionSinArancel() throws Exception {
            reservar(pacienteDos(), limpieza, MIERCOLES_10).andExpect(status().isCreated());
            assertThat(senasDe(pacienteDos)).isZero();
        }

        @Test
        @DisplayName("Turno del personal sin seña")
        void turnoDelPersonalSinSena() throws Exception {
            como("recepcion").post("/api/v1/turnos", cuerpoJson(Map.of("pacienteId", pacienteDos, "profesionalId", ana,
                    "sillonId", sillon, "prestacionId", consulta, "inicio", MIERCOLES_10)))
                    .andExpect(status().isCreated());
            assertThat(senasDe(pacienteDos)).isZero();
        }
    }

    @Nested
    class LinkDePagoYDatosParaMercadoPago {

        @Test
        @DisplayName("Datos que recibe Mercado Pago")
        void datosQueRecibeMercadoPago() throws Exception {
            reservar(pacienteDos(), consulta, MIERCOLES_10).andExpect(status().isCreated());

            assertThat(pasarela().links()).singleElement().satisfies(link -> {
                assertThat(link.importe()).isEqualByComparingTo("4500.00");
                assertThat(link.descripcion()).isEqualTo("Seña de turno");
                assertThat(link.referencia()).doesNotContain("Consulta").doesNotContain("Paciente")
                        .doesNotContain("99000002");
            });
        }

        @Test
        @DisplayName("Pasarela caída al reservar")
        void pasarelaCaidaAlReservar() throws Exception {
            pasarela().fallarLinks(true);

            reservar(pacienteDos(), consulta, MIERCOLES_10)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.estado").value("RESERVADO"));

            Map<String, Object> sena = senaDelUnicoTurno(pacienteDos);
            assertThat(sena.get("estado")).isEqualTo("PENDIENTE");
            assertThat(sena.get("link_pago")).isNull();
        }
    }

    /** Delta de {@code reserva-online}. */
    @Nested
    class ReservaConSena {

        @Test
        @DisplayName("Reserva que crea una seña")
        void reservaQueCreaUnaSena() throws Exception {
            reservar(pacienteDos(), consulta, MIERCOLES_10)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.estado").value("RESERVADO"))
                    .andExpect(jsonPath("$.sena.estado").value("PENDIENTE"))
                    .andExpect(jsonPath("$.sena.importe").value(4500.00))
                    .andExpect(jsonPath("$.sena.venceEn").value(endsWith("T12:30:00-03:00")));
        }

        @Test
        @DisplayName("Reserva sin seña")
        void reservaSinSena() throws Exception {
            senas(false, 30, 30);
            reservar(pacienteDos(), consulta, MIERCOLES_10)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.sena").value(nullValue()));
        }

        @Test
        @DisplayName("Dos pacientes por el mismo hueco con señas activas")
        void dosPacientesPorElMismoHueco() throws Exception {
            Navegador uno = pacienteUno();
            Navegador dos = pacienteDos();
            CountDownLatch largada = new CountDownLatch(1);
            ExecutorService hilos = Executors.newFixedThreadPool(2);
            try {
                List<Future<MvcResult>> resultados = new ArrayList<>();
                for (Navegador navegador : List.of(uno, dos)) {
                    Callable<MvcResult> pedido = () -> {
                        largada.await();
                        return reservar(navegador, consulta, MIERCOLES_10).andReturn();
                    };
                    resultados.add(hilos.submit(pedido));
                }
                largada.countDown();
                List<Integer> estados = new ArrayList<>();
                for (Future<MvcResult> resultado : resultados) {
                    estados.add(resultado.get().getResponse().getStatus());
                }
                assertThat(estados).containsExactlyInAnyOrder(201, 409);
            } finally {
                hilos.shutdownNow();
            }
            assertThat(id("SELECT count(*) FROM sena")).isEqualTo(1);
        }
    }

    /** Delta de {@code turnos-paciente}. */
    @Nested
    class LaSenaEnMisTurnos {

        @Test
        @DisplayName("Turno con seña pendiente")
        void turnoConSenaPendiente() throws Exception {
            Navegador dos = pacienteDos();
            reservar(dos, consulta, MIERCOLES_10).andExpect(status().isCreated());

            dos.get(MIS_TURNOS)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].sena.estado").value("PENDIENTE"))
                    .andExpect(jsonPath("$[0].sena.importe").value(4500.00))
                    .andExpect(jsonPath("$[0].sena.venceEn").value(endsWith("T12:30:00-03:00")))
                    .andExpect(jsonPath("$[0].sena.linkPago").value(notNullValue()));
        }

        @Test
        @DisplayName("Turno con seña pagada")
        void turnoConSenaPagada() throws Exception {
            long turno = turnoEn(pacienteDos, consulta, "RESERVADO", "2026-12-09");
            senaDirecta(turno, pacienteDos, "4500.00", "PAGADA", Instant.now());

            pacienteDos().get(MIS_TURNOS)
                    .andExpect(jsonPath("$[0].sena.estado").value("PAGADA"))
                    .andExpect(jsonPath("$[0].sena.linkPago").value(nullValue()));
        }

        @Test
        @DisplayName("Turno sin seña")
        void turnoSinSena() throws Exception {
            turnoEn(pacienteDos, consulta, "RESERVADO", "2026-12-09");

            pacienteDos().get(MIS_TURNOS).andExpect(jsonPath("$[0].sena").value(nullValue()));
        }
    }

    /** Delta de {@code turnos-paciente}: el link nuevo. */
    @Nested
    class NuevoLinkDePagoDeLaSena {

        private String linkDe(String numero) {
            return RESERVAS + "/" + numero + "/sena/link";
        }

        @Test
        @DisplayName("Pedir un link nuevo")
        void pedirUnLinkNuevo() throws Exception {
            pasarela().fallarLinks(true);
            Navegador dos = pacienteDos();
            reservar(dos, consulta, MIERCOLES_10).andExpect(status().isCreated());
            pasarela().fallarLinks(false);
            long turno = id("SELECT turno_id FROM sena");

            dos.post(linkDe(numeroDelTurno(turno)), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.linkPago").value(notNullValue()));
        }

        @Test
        @DisplayName("Seña ya pagada")
        void senaYaPagada() throws Exception {
            long turno = turnoEn(pacienteDos, consulta, "RESERVADO", "2026-12-09");
            senaDirecta(turno, pacienteDos, "4500.00", "PAGADA", Instant.now());

            pacienteDos().post(linkDe(numeroDelTurno(turno)), null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("SENA_NO_PENDIENTE"));
        }

        @Test
        @DisplayName("Pasarela caída al pedir el link")
        void pasarelaCaidaAlPedirElLink() throws Exception {
            Navegador dos = pacienteDos();
            reservar(dos, consulta, MIERCOLES_10).andExpect(status().isCreated());
            pasarela().fallarLinks(true);
            long turno = id("SELECT turno_id FROM sena");

            dos.post(linkDe(numeroDelTurno(turno)), null)
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.codigo").value("PASARELA_NO_DISPONIBLE"));
        }
    }
}
