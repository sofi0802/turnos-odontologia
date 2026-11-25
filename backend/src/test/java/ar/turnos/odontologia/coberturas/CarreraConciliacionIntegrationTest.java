package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import ar.turnos.odontologia.coberturas.application.Liquidaciones;
import ar.turnos.odontologia.shared.security.Navegador;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Conciliaciones simultáneas (C-20, spec conciliacion-liquidaciones, tarea 7.3, design D4): toda operación sobre una
 * liquidación empieza con el bloqueo de su cabecera, así se aplican de a una. Un espía demora cada lectura bloqueada
 * después de tomar el bloqueo: sin el bloqueo, las dos operaciones leerían el mismo estado inicial. La liquidación de
 * "Obra Social Ejemplo" de {@code 2026-10} está {@code PRESENTADA} con la consulta (15000.00) y la limpieza (12500.00)
 * de "Paciente Uno". Reloj: 2026-11-24 a las 12:00 en Buenos Aires. Solo datos ficticios.
 */
class CarreraConciliacionIntegrationTest extends IntegracionLiquidacionesBase {

    @MockitoSpyBean
    private Liquidaciones liquidaciones;

    private long liquidacion;
    private long lineaConsulta;
    private long lineaLimpieza;

    @BeforeEach
    void liquidacionPresentada() throws Exception {
        semilla();
        atendido(pacienteUno, consulta, "2026-10-06T10:00");
        atendido(pacienteUno, limpieza, "2026-10-20T10:00");
        JsonNode generada = generada(obraSocialEjemplo, "2026-10");
        liquidacion = generada.path("id").asLong();
        lineaConsulta = lineaDe(generada, "Consulta o control").path("id").asLong();
        lineaLimpieza = lineaDe(generada, "Limpieza (profilaxis)").path("id").asLong();
        presentar(liquidacion);
        doAnswer(invocacion -> {
            Object leida = invocacion.callRealMethod();
            Thread.sleep(150);
            return leida;
        }).when(liquidaciones).bloquear(anyLong());
    }

    private String codigo(MvcResult r) {
        try {
            return codigoDeError(r);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @RepeatedTest(5)
    @DisplayName("Cerrar mientras se concilia la última línea")
    void cerrarMientrasSeConciliaLaUltimaLinea() throws Exception {
        // GIVEN solo la línea de "Consulta o control" tiene resultado
        assertThat(status(conciliar(liquidacion, lineaConsulta, new BigDecimal("15000.00"), null))).isEqualTo(200);
        Navegador paraConciliar = como("admin");
        Navegador paraCerrar = como("admin");

        // WHEN al mismo tiempo admin registra 12500.00 en la limpieza y cierra la liquidación
        List<MvcResult> resultados = enParalelo(
                () -> put(paraConciliar, urlLinea(liquidacion, lineaLimpieza) + "/conciliacion",
                        conciliacion(new BigDecimal("12500.00"), null)),
                () -> post(paraCerrar, url(liquidacion) + "/cerrar", null));

        // THEN o la conciliación se aplica primero y el cierre responde 200, o el cierre responde 409 CONCILIACION_INCOMPLETA y la conciliación se aplica
        MvcResult conciliacion = resultados.get(0);
        MvcResult cierre = resultados.get(1);
        assertThat(status(conciliacion)).as("la conciliación siempre se aplica: " + texto(conciliacion)).isEqualTo(200);
        if (status(cierre) == 200) {
            assertThat(estadoGuardadoLiquidacion(liquidacion)).isEqualTo("CERRADA");
        } else {
            assertThat(status(cierre)).isEqualTo(409);
            assertThat(codigo(cierre)).isEqualTo("CONCILIACION_INCOMPLETA");
            assertThat(estadoGuardadoLiquidacion(liquidacion)).isEqualTo("PRESENTADA");
        }
        // AND nunca queda CERRADA con una línea pendiente, y el total cobrado coincide con las líneas
        assertThat(jdbc.queryForObject("SELECT count(*) FROM liquidacion_linea WHERE liquidacion_id = ? "
                + "AND resultado IS NULL", Integer.class, liquidacion)).isZero();
        assertThat(totalCobradoGuardado(liquidacion)).isEqualByComparingTo("27500.00")
                .isEqualByComparingTo(sumaDeLoCobrado(liquidacion));
    }

    @RepeatedTest(5)
    @DisplayName("Dos conciliaciones a la vez de líneas distintas")
    void dosConciliacionesALaVezDeLineasDistintas() throws Exception {
        // GIVEN las dos líneas sin conciliar
        Navegador uno = como("admin");
        Navegador otro = como("admin");

        // WHEN al mismo tiempo admin registra 15000.00 en la consulta y 10000.00 en la limpieza
        List<MvcResult> resultados = enParalelo(
                () -> put(uno, urlLinea(liquidacion, lineaConsulta) + "/conciliacion",
                        conciliacion(new BigDecimal("15000.00"), null)),
                () -> put(otro, urlLinea(liquidacion, lineaLimpieza) + "/conciliacion",
                        conciliacion(new BigDecimal("10000.00"), null)));

        // THEN las dos responden 200 y el total cobrado es la suma de las dos (sin perder una de las actualizaciones)
        assertThat(resultados).extracting(IntegracionCoberturasBase::status).containsExactly(200, 200);
        assertThat(totalCobradoGuardado(liquidacion)).isEqualByComparingTo("25000.00");
        assertThat(sumaDeLoCobrado(liquidacion)).isEqualByComparingTo("25000.00");
        assertThat(numero(detalle(liquidacion).path("diferencia"))).isEqualByComparingTo("2500.00");
    }
}
