package ar.turnos.odontologia.caja;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnos.odontologia.coberturas.application.ServicioPresupuestos;
import ar.turnos.odontologia.coberturas.application.ServicioPresupuestos.ItemPedido;
import ar.turnos.odontologia.shared.security.Navegador;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;

/** Escenarios de la spec {@code cobros-pacientes} (C-22). */
@ExtendWith(OutputCaptureExtension.class)
class CobrosIntegrationTest extends IntegracionCajaBase {

    @Autowired
    private ServicioPresupuestos presupuestos;

    private String cuenta(long paciente) {
        return "/api/v1/pacientes/" + paciente + "/cuenta-corriente";
    }

    private String cobros(long cargo) {
        return "/api/v1/cargos/" + cargo + "/cobros";
    }

    private MvcResult cobrar(Navegador navegador, long cargo, String importe, String medio) throws Exception {
        return navegador.post(cobros(cargo), cuerpoJson(Map.of("importe", importe, "medio", medio))).andReturn();
    }

    @Nested
    class CargoPorTurnoAtendido {

        @Test
        @DisplayName("Paciente con cobertura")
        void pacienteConCobertura() throws Exception {
            turno(pacienteUno, consulta, "ATENDIDO");

            como("recepcion").get(cuenta(pacienteUno))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cargos.length()").value(1))
                    .andExpect(jsonPath("$.cargos[0].precio").value(15000.00))
                    .andExpect(jsonPath("$.cargos[0].porcentajeCobertura").value(40))
                    .andExpect(jsonPath("$.cargos[0].cobertura").value(6000.00))
                    .andExpect(jsonPath("$.cargos[0].copago").value(9000.00));
            assertThat(jdbc.queryForObject("SELECT afiliacion_id FROM cargo", Long.class)).isEqualTo(afiliacionUno);
        }

        @Test
        @DisplayName("Paciente particular")
        void pacienteParticular() throws Exception {
            turno(pacienteDos, consulta, "ATENDIDO");

            como("recepcion").get(cuenta(pacienteDos))
                    .andExpect(jsonPath("$.cargos[0].porcentajeCobertura").value(0))
                    .andExpect(jsonPath("$.cargos[0].copago").value(15000.00));
        }

        @Test
        @DisplayName("Ausentes y cancelados no generan deuda")
        void ausentesYCanceladosNoGeneranDeuda() throws Exception {
            turno(pacienteDos, consulta, "AUSENTE");
            turno(pacienteDos, consulta, "CANCELADO");

            como("recepcion").get(cuenta(pacienteDos))
                    .andExpect(jsonPath("$.cargos.length()").value(0))
                    .andExpect(jsonPath("$.saldoTotal").value(0.00));
        }

        @Test
        @DisplayName("Un solo cargo por turno")
        void unSoloCargoPorTurno() throws Exception {
            long turno = turno(pacienteDos, consulta, "ATENDIDO");
            Navegador recepcion = como("recepcion");

            recepcion.get(cuenta(pacienteDos)).andExpect(status().isOk());
            servicioCobros.generarCargos();
            recepcion.get(cuenta(pacienteDos)).andExpect(jsonPath("$.cargos.length()").value(1));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM cargo WHERE turno_id = ?", Integer.class, turno))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("El copago del cargo coincide con el de un presupuesto de C-19 de la misma prestación")
        void coincideConElPresupuesto() {
            long cargo = cargoAtendido(pacienteUno, consulta);

            var presupuesto = presupuestos.emitir(pacienteUno, ana, List.of(new ItemPedido(consulta, null, null, null, 1)));

            assertThat(jdbc.queryForObject("SELECT copago FROM cargo WHERE id = ?", BigDecimal.class, cargo))
                    .isEqualByComparingTo(presupuesto.totales().copago());
        }
    }

    @Nested
    class ElCargoEsUnaFoto {

        @Test
        @DisplayName("Cambia el arancel después")
        void cambiaElArancelDespues() throws Exception {
            cargoAtendido(pacienteUno, consulta);
            arancel(consulta, "18000.00");

            como("recepcion").get(cuenta(pacienteUno))
                    .andExpect(jsonPath("$.cargos[0].precio").value(15000.00))
                    .andExpect(jsonPath("$.cargos[0].copago").value(9000.00));
        }
    }

