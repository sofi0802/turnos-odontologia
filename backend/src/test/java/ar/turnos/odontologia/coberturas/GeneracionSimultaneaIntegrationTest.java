package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import ar.turnos.odontologia.coberturas.application.Liquidaciones;
import ar.turnos.odontologia.coberturas.application.TurnosLiquidables;
import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion.Candidato;
import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion.CodigoCubierto;
import ar.turnos.odontologia.coberturas.domain.Periodo;
import ar.turnos.odontologia.shared.security.Navegador;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Generaciones simultáneas (C-20, spec liquidaciones-obras-sociales, tarea 3.4, design D4): la restricción única de la
 * base frena a los pedidos que pasaron a la vez la verificación del dominio, igual que las afiliaciones de C-18.
 * Reloj: 2026-11-24 a las 12:00 en Buenos Aires. Solo datos ficticios.
 */
class GeneracionSimultaneaIntegrationTest extends IntegracionLiquidacionesBase {

    /** Espías de los puertos: dejan demorar la lectura para forzar el peor orden de los pedidos. */
    @MockitoSpyBean
    private Liquidaciones liquidaciones;

    @MockitoSpyBean
    private TurnosLiquidables turnosLiquidables;

    @RepeatedTest(5)
    @DisplayName("Dos generaciones a la vez")
    void dosGeneracionesALaVez() throws Exception {
        // GIVEN "Paciente Uno" tiene dos turnos ATENDIDO de octubre
        semilla();
        atendido(pacienteUno, consulta, "2026-10-06T10:00");
        atendido(pacienteUno, limpieza, "2026-10-20T10:00");
        Navegador unAdmin = como("admin");
        Navegador otroAdmin = como("admin");

        // WHEN dos pedidos de admin generan al mismo tiempo la liquidación de "Obra Social Ejemplo" para 2026-10
        List<MvcResult> resultados = enParalelo(
                () -> post(unAdmin, LIQUIDACIONES, generacion(obraSocialEjemplo, "2026-10")),
                () -> post(otroAdmin, LIQUIDACIONES, generacion(obraSocialEjemplo, "2026-10")));

        // THEN uno recibe 201, el otro 409 con el código LIQUIDACION_EXISTENTE y hay una sola liquidación con sus líneas
        assertThat(resultados).extracting(IntegracionCoberturasBase::status).containsExactlyInAnyOrder(201, 409);
        MvcResult rechazada = resultados.stream().filter(r -> status(r) == 409).findFirst().orElseThrow();
        assertThat(codigoDeError(rechazada)).isEqualTo("LIQUIDACION_EXISTENTE");
        assertThat(texto(rechazada)).doesNotContain("Paciente Uno", "99000001", "OSE-000001", "15000");
        assertThat(cantidad("liquidacion")).isEqualTo(1);
        assertThat(cantidad("liquidacion_linea")).isEqualTo(2);
        long liquidacion = jdbc.queryForObject("SELECT id FROM liquidacion", Long.class);
        assertThat(totalSolicitadoGuardado(liquidacion)).isEqualByComparingTo("27500.00");
        assertThat(sumaDeLasLineas(liquidacion)).isEqualByComparingTo("27500.00");
    }

