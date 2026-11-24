package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import ar.turnos.odontologia.coberturas.application.Afiliaciones;
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Carreras de afiliaciones, de punta a punta por la API (C-18, tarea 4.3): la {@code EXCLUDE} de la base frena a los
 * pedidos que pasaron a la vez la verificación del dominio (design D4), igual que la agenda con los turnos (DD-03).
 */
@Tag("integration")
@ExtendWith(OutputCaptureExtension.class)
class CarreraAfiliacionesIntegrationTest extends IntegracionCoberturasBase {

    /** Espía del puerto: deja demorar la verificación previa para forzar el peor orden de los pedidos. */
    @MockitoSpyBean
    private Afiliaciones afiliaciones;

    private long planBase;
    private long planOro;

    private void catalogo() {
        long ose = obraSocial("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL");
        planBase = plan(ose, "Plan Base");
        long prepaga = obraSocial("Prepaga Prueba", null, "PREPAGA");
        planOro = plan(prepaga, "Plan Oro");
    }

    @RepeatedTest(5)
    @DisplayName("Dos pedidos superpuestos a la vez")
    void dosPedidosSuperpuestosALaVez() throws Exception {
        // GIVEN "Paciente Uno" sin afiliaciones
        catalogo();
        Navegador recepcion = como("recepcion");
        Navegador admin = como("admin");
        CountDownLatch largada = new CountDownLatch(1);

        // WHEN llegan a la vez dos afiliaciones de "Paciente Uno" desde 2026-01-01, una a "Plan Base" y otra a "Plan Oro"
        List<MvcResult> resultados = enParalelo(largada,
                () -> post(recepcion, afiliaciones(pacienteUno), afiliacionSolicitud(planBase, "OSE-000001", "2026-01-01", null)),
                () -> post(admin, afiliaciones(pacienteUno), afiliacionSolicitud(planOro, "PP-77", "2026-01-01", null)));

        // THEN una responde 201 y la otra 409 con el código VIGENCIA_SUPERPUESTA
        assertThat(resultados).extracting(IntegracionCoberturasBase::status).containsExactlyInAnyOrder(201, 409);
        assertThat(resultados.stream().filter(r -> status(r) == 409).map(this::codigoSeguro))
                .containsExactly("VIGENCIA_SUPERPUESTA");
        // AND "Paciente Uno" tiene una sola afiliación
        assertThat(cantidad("afiliacion_paciente")).isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("Dos pedidos superpuestos que pasan juntos la verificación previa")
    void dosPedidosQuePasanJuntosLaVerificacionPrevia(CapturedOutput salida) throws Exception {
        // GIVEN "Paciente Uno" sin afiliaciones y los dos pedidos frenados después de leer sus afiliaciones, así
        // ninguno ve al otro: solo la EXCLUDE de la base puede impedir la superposición
        catalogo();
        CountDownLatch dosLeyeron = new CountDownLatch(2);
        doAnswer(invocacion -> {
            Object lectura = invocacion.callRealMethod();
            dosLeyeron.countDown();
            dosLeyeron.await(10, TimeUnit.SECONDS);
            return lectura;
        }).when(afiliaciones).delPaciente(anyLong());
        Navegador recepcion = como("recepcion");
        Navegador admin = como("admin");
        CountDownLatch largada = new CountDownLatch(1);

        // WHEN llegan a la vez dos afiliaciones superpuestas de "Paciente Uno"
        List<MvcResult> resultados = enParalelo(largada,
                () -> post(recepcion, afiliaciones(pacienteUno), afiliacionSolicitud(planBase, "OSE-000001", "2026-01-01", null)),
                () -> post(admin, afiliaciones(pacienteUno), afiliacionSolicitud(planOro, "PP-77", "2026-03-01", "2026-12-31")));

        // THEN una responde 201 y la otra 409 con VIGENCIA_SUPERPUESTA, traducido desde la restricción de la base
        assertThat(resultados).extracting(IntegracionCoberturasBase::status).containsExactlyInAnyOrder(201, 409);
        assertThat(resultados.stream().filter(r -> status(r) == 409).map(this::codigoSeguro))
                .containsExactly("VIGENCIA_SUPERPUESTA");
        assertThat(cantidad("afiliacion_paciente")).isEqualTo(1);
        // AND la violación de la restricción no dejó el número de afiliado en los logs (RN-OS-07)
        assertThat(salida.getAll()).doesNotContain("OSE-000001", "PP-77");
    }

    @RepeatedTest(5)
    @DisplayName("Dos pacientes con el mismo número que pasan juntos la verificación previa")
    void dosPacientesConElMismoNumero(CapturedOutput salida) throws Exception {
        // GIVEN "Paciente Uno" y "Paciente Dos" sin afiliaciones, con los dos pedidos frenados después de la lectura
        catalogo();
        CountDownLatch dosLeyeron = new CountDownLatch(2);
        doAnswer(invocacion -> {
            Object lectura = invocacion.callRealMethod();
            dosLeyeron.countDown();
            dosLeyeron.await(10, TimeUnit.SECONDS);
            return lectura;
        }).when(afiliaciones).delPaciente(anyLong());
        Navegador recepcion = como("recepcion");
        Navegador admin = como("admin");
        CountDownLatch largada = new CountDownLatch(1);

        // WHEN llegan a la vez dos afiliaciones con el mismo número en la misma obra social para pacientes distintos
        List<MvcResult> resultados = enParalelo(largada,
                () -> post(recepcion, afiliaciones(pacienteUno), afiliacionSolicitud(planBase, "OSE-000001", "2026-01-01", null)),
                () -> post(admin, afiliaciones(pacienteDos), afiliacionSolicitud(planBase, "ose-000001", "2026-01-01", null)));

        // THEN una responde 201 y la otra 409 con AFILIADO_DUPLICADO, sin repetir el número
        assertThat(resultados).extracting(IntegracionCoberturasBase::status).containsExactlyInAnyOrder(201, 409);
        MvcResult rechazada = resultados.stream().filter(r -> status(r) == 409).findFirst().orElseThrow();
        assertThat(codigoSeguro(rechazada)).isEqualTo("AFILIADO_DUPLICADO");
        assertThat(texto(rechazada)).doesNotContainIgnoringCase("OSE-000001");
        assertThat(cantidad("afiliacion_paciente")).isEqualTo(1);
        // AND la violación de la restricción no dejó el número de afiliado en los logs (RN-OS-07)
        assertThat(salida.getAll()).doesNotContainIgnoringCase("OSE-000001");
    }

    private String codigoSeguro(MvcResult resultado) {
        try {
            return codigoDeError(resultado);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
