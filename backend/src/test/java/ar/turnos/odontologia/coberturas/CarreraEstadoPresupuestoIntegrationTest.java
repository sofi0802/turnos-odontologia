package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.shared.security.Navegador;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Carrera entre aceptar y rechazar (C-19, spec presupuestos, tarea 5.3, design D5): el {@code UPDATE} condicional hace
 * que uno solo gane y el otro reciba {@code PRESUPUESTO_CERRADO}, sin reintentos ni bloqueos de aplicación.
 */
class CarreraEstadoPresupuestoIntegrationTest extends IntegracionPresupuestosBase {

    @RepeatedTest(5)
    @DisplayName("Aceptar y rechazar a la vez")
    void aceptarYRechazarALaVez() throws Exception {
        // GIVEN un presupuesto PENDIENTE de "Paciente Uno"
        semilla();
        long presupuesto = emitirComo("ana.ejemplo", pacienteUno, item(consulta, 1));
        Navegador ana = como("ana.ejemplo");
        Navegador bruno = como("bruno.prueba");
        CountDownLatch largada = new CountDownLatch(1);

        // WHEN ana.ejemplo lo acepta y bruno.prueba lo rechaza al mismo tiempo
        List<MvcResult> resultados = enParalelo(largada,
                () -> post(ana, presupuestoUrl(pacienteUno, presupuesto) + "/aceptar", null),
                () -> post(bruno, presupuestoUrl(pacienteUno, presupuesto) + "/rechazar", null));

        // THEN uno responde 200 y el otro 409 con el código PRESUPUESTO_CERRADO
        assertThat(resultados).extracting(IntegracionCoberturasBase::status).containsExactlyInAnyOrder(200, 409);
        MvcResult ganador = resultados.stream().filter(r -> status(r) == 200).findFirst().orElseThrow();
        MvcResult perdedor = resultados.stream().filter(r -> status(r) == 409).findFirst().orElseThrow();
        assertThat(codigoDeError(perdedor)).isEqualTo("PRESUPUESTO_CERRADO");
        // AND el estado final es el del pedido que respondió 200
        assertThat(estadoGuardado(presupuesto)).isEqualTo(cuerpo(ganador).path("estado").asText());
    }

    @SafeVarargs
    private static List<MvcResult> enParalelo(CountDownLatch largada, Callable<MvcResult>... pedidos) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(pedidos.length);
        try {
            List<Future<MvcResult>> futuros = new ArrayList<>();
            for (Callable<MvcResult> pedido : pedidos) {
                futuros.add(hilos.submit(() -> {
                    largada.await();
                    return pedido.call();
                }));
            }
            largada.countDown();
            List<MvcResult> resultados = new ArrayList<>();
            for (Future<MvcResult> futuro : futuros) {
                resultados.add(futuro.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            hilos.shutdownNow();
        }
    }
}