    @RepeatedTest(5)
    @DisplayName("Dos generaciones que pasan juntas la verificación previa")
    void dosGeneracionesQuePasanJuntasLaVerificacionPrevia() throws Exception {
        // GIVEN un turno atendido y los dos pedidos frenados después de verificar que la liquidación no existe, así
        // ninguno ve al otro: solo liquidacion_periodo_unico puede impedir la segunda
        semilla();
        atendido(pacienteUno, consulta, "2026-10-06T10:00");
        CountDownLatch dosVerificaron = new CountDownLatch(2);
        doAnswer(invocacion -> {
            Object existe = invocacion.callRealMethod();
            dosVerificaron.countDown();
            dosVerificaron.await(10, TimeUnit.SECONDS);
            return existe;
        }).when(liquidaciones).existe(anyLong(), any(Periodo.class));
        Navegador unAdmin = como("admin");
        Navegador otroAdmin = como("admin");

        // WHEN llegan a la vez dos pedidos para la misma obra social y período
        List<MvcResult> resultados = enParalelo(
                () -> post(unAdmin, LIQUIDACIONES, generacion(obraSocialEjemplo, "2026-10")),
                () -> post(otroAdmin, LIQUIDACIONES, generacion(obraSocialEjemplo, "2026-10")));

        // THEN uno recibe 201 y el otro 409 LIQUIDACION_EXISTENTE, traducido desde la restricción de la base
        assertThat(resultados).extracting(IntegracionCoberturasBase::status).containsExactlyInAnyOrder(201, 409);
        assertThat(resultados.stream().filter(r -> status(r) == 409).map(r -> {
            try {
                return codigoDeError(r);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        })).containsExactly("LIQUIDACION_EXISTENTE");
        assertThat(cantidad("liquidacion")).isEqualTo(1);
        assertThat(cantidad("liquidacion_linea")).isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("Dos obras sociales que se disputan un turno")
    void dosObrasSocialesQueSeDisputanUnTurno() throws Exception {
        // GIVEN "Paciente Uno" con un turno atendido en octubre, afiliado a "Plan Base" de "Obra Social Ejemplo", y una
        // corrección de afiliación entre medio hace que "Prepaga Prueba" también lo vea como suyo: la generación de la
        // prepaga lee los candidatos de la obra social, después de que esta los leyó
        semilla();
        long turno = atendido(pacienteUno, consulta, "2026-10-06T10:00");
        CountDownLatch obraSocialLeyo = new CountDownLatch(1);
        AtomicInteger lecturasDeLaPrepaga = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<List<Candidato>> deLaObraSocial =
                new java.util.concurrent.atomic.AtomicReference<>(List.of());
        doAnswer(invocacion -> {
            Long obraSocial = invocacion.getArgument(0);
            @SuppressWarnings("unchecked")
            List<Candidato> reales = (List<Candidato>) invocacion.callRealMethod();
            if (obraSocial.equals(obraSocialEjemplo)) {
                deLaObraSocial.set(reales);
                obraSocialLeyo.countDown();
                return reales;
            }
            if (lecturasDeLaPrepaga.getAndIncrement() > 0) {
                return reales; // el reintento ve la base real: el turno ya es de la otra liquidación
            }
            obraSocialLeyo.await(10, TimeUnit.SECONDS);
            return deLaObraSocial.get().stream()
                    .map(c -> new Candidato(c.turnoId(), c.pacienteId(), planOro, c.prestacionId(), c.fecha(),
                            c.pacienteNombre(), c.pacienteDni(), "PP-000001", "Plan Oro", c.prestacionNombre(),
                            c.arancel(), List.of(new CodigoCubierto(n0101, "N-0101", 80))))
                    .toList();
        }).when(turnosLiquidables).candidatos(anyLong(), any(Instant.class), anyLong());
        Navegador unAdmin = como("admin");
        Navegador otroAdmin = como("admin");

        // WHEN generan a la vez las liquidaciones de 2026-10 de las dos obras sociales
        List<MvcResult> resultados = enParalelo(
                () -> post(unAdmin, LIQUIDACIONES, generacion(obraSocialEjemplo, "2026-10")),
                () -> post(otroAdmin, LIQUIDACIONES, generacion(prepaga, "2026-10")));

        // THEN las dos responden 201 pero el turno quedó en una sola liquidación
        assertThat(resultados).extracting(IntegracionCoberturasBase::status).containsExactly(201, 201);
        assertThat(cantidad("liquidacion")).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT turno_id FROM liquidacion_linea", Long.class)).containsExactly(turno);
        // AND el reintento de la segunda no dejó una liquidación a medias: los totales coinciden con las líneas
        for (long id : jdbc.queryForList("SELECT id FROM liquidacion", Long.class)) {
            assertThat(totalSolicitadoGuardado(id)).isEqualByComparingTo(sumaDeLasLineas(id));
        }
    }
}