    @Nested
    class TurnoSinPrecio {

        @Test
        @DisplayName("Atendido sin arancel")
        void atendidoSinArancel() throws Exception {
            long turno = turno(pacienteDos, limpieza, "ATENDIDO");

            como("recepcion").get("/api/v1/cargos/pendientes")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sinPrecio[0].turnoId").value(turno))
                    .andExpect(jsonPath("$.sinPrecio[0].prestacion").value("Limpieza (profilaxis)"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM cargo", Integer.class)).isZero();
        }

        @Test
        @DisplayName("Se carga el precio después")
        void seCargaElPrecioDespues() throws Exception {
            long turno = turno(pacienteDos, limpieza, "ATENDIDO");
            Navegador recepcion = como("recepcion");
            recepcion.get("/api/v1/cargos/pendientes").andExpect(jsonPath("$.sinPrecio.length()").value(1));

            arancel(limpieza, "25000.00");

            recepcion.get("/api/v1/cargos/pendientes")
                    .andExpect(jsonPath("$.sinPrecio.length()").value(0))
                    .andExpect(jsonPath("$.cargos[0].copago").value(25000.00));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM cargo WHERE turno_id = ?", Integer.class, turno))
                    .isEqualTo(1);
        }
    }

    @Nested
    class CoberturaSinCodigo {

        @Test
        @DisplayName("Prestación sin código")
        void prestacionSinCodigo() throws Exception {
            turno(pacienteUno, extraccion, "ATENDIDO");

            como("recepcion").get(cuenta(pacienteUno))
                    .andExpect(jsonPath("$.cargos[0].porcentajeCobertura").value(0))
                    .andExpect(jsonPath("$.cargos[0].copago").value(30000.00));
        }
    }

    @Nested
    class CobrarUnCargo {

        @Test
        @DisplayName("Cobro total")
        void cobroTotal() throws Exception {
            cajaAbiertaHoy("5000.00");
            long cargo = cargoAtendido(pacienteUno, consulta);

            como("recepcion").post(cobros(cargo), cuerpoJson(Map.of("importe", "9000.00", "medio", "EFECTIVO")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.saldo").value(0.00))
                    .andExpect(jsonPath("$.cobro.medio").value("EFECTIVO"));
        }

        @Test
        @DisplayName("Cobro parcial")
        void cobroParcial() throws Exception {
            cajaAbiertaHoy("5000.00");
            long cargo = cargoAtendido(pacienteUno, consulta);
            Navegador recepcion = como("recepcion");

            recepcion.post(cobros(cargo), cuerpoJson(Map.of("importe", "4000.00", "medio", "DEBITO")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.saldo").value(5000.00));
            recepcion.get(cuenta(pacienteUno)).andExpect(jsonPath("$.saldoTotal").value(5000.00));
        }

        @Test
        @DisplayName("Importe inválido")
        void importeInvalido() throws Exception {
            cajaAbiertaHoy("5000.00");
            long cargo = cargoAtendido(pacienteUno, consulta);
            Navegador recepcion = como("recepcion");

            for (String importe : List.of("0.00", "10.005")) {
                recepcion.post(cobros(cargo), cuerpoJson(Map.of("importe", importe, "medio", "EFECTIVO")))
                        .andExpect(status().isUnprocessableEntity())
                        .andExpect(jsonPath("$.errores[0].campo").value("importe"));
            }
            recepcion.post(cobros(cargo), cuerpoJson(Map.of("importe", "100.00", "medio", "CHEQUE")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("medio"));
        }
    }

    @Nested
    class RechazosDelCobro {

        @Test
        @DisplayName("Importe mayor que el saldo")
        void importeMayorQueElSaldo() throws Exception {
            long caja = cajaAbiertaHoy("5000.00");
            long cargo = cargoAtendido(pacienteUno, consulta);
            cobroDirecto(cargo, caja, "4000.00", "EFECTIVO", false);

            como("recepcion").post(cobros(cargo), cuerpoJson(Map.of("importe", "6000.00", "medio", "EFECTIVO")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("IMPORTE_SUPERA_SALDO"));
        }

        @Test
        @DisplayName("Cobro sin caja abierta")
        void cobroSinCajaAbierta() throws Exception {
            long cargo = cargoAtendido(pacienteUno, consulta);

            como("recepcion").post(cobros(cargo), cuerpoJson(Map.of("importe", "1000.00", "medio", "EFECTIVO")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("SIN_CAJA_ABIERTA"));
        }

        @Test
        @DisplayName("Un cargo inexistente responde 404")
        void cargoInexistente() throws Exception {
            cajaAbiertaHoy("5000.00");
            como("recepcion").post(cobros(999999), cuerpoJson(Map.of("importe", "1000.00", "medio", "EFECTIVO")))
                    .andExpect(status().isNotFound());
        }

        @RepeatedTest(5)
        @DisplayName("Cobros simultáneos")
        void cobrosSimultaneos() throws Exception {
            cajaAbiertaHoy("5000.00");
            long cargo = cargoAtendido(pacienteUno, consulta);
            Navegador primera = como("recepcion");
            Navegador segunda = como("recepcion");
            CountDownLatch largada = new CountDownLatch(1);
            ExecutorService hilos = Executors.newFixedThreadPool(2);
            try {
                List<Future<MvcResult>> pedidos = new ArrayList<>();
                pedidos.add(hilos.submit(CajaIntegrationTest.enLaLargada(largada,
                        () -> cobrar(primera, cargo, "6000.00", "EFECTIVO"))));
                pedidos.add(hilos.submit(CajaIntegrationTest.enLaLargada(largada,
                        () -> cobrar(segunda, cargo, "6000.00", "DEBITO"))));
                largada.countDown();
                List<Integer> estados = new ArrayList<>();
                List<String> codigos = new ArrayList<>();
                for (Future<MvcResult> pedido : pedidos) {
                    MvcResult resultado = pedido.get();
                    estados.add(resultado.getResponse().getStatus());
                    if (resultado.getResponse().getStatus() == 409) {
                        codigos.add(cuerpo(resultado).get("codigo").asText());
                    }
                }
                assertThat(estados).containsExactlyInAnyOrder(201, 409);
                assertThat(codigos).containsExactly("IMPORTE_SUPERA_SALDO");
                assertThat(jdbc.queryForObject("SELECT SUM(importe) FROM cobro WHERE NOT anulado", BigDecimal.class))
                        .isEqualByComparingTo("6000.00");
            } finally {
                hilos.shutdownNow();
            }
        }
    }

    @Nested
    class PendientesDeCobro {

        @Test
        @DisplayName("Pendientes del consultorio")
        void pendientesDelConsultorio() throws Exception {
            long caja = cajaAbiertaHoy("5000.00");
            long cargoUno = cargoAtendido(pacienteUno, consulta);
            long cargoDos = cargoAtendido(pacienteDos, consulta);
            cobroDirecto(cargoUno, caja, "4000.00", "EFECTIVO", false);
            cobroDirecto(cargoDos, caja, "15000.00", "DEBITO", false);

            como("recepcion").get("/api/v1/cargos/pendientes")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cargos.length()").value(1))
                    .andExpect(jsonPath("$.cargos[0].id").value(cargoUno))
                    .andExpect(jsonPath("$.cargos[0].paciente").value("Uno, Paciente"))
                    .andExpect(jsonPath("$.cargos[0].pagado").value(4000.00))
                    .andExpect(jsonPath("$.cargos[0].saldo").value(5000.00));
        }
    }

    @Nested
    class CuentaCorrienteDelPaciente {

        @Test
        @DisplayName("Cuenta con deuda")
        void cuentaConDeuda() throws Exception {
            long caja = cajaAbiertaHoy("5000.00");
            long cargo = cargoAtendido(pacienteUno, consulta);
            cobroDirecto(cargo, caja, "4000.00", "EFECTIVO", false);
            cobroDirecto(cargo, caja, "1000.00", "EFECTIVO", true);

            como("admin").get(cuenta(pacienteUno))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paciente").value("Uno, Paciente"))
                    .andExpect(jsonPath("$.cargos[0].cobros.length()").value(2))
                    .andExpect(jsonPath("$.cargos[0].cobros[1].anulado").value(true))
                    .andExpect(jsonPath("$.cargos[0].saldo").value(5000.00))
                    .andExpect(jsonPath("$.saldoTotal").value(5000.00));
        }

        @Test
        @DisplayName("Un paciente inexistente responde 404 en la cuenta corriente")
        void pacienteInexistente() throws Exception {
            como("admin").get(cuenta(999999)).andExpect(status().isNotFound());
        }
    }

    @Nested
    class AnularUnCobro {

        @Test
        @DisplayName("El admin anula un cobro")
        void elAdminAnulaUnCobro() throws Exception {
            long caja = cajaAbiertaHoy("5000.00");
            long cargo = cargoAtendido(pacienteUno, consulta);
            long cobro = cobroDirecto(cargo, caja, "4000.00", "EFECTIVO", false);
            Navegador admin = como("admin");

            admin.post("/api/v1/cobros/" + cobro + "/anulacion",
                            cuerpoJson(Map.of("motivo", "Cargado al paciente equivocado")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.anulado").value(true))
                    .andExpect(jsonPath("$.anuladoPor").value("admin"));
            admin.get(cuenta(pacienteUno)).andExpect(jsonPath("$.cargos[0].saldo").value(9000.00));
            admin.get(CAJAS + "/abierta").andExpect(jsonPath("$.totales.EFECTIVO").value(0.00));
        }

        @Test
        @DisplayName("Anular con la caja cerrada")
        void anularConLaCajaCerrada() throws Exception {
            long viernes = cajaCerradaEl("2026-12-04", "1000.00", "1000.00", null);
            long cargo = cargoAtendido(pacienteUno, consulta);
            long cobro = cobroDirecto(cargo, viernes, "4000.00", "DEBITO", false);

            como("admin").post("/api/v1/cobros/" + cobro + "/anulacion", cuerpoJson(Map.of("motivo", "Error de carga")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("CAJA_CERRADA"));
        }

        @Test
        @DisplayName("Anular sin motivo")
        void anularSinMotivo() throws Exception {
            long caja = cajaAbiertaHoy("5000.00");
            long cobro = cobroDirecto(cargoAtendido(pacienteUno, consulta), caja, "4000.00", "EFECTIVO", false);

            como("admin").post("/api/v1/cobros/" + cobro + "/anulacion", cuerpoJson(Map.of("motivo", "  ")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("motivo"));
        }

        @Test
        @DisplayName("Un cobro ya anulado no se anula dos veces")
        void yaAnulado() throws Exception {
            long caja = cajaAbiertaHoy("5000.00");
            long cobro = cobroDirecto(cargoAtendido(pacienteUno, consulta), caja, "4000.00", "EFECTIVO", true);

            como("admin").post("/api/v1/cobros/" + cobro + "/anulacion", cuerpoJson(Map.of("motivo", "Otra vez")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("COBRO_ANULADO"));
        }
    }

    @Nested
    class PermisosDeLosCobros {

        @Test
        @DisplayName("El admin no cobra")
        void elAdminNoCobra() throws Exception {
            cajaAbiertaHoy("5000.00");
            long cargo = cargoAtendido(pacienteUno, consulta);
            como("admin").post(cobros(cargo), cuerpoJson(Map.of("importe", "1000.00", "medio", "EFECTIVO")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("La recepción no anula")
        void laRecepcionNoAnula() throws Exception {
            long caja = cajaAbiertaHoy("5000.00");
            long cobro = cobroDirecto(cargoAtendido(pacienteUno, consulta), caja, "4000.00", "EFECTIVO", false);
            como("recepcion").post("/api/v1/cobros/" + cobro + "/anulacion", cuerpoJson(Map.of("motivo", "Error")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("El odontólogo no ve la cuenta corriente")
        void elOdontologoNoVeLaCuentaCorriente() throws Exception {
            como("ana.ejemplo").get(cuenta(pacienteUno)).andExpect(status().isForbidden());
        }
    }

    @Nested
    class ImportesFueraDeLosLogs {

        @Test
        @DisplayName("Log de un cobro")
        void logDeUnCobro(CapturedOutput salida) throws Exception {
            cajaAbiertaHoy("5000.00");
            long cargo = cargoAtendido(pacienteUno, consulta);

            como("recepcion").post(cobros(cargo), cuerpoJson(Map.of("importe", "9000.00", "medio", "EFECTIVO")))
                    .andExpect(status().isCreated());

            assertThat(salida.getAll()).contains("Cobro").doesNotContain("9000").doesNotContain("Paciente")
                    .doesNotContain("99000001");
        }
    }
}
