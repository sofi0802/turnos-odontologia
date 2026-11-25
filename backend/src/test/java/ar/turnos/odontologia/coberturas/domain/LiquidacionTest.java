package ar.turnos.odontologia.coberturas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnos.odontologia.coberturas.domain.Liquidacion.Hitos;
import ar.turnos.odontologia.coberturas.domain.Liquidacion.Totales;
import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion.Candidato;
import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion.CodigoCubierto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Agregado {@link Liquidacion} (C-20, tarea 2.5, design D4 y D8): presentar, conciliar, cerrar y totales. Solo datos
 * ficticios: la liquidación de "Obra Social Ejemplo" de {@code 2026-10} con la consulta (15000.00) y la limpieza
 * (12500.00) de "Paciente Uno".
 */
class LiquidacionTest {

    private static final OffsetDateTime AHORA = OffsetDateTime.of(2026, 11, 24, 12, 0, 0, 0, ZoneOffset.ofHours(-3));
    private static final long CONSULTA = 101L;
    private static final long LIMPIEZA = 102L;

    private static LineaLiquidacion linea(long id, long turno, String prestacion, String precio, int porcentaje) {
        Candidato c = new Candidato(turno, 20L, 30L, 40L, LocalDate.of(2026, 10, 6), "Paciente Uno", "99000001",
                "OSE-000001", "Plan Base", prestacion, new BigDecimal(precio),
                List.of(new CodigoCubierto(1L, "N-0101", porcentaje)));
        return LineaLiquidacion.desde(c, null, null).linea().conId(id);
    }

    private static LineaLiquidacion lineaSinCodigo(long id, long turno) {
        Candidato c = new Candidato(turno, 20L, 30L, 40L, LocalDate.of(2026, 10, 6), "Paciente Uno", "99000001",
                "OSE-000001", "Plan Base", "Consulta o control", new BigDecimal("15000.00"),
                List.of(new CodigoCubierto(1L, "N-0101", 100), new CodigoCubierto(2L, "N-0102", 80)));
        return LineaLiquidacion.desde(c, null, null).linea().conId(id);
    }

    private static Liquidacion borrador(LineaLiquidacion... lineas) {
        return new Liquidacion(7L, 1L, Periodo.de("2026-10"), EstadoLiquidacion.BORRADOR, List.of(lineas),
                Hitos.generada(AHORA, "admin"));
    }

    private static Liquidacion borradorCompleto() {
        return borrador(linea(CONSULTA, 1L, "Consulta o control", "15000.00", 100),
                linea(LIMPIEZA, 2L, "Limpieza (profilaxis)", "25000.00", 50));
    }

    private static Liquidacion presentadaCompleta() {
        return borradorCompleto().presentar(AHORA, "admin");
    }

