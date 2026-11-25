package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Totales de la conciliación, cierre y efectos sobre el paciente (C-20, spec conciliacion-liquidaciones, tarea 7.2,
 * RN-LQ-06 y RN-LQ-07). La liquidación de "Obra Social Ejemplo" de {@code 2026-10} está {@code PRESENTADA} con la
 * consulta (15000.00) y la limpieza (12500.00) de "Paciente Uno", total solicitado 27500.00. Reloj: 2026-11-24 a las
 * 12:00 en Buenos Aires. Solo datos ficticios.
 */
class TotalesYCierreIntegrationTest extends IntegracionLiquidacionesBase {

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
    }

    private MvcResult cobrar(long linea, String monto, String motivo) throws Exception {
        MvcResult r = conciliar(liquidacion, linea, new BigDecimal(monto), motivo);
        assertThat(status(r)).as("conciliar de preparación: " + texto(r)).isEqualTo(200);
        return r;
    }

    private MvcResult cerrar() throws Exception {
        return post(como("admin"), url(liquidacion) + "/cerrar", null);
    }

    private void assertTotalesGuardados() {
        assertThat(totalSolicitadoGuardado(liquidacion)).isEqualByComparingTo(sumaDeLasLineas(liquidacion));
        assertThat(totalCobradoGuardado(liquidacion)).isEqualByComparingTo(sumaDeLoCobrado(liquidacion));
    }

    // --- Conciliación parcial y totales ---

    @Test
    @DisplayName("Una línea conciliada y otra pendiente")
    void unaLineaConciliadaYOtraPendiente() throws Exception {
        // GIVEN admin registró 15000.00 cobrados en la línea de "Consulta o control"
        cobrar(lineaConsulta, "15000.00", null);

        // WHEN admin consulta la liquidación
        JsonNode detalle = detalle(liquidacion);

        // THEN el total solicitado es 27500.00, el total cobrado 15000.00, la diferencia 0.00 y hay 1 línea pendiente
        assertThat(numero(detalle.path("totalSolicitado"))).isEqualByComparingTo("27500.00");
        assertThat(numero(detalle.path("totalCobrado"))).isEqualByComparingTo("15000.00");
        assertThat(numero(detalle.path("diferencia"))).isEqualByComparingTo("0.00");
        assertThat(detalle.path("lineasPendientes").asInt()).isEqualTo(1);
        assertTotalesGuardados();
    }

    @Test
    @DisplayName("Una línea parcial")
    void unaLineaParcial() throws Exception {
        // GIVEN admin registró 15000.00 en la consulta y 10000.00 en la limpieza
        cobrar(lineaConsulta, "15000.00", null);
        cobrar(lineaLimpieza, "10000.00", null);

        // WHEN admin consulta la liquidación
        JsonNode detalle = detalle(liquidacion);

        // THEN el total cobrado es 25000.00, la diferencia 2500.00 y no hay líneas pendientes
        assertThat(numero(detalle.path("totalCobrado"))).isEqualByComparingTo("25000.00");
        assertThat(numero(detalle.path("diferencia"))).isEqualByComparingTo("2500.00");
        assertThat(detalle.path("lineasPendientes").asInt()).isZero();
        assertThat(numero(detalle.path("solicitadoConciliado"))).isEqualByComparingTo("27500.00");
        assertThat(numero(detalle.path("totalSolicitado"))).isEqualByComparingTo("27500.00");
        assertTotalesGuardados();
    }

    @Test
    @DisplayName("Una línea rechazada")
    void unaLineaRechazada() throws Exception {
        // GIVEN admin registró 15000.00 en la consulta y 0.00 en la limpieza con un motivo
        cobrar(lineaConsulta, "15000.00", null);
        cobrar(lineaLimpieza, "0.00", "Falta la orden de la práctica");

        // WHEN admin consulta la liquidación
        JsonNode detalle = detalle(liquidacion);

        // THEN el total cobrado es 15000.00 y la diferencia 12500.00
        assertThat(numero(detalle.path("totalCobrado"))).isEqualByComparingTo("15000.00");
        assertThat(numero(detalle.path("diferencia"))).isEqualByComparingTo("12500.00");
        assertTotalesGuardados();
    }

    @Test
    @DisplayName("Corregir una línea recalcula el total cobrado")
    void corregirRecalculaElTotal() throws Exception {
        cobrar(lineaConsulta, "15000.00", null);
        cobrar(lineaLimpieza, "10000.00", null);

        cobrar(lineaLimpieza, "12500.00", null);

        assertThat(totalCobradoGuardado(liquidacion)).isEqualByComparingTo("27500.00");
        assertThat(numero(detalle(liquidacion).path("diferencia"))).isEqualByComparingTo("0.00");
        assertTotalesGuardados();
    }

    // --- Cerrar la liquidación conciliada ---

    @Test
    @DisplayName("Cerrar con todo conciliado")
    void cerrarConTodoConciliado() throws Exception {
        // GIVEN las dos líneas tienen resultado (COBRADA y PARCIAL)
        cobrar(lineaConsulta, "15000.00", null);
        cobrar(lineaLimpieza, "10000.00", null);

        // WHEN admin cierra la liquidación
        MvcResult r = cerrar();

        // THEN la respuesta es 200 con estado CERRADA, la fecha de cierre, total cobrado 25000.00 y diferencia 2500.00
        assertThat(status(r)).isEqualTo(200);
        JsonNode cerrada = cuerpo(r);
        assertThat(cerrada.path("estado").asText()).isEqualTo("CERRADA");
        assertThat(cerrada.path("cerradaEn").asText()).startsWith("2026-11-24T15:00");
        assertThat(cerrada.path("cerradaPor").asText()).isEqualTo("admin");
        assertThat(numero(cerrada.path("totalCobrado"))).isEqualByComparingTo("25000.00");
        assertThat(numero(cerrada.path("diferencia"))).isEqualByComparingTo("2500.00");
        assertThat(estadoGuardadoLiquidacion(liquidacion)).isEqualTo("CERRADA");
        assertTotalesGuardados();
    }

    @Test
    @DisplayName("Cerrar con una línea pendiente")
    void cerrarConUnaLineaPendiente() throws Exception {
        // GIVEN solo la línea de "Consulta o control" tiene resultado
        cobrar(lineaConsulta, "15000.00", null);

        // WHEN admin cierra la liquidación
        MvcResult r = cerrar();

        // THEN la respuesta es 409 con el código CONCILIACION_INCOMPLETA y sigue PRESENTADA
        assertThat(status(r)).isEqualTo(409);
        assertThat(codigoDeError(r)).isEqualTo("CONCILIACION_INCOMPLETA");
        assertThat(estadoGuardadoLiquidacion(liquidacion)).isEqualTo("PRESENTADA");
    }

    @Test
    @DisplayName("Cerrar un borrador o una cerrada")
    void cerrarUnBorradorOUnaCerrada() throws Exception {
        // GIVEN una liquidación en BORRADOR y la presentada del test, que se cierra
        atendido(pacienteTres, consulta, "2026-10-07T10:00");
        long borrador = generada(prepaga, "2026-10").path("id").asLong();
        cobrar(lineaConsulta, "15000.00", null);
        cobrar(lineaLimpieza, "12500.00", null);
        assertThat(status(cerrar())).isEqualTo(200);

        // WHEN admin cierra una liquidación en BORRADOR o una ya CERRADA
        MvcResult deBorrador = post(como("admin"), url(borrador) + "/cerrar", null);
        MvcResult otraVez = cerrar();

        // THEN la respuesta es 409 con el código LIQUIDACION_NO_PRESENTADA o LIQUIDACION_CERRADA
        assertThat(status(deBorrador)).isEqualTo(409);
        assertThat(codigoDeError(deBorrador)).isEqualTo("LIQUIDACION_NO_PRESENTADA");
        assertThat(status(otraVez)).isEqualTo(409);
        assertThat(codigoDeError(otraVez)).isEqualTo("LIQUIDACION_CERRADA");
        assertThat(status(post(como("admin"), url(999999L) + "/cerrar", null))).isEqualTo(404);
    }

    @Test
    @DisplayName("La base rechaza cambiar una liquidación cerrada")
    void laBaseRechazaCambiarUnaLiquidacionCerrada() throws Exception {
        // GIVEN la liquidación de 2026-10 está CERRADA
        cobrar(lineaConsulta, "15000.00", null);
        cobrar(lineaLimpieza, "10000.00", null);
        cerrar();

        // WHEN se intenta por SQL cambiar el monto cobrado de una línea o volver la liquidación a PRESENTADA
        // THEN la base rechaza cada operación y los datos no cambian
        assertThatThrownBy(() -> jdbc.update("UPDATE liquidacion_linea SET monto_cobrado = 12500.00, "
                + "resultado = 'COBRADA' WHERE id = ?", lineaLimpieza)).hasMessageContaining("cerrada");
        assertThatThrownBy(() -> jdbc.update("UPDATE liquidacion SET estado = 'PRESENTADA', cerrada_en = NULL, "
                + "cerrada_por = NULL WHERE id = ?", liquidacion)).hasMessageContaining("cerrada");
        assertThat(jdbc.queryForObject("SELECT monto_cobrado FROM liquidacion_linea WHERE id = ?", BigDecimal.class,
                lineaLimpieza)).isEqualByComparingTo("10000.00");
        assertThat(estadoGuardadoLiquidacion(liquidacion)).isEqualTo("CERRADA");
        assertTotalesGuardados();
    }

    // --- Lo no cobrado no se le cobra al paciente ---

    @Test
    @DisplayName("Línea rechazada sin efectos sobre el paciente")
    void lineaRechazadaSinEfectosSobreElPaciente() throws Exception {
        // GIVEN no hay notificaciones ni otros datos del paciente antes de conciliar
        int notificaciones = cantidad("notificacion");
        int presupuestos = cantidad("presupuesto");
        int turnos = cantidad("turno");

        // WHEN admin registra 0.00 cobrados en la línea de "Limpieza (profilaxis)" con un motivo
        MvcResult r = cobrar(lineaLimpieza, "0.00", "Falta la orden de la práctica");
        JsonNode cerrada = cuerpo(r);

        // THEN no se crea ninguna notificación para "Paciente Uno" y la liquidación informa la diferencia de 12500.00
        assertThat(cantidad("notificacion")).isEqualTo(notificaciones);
        assertThat(numero(cerrada.path("diferencia"))).isEqualByComparingTo("12500.00");
        // AND tampoco se crearon cargos, presupuestos ni cambios en los turnos
        assertThat(cantidad("presupuesto")).isEqualTo(presupuestos);
        assertThat(cantidad("turno")).isEqualTo(turnos);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE estado <> 'ATENDIDO'", Integer.class)).isZero();
        cobrar(lineaConsulta, "15000.00", null);
        assertThat(status(cerrar())).isEqualTo(200);
        assertThat(cantidad("notificacion")).isEqualTo(notificaciones);
    }
}
