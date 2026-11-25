package ar.turnos.odontologia.caja;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnos.odontologia.shared.security.Navegador;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Escenarios de la spec {@code caja} (C-22). */
class CajaIntegrationTest extends IntegracionCajaBase {

    private MvcResult abrir(Navegador navegador, Object monto) throws Exception {
        return navegador.post(CAJAS, cuerpoJson(Map.of("montoInicial", monto))).andReturn();
    }

    @Nested
    class AbrirLaCaja {

        @Test
        @DisplayName("La recepción abre la caja")
        void laRecepcionAbreLaCaja() throws Exception {
            como("recepcion").post(CAJAS, cuerpoJson(Map.of("montoInicial", "5000.00")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.fecha").value("2026-12-07"))
                    .andExpect(jsonPath("$.montoInicial").value(5000.00))
                    .andExpect(jsonPath("$.abiertaPor").value("recepcion"));
        }

        @Test
        @DisplayName("Monto inicial negativo")
        void montoInicialNegativo() throws Exception {
            como("recepcion").post(CAJAS, cuerpoJson(Map.of("montoInicial", "-1.00")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                    .andExpect(jsonPath("$.errores[0].campo").value("montoInicial"));
        }
    }

    @Nested
    class UnaSolaCajaAbierta {

        @Test
        @DisplayName("Ya hay una caja abierta")
        void yaHayUnaCajaAbierta() throws Exception {
            cajaAbiertaHoy("5000.00");
            como("recepcion").post(CAJAS, cuerpoJson(Map.of("montoInicial", "1000.00")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("CAJA_YA_ABIERTA"));
        }

        @Test
        @DisplayName("Caja de un día anterior sin cerrar")
        void cajaDeUnDiaAnteriorSinCerrar() throws Exception {
            long viernes = id("INSERT INTO caja(fecha, monto_inicial, abierta_por, abierta_en) VALUES "
                    + "(DATE '2026-12-04', 1000, 'recepcion', now()) RETURNING id");
            como("recepcion").post(CAJAS, cuerpoJson(Map.of("montoInicial", "5000.00")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("CAJA_YA_ABIERTA"))
                    .andExpect(jsonPath("$.cajaId").value(viernes));
        }

        @RepeatedTest(5)
        @DisplayName("Dos aperturas simultáneas")
        void dosAperturasSimultaneas() throws Exception {
            Navegador primera = como("recepcion");
            Navegador segunda = como("recepcion");
            CountDownLatch largada = new CountDownLatch(1);
            ExecutorService hilos = Executors.newFixedThreadPool(2);
            try {
                List<Future<MvcResult>> pedidos = new ArrayList<>();
                pedidos.add(hilos.submit(enLaLargada(largada, () -> abrir(primera, "5000.00"))));
                pedidos.add(hilos.submit(enLaLargada(largada, () -> abrir(segunda, "3000.00"))));
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
                assertThat(codigos).containsExactly("CAJA_YA_ABIERTA");
                assertThat(jdbc.queryForObject("SELECT count(*) FROM caja WHERE cerrada_en IS NULL", Integer.class))
                        .isEqualTo(1);
            } finally {
                hilos.shutdownNow();
            }
        }
    }

    @Nested
    class ConsultarLaCajaAbierta {

        @Test
        @DisplayName("Caja abierta con cobros")
        void cajaAbiertaConCobros() throws Exception {
            long caja = cajaAbiertaHoy("5000.00");
            long cargoUno = cargoAtendido(pacienteUno, consulta);
            long cargoDos = cargoAtendido(pacienteDos, consulta);
            cobroDirecto(cargoUno, caja, "9000.00", "EFECTIVO", false);
            cobroDirecto(cargoDos, caja, "15000.00", "DEBITO", false);
            cobroDirecto(cargoDos, caja, "2000.00", "EFECTIVO", true);

            como("recepcion").get(CAJAS + "/abierta")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totales.EFECTIVO").value(9000.00))
                    .andExpect(jsonPath("$.totales.DEBITO").value(15000.00))
                    .andExpect(jsonPath("$.totales.CREDITO").value(0.00))
                    .andExpect(jsonPath("$.totales.TRANSFERENCIA").value(0.00))
                    .andExpect(jsonPath("$.efectivoEsperado").value(14000.00))
                    .andExpect(jsonPath("$.cobros.length()").value(3));
        }

        @Test
        @DisplayName("Sin caja abierta")
        void sinCajaAbierta() throws Exception {
            como("recepcion").get(CAJAS + "/abierta").andExpect(status().isNoContent());
        }
    }

    @Nested
    class CerrarLaCajaConArqueo {

        private long cajaConEsperadoDe14000() {
            long caja = cajaAbiertaHoy("5000.00");
            cobroDirecto(cargoAtendido(pacienteUno, consulta), caja, "9000.00", "EFECTIVO", false);
            return caja;
        }

        @Test
        @DisplayName("Arqueo cuadrado")
        void arqueoCuadrado() throws Exception {
            long caja = cajaConEsperadoDe14000();
            Navegador recepcion = como("recepcion");

            recepcion.post(CAJAS + "/" + caja + "/cierre", cuerpoJson(Map.of("efectivoContado", "14000.00")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.diferencia").value(0.00))
                    .andExpect(jsonPath("$.efectivoEsperado").value(14000.00));
            recepcion.get(CAJAS + "/abierta").andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Arqueo descuadrado con motivo")
        void arqueoDescuadradoConMotivo() throws Exception {
            long caja = cajaConEsperadoDe14000();

            como("recepcion").post(CAJAS + "/" + caja + "/cierre",
                            cuerpoJson(Map.of("efectivoContado", "13500.00", "motivoDiferencia", "Faltante sin explicar")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.diferencia").value(-500.00))
                    .andExpect(jsonPath("$.motivoDiferencia").value("Faltante sin explicar"));
        }

        @Test
        @DisplayName("Arqueo descuadrado sin motivo")
        void arqueoDescuadradoSinMotivo() throws Exception {
            long caja = cajaConEsperadoDe14000();
            Navegador recepcion = como("recepcion");

            recepcion.post(CAJAS + "/" + caja + "/cierre", cuerpoJson(Map.of("efectivoContado", "14500.00")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("motivoDiferencia"));
            recepcion.get(CAJAS + "/abierta").andExpect(status().isOk());
        }

        @Test
        @DisplayName("Caja ya cerrada")
        void cajaYaCerrada() throws Exception {
            long viernes = cajaCerradaEl("2026-12-04", "1000.00", "1000.00", null);

            como("recepcion").post(CAJAS + "/" + viernes + "/cierre", cuerpoJson(Map.of("efectivoContado", "1000.00")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("CAJA_CERRADA"));
        }
    }

    @Nested
    class HistorialDeCajas {

        @Test
        @DisplayName("Historial de la semana")
        void historialDeLaSemana() throws Exception {
            long viernes = cajaCerradaEl("2026-12-04", "22000.00", "21500.00", "Faltante sin explicar");
            long hoy = cajaAbiertaHoy("5000.00");

            como("admin").get(CAJAS + "?desde=2026-12-01&hasta=2026-12-07")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(hoy))
                    .andExpect(jsonPath("$[0].diferencia").doesNotExist())
                    .andExpect(jsonPath("$[1].id").value(viernes))
                    .andExpect(jsonPath("$[1].diferencia").value(-500.00))
                    .andExpect(jsonPath("$[1].motivoDiferencia").value("Faltante sin explicar"));
        }

        @Test
        @DisplayName("El historial rechaza un período de más de 92 días en el campo hasta")
        void periodoLargo() throws Exception {
            como("admin").get(CAJAS + "?desde=2026-12-01&hasta=2027-03-03")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("hasta"));
        }
    }

    @Nested
    class PermisosDeLaCaja {

        @Test
        @DisplayName("El admin no abre la caja")
        void elAdminNoAbreLaCaja() throws Exception {
            como("admin").post(CAJAS, cuerpoJson(Map.of("montoInicial", "5000.00"))).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("El odontólogo no ve la caja")
        void elOdontologoNoVeLaCaja() throws Exception {
            como("ana.ejemplo").get(CAJAS + "/abierta").andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("El paciente no ve la caja")
        void elPacienteNoVeLaCaja() throws Exception {
            como("paciente.uno@example.com").get(CAJAS + "/abierta").andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Sin sesión en la caja")
        void sinSesionEnLaCaja() throws Exception {
            Navegador navegador = navegador();
            navegador.get(SESION);
            navegador.get(CAJAS + "/abierta").andExpect(status().isUnauthorized());
        }
    }

    static Callable<MvcResult> enLaLargada(CountDownLatch largada, Callable<MvcResult> pedido) {
        return () -> {
            largada.await();
            return pedido.call();
        };
    }
}