    private static void assertCodigo(Runnable accion, CodigoCobertura codigo) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(CoberturaException.class,
                e -> assertThat(e.codigo()).isEqualTo(codigo));
    }

    @Nested
    class Presentar {

        @Test
        @DisplayName("Presentar una liquidación completa")
        void completa() {
            Liquidacion presentada = borradorCompleto().presentar(AHORA, "admin");

            assertThat(presentada.estado()).isEqualTo(EstadoLiquidacion.PRESENTADA);
            assertThat(presentada.hitos().presentadaEn()).isEqualTo(AHORA);
            assertThat(presentada.hitos().presentadaPor()).isEqualTo("admin");
            assertThat(presentada.hitos().generadaPor()).isEqualTo("admin");
            assertThat(presentada.lineas()).hasSize(2);
            assertThat(presentada.totales().totalSolicitado()).isEqualTo(new BigDecimal("27500.00"));
        }

        @Test
        @DisplayName("Presentar una liquidación vacía")
        void vacia() {
            assertCodigo(() -> borrador().presentar(AHORA, "admin"), CodigoCobertura.LIQUIDACION_VACIA);
        }

        @Test
        @DisplayName("Presentar con una línea sin código")
        void sinCodigo() {
            Liquidacion l = borrador(linea(CONSULTA, 1L, "Consulta o control", "15000.00", 100), lineaSinCodigo(103L, 3L));

            assertCodigo(() -> l.presentar(AHORA, "admin"), CodigoCobertura.LINEA_SIN_CODIGO);
        }

        @Test
        @DisplayName("Presentar dos veces o una cerrada")
        void dosVeces() {
            assertCodigo(() -> presentadaCompleta().presentar(AHORA, "admin"), CodigoCobertura.LIQUIDACION_PRESENTADA);
            assertCodigo(() -> cerradaCompleta().presentar(AHORA, "admin"), CodigoCobertura.LIQUIDACION_CERRADA);
        }

        @Test
        @DisplayName("El estado se verifica antes que la vacía")
        void primeroElEstado() {
            Liquidacion vaciaPresentada = new Liquidacion(7L, 1L, Periodo.de("2026-10"), EstadoLiquidacion.PRESENTADA,
                    List.of(), Hitos.generada(AHORA, "admin"));

            assertCodigo(() -> vaciaPresentada.presentar(AHORA, "admin"), CodigoCobertura.LIQUIDACION_PRESENTADA);
        }
    }

    @Nested
    class Conciliar {

        @Test
        @DisplayName("Conciliar una línea cobrada completa")
        void cobrada() {
            Liquidacion l = presentadaCompleta().conciliar(CONSULTA, new BigDecimal("15000.00"), null, AHORA, "admin");

            LineaLiquidacion consulta = l.linea(CONSULTA);
            assertThat(consulta.resultado()).isEqualTo(ResultadoConciliacion.COBRADA);
            assertThat(consulta.montoCobrado()).isEqualTo(new BigDecimal("15000.00"));
            assertThat(consulta.conciliadaPor()).isEqualTo("admin");
            assertThat(consulta.conciliadaEn()).isEqualTo(AHORA);
            assertThat(consulta.montoSolicitado()).isEqualTo(new BigDecimal("15000.00"));
            assertThat(l.linea(LIMPIEZA).resultado()).isNull();
        }

        @Test
        @DisplayName("Conciliar una línea rechazada con motivo")
        void rechazada() {
            Liquidacion l = presentadaCompleta().conciliar(LIMPIEZA, new BigDecimal("0.00"),
                    "Falta la orden de la práctica", AHORA, "admin");

            assertThat(l.linea(LIMPIEZA).resultado()).isEqualTo(ResultadoConciliacion.RECHAZADA);
            assertThat(l.linea(LIMPIEZA).motivoRechazo()).isEqualTo("Falta la orden de la práctica");
        }

        @Test
        @DisplayName("Conciliar un borrador")
        void borrador() {
            assertCodigo(() -> borradorCompleto().conciliar(CONSULTA, new BigDecimal("15000.00"), null, AHORA, "admin"),
                    CodigoCobertura.LIQUIDACION_NO_PRESENTADA);
        }

        @Test
        @DisplayName("Conciliar una liquidación cerrada")
        void cerrada() {
            assertCodigo(() -> cerradaCompleta().conciliar(CONSULTA, new BigDecimal("15000.00"), null, AHORA, "admin"),
                    CodigoCobertura.LIQUIDACION_CERRADA);
        }

        @Test
        @DisplayName("Conciliar una línea que no es de la liquidación")
        void lineaAjena() {
            assertCodigo(() -> presentadaCompleta().conciliar(999L, new BigDecimal("1.00"), null, AHORA, "admin"),
                    CodigoCobertura.RECURSO_NO_ENCONTRADO);
        }

        @Test
        @DisplayName("Conciliar con un monto mayor que el solicitado")
        void montoMayor() {
            assertCodigo(() -> presentadaCompleta().conciliar(CONSULTA, new BigDecimal("15000.01"), null, AHORA, "admin"),
                    CodigoCobertura.MONTO_COBRADO_INVALIDO);
        }

        @Test
        @DisplayName("Corregir una línea conciliada mientras la liquidación sigue presentada")
        void correccion() {
            Liquidacion l = presentadaCompleta()
                    .conciliar(LIMPIEZA, new BigDecimal("10000.00"), null, AHORA, "admin")
                    .conciliar(LIMPIEZA, new BigDecimal("12500.00"), null, AHORA.plusMinutes(1), "admin");

            assertThat(l.linea(LIMPIEZA).resultado()).isEqualTo(ResultadoConciliacion.COBRADA);
            assertThat(l.linea(LIMPIEZA).montoCobrado()).isEqualTo(new BigDecimal("12500.00"));
            assertThat(l.linea(LIMPIEZA).conciliadaEn()).isEqualTo(AHORA.plusMinutes(1));
        }

        @Test
        @DisplayName("Corregir un rechazo a cobrada borra el motivo")
        void correccionBorraElMotivo() {
            Liquidacion l = presentadaCompleta()
                    .conciliar(LIMPIEZA, BigDecimal.ZERO, "Falta la orden", AHORA, "admin")
                    .conciliar(LIMPIEZA, new BigDecimal("12500.00"), null, AHORA, "admin");

            assertThat(l.linea(LIMPIEZA).motivoRechazo()).isNull();
        }
    }

    @Nested
    class Cerrar {

        @Test
        @DisplayName("Cerrar con todo conciliado")
        void completa() {
            Liquidacion cerrada = cerradaCompleta();

            assertThat(cerrada.estado()).isEqualTo(EstadoLiquidacion.CERRADA);
            assertThat(cerrada.hitos().cerradaEn()).isEqualTo(AHORA);
            assertThat(cerrada.hitos().cerradaPor()).isEqualTo("admin");
            assertThat(cerrada.totales().totalCobrado()).isEqualTo(new BigDecimal("25000.00"));
            assertThat(cerrada.totales().diferencia()).isEqualTo(new BigDecimal("2500.00"));
        }

        @Test
        @DisplayName("Cerrar con una línea pendiente")
        void incompleta() {
            Liquidacion l = presentadaCompleta().conciliar(CONSULTA, new BigDecimal("15000.00"), null, AHORA, "admin");

            assertCodigo(() -> l.cerrar(AHORA, "admin"), CodigoCobertura.CONCILIACION_INCOMPLETA);
        }

        @Test
        @DisplayName("Cerrar un borrador o una cerrada")
        void estadoIncorrecto() {
            assertCodigo(() -> borradorCompleto().cerrar(AHORA, "admin"), CodigoCobertura.LIQUIDACION_NO_PRESENTADA);
            assertCodigo(() -> cerradaCompleta().cerrar(AHORA, "admin"), CodigoCobertura.LIQUIDACION_CERRADA);
        }
    }

    @Nested
    class EnBorrador {

        @Test
        @DisplayName("Quitar una línea de un borrador")
        void quitar() {
            Liquidacion l = borradorCompleto().sinLinea(LIMPIEZA);

            assertThat(l.lineas()).hasSize(1);
            assertThat(l.totales().totalSolicitado()).isEqualTo(new BigDecimal("15000.00"));
        }

        @Test
        @DisplayName("Quitar una línea de una presentada, de una cerrada o inexistente")
        void quitarMal() {
            assertCodigo(() -> presentadaCompleta().sinLinea(LIMPIEZA), CodigoCobertura.LIQUIDACION_PRESENTADA);
            assertCodigo(() -> cerradaCompleta().sinLinea(LIMPIEZA), CodigoCobertura.LIQUIDACION_CERRADA);
            assertCodigo(() -> borradorCompleto().sinLinea(999L), CodigoCobertura.RECURSO_NO_ENCONTRADO);
        }
    }

    @Nested
    class TotalesDelDetalle {

        @Test
        @DisplayName("Sin conciliar nada hay dos líneas pendientes")
        void sinConciliar() {
            Totales t = presentadaCompleta().totales();

            assertThat(t.totalSolicitado()).isEqualTo(new BigDecimal("27500.00"));
            assertThat(t.totalCobrado()).isEqualTo(new BigDecimal("0.00"));
            assertThat(t.solicitadoConciliado()).isEqualTo(new BigDecimal("0.00"));
            assertThat(t.diferencia()).isEqualTo(new BigDecimal("0.00"));
            assertThat(t.lineasPendientes()).isEqualTo(2);
        }

        @Test
        @DisplayName("Una línea conciliada y otra pendiente")
        void unaConciliada() {
            Totales t = presentadaCompleta()
                    .conciliar(CONSULTA, new BigDecimal("15000.00"), null, AHORA, "admin").totales();

            assertThat(t.totalSolicitado()).isEqualTo(new BigDecimal("27500.00"));
            assertThat(t.totalCobrado()).isEqualTo(new BigDecimal("15000.00"));
            assertThat(t.diferencia()).isEqualTo(new BigDecimal("0.00"));
            assertThat(t.lineasPendientes()).isEqualTo(1);
        }

        @Test
        @DisplayName("Una línea parcial")
        void parcial() {
            Totales t = cerradaCompleta().totales();

            assertThat(t.totalSolicitado()).isEqualTo(new BigDecimal("27500.00"));
            assertThat(t.totalCobrado()).isEqualTo(new BigDecimal("25000.00"));
            assertThat(t.solicitadoConciliado()).isEqualTo(new BigDecimal("27500.00"));
            assertThat(t.diferencia()).isEqualTo(new BigDecimal("2500.00"));
            assertThat(t.lineasPendientes()).isZero();
        }

        @Test
        @DisplayName("Una línea rechazada")
        void rechazada() {
            Totales t = presentadaCompleta()
                    .conciliar(CONSULTA, new BigDecimal("15000.00"), null, AHORA, "admin")
                    .conciliar(LIMPIEZA, BigDecimal.ZERO, "Motivo ficticio", AHORA, "admin").totales();

            assertThat(t.totalCobrado()).isEqualTo(new BigDecimal("15000.00"));
            assertThat(t.diferencia()).isEqualTo(new BigDecimal("12500.00"));
        }

        @Test
        @DisplayName("El total solicitado no suma las líneas sin código")
        void sinCodigoNoSuma() {
            Liquidacion l = borrador(linea(CONSULTA, 1L, "Consulta o control", "15000.00", 100), lineaSinCodigo(103L, 3L));

            assertThat(l.totales().totalSolicitado()).isEqualTo(new BigDecimal("15000.00"));
        }

        @Test
        @DisplayName("Conciliar no cambia los montos solicitados")
        void losSolicitadosNoCambian() {
            Liquidacion l = presentadaCompleta().conciliar(LIMPIEZA, new BigDecimal("10000.00"), null, AHORA, "admin");

            assertThat(l.linea(LIMPIEZA).montoSolicitado()).isEqualTo(new BigDecimal("12500.00"));
            assertThat(l.totales().totalSolicitado()).isEqualTo(new BigDecimal("27500.00"));
        }
    }

    private static Liquidacion cerradaCompleta() {
        return presentadaCompleta()
                .conciliar(CONSULTA, new BigDecimal("15000.00"), null, AHORA, "admin")
                .conciliar(LIMPIEZA, new BigDecimal("10000.00"), null, AHORA, "admin")
                .cerrar(AHORA, "admin");
    }
}
