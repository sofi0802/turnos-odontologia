package ar.turnos.odontologia.reportes.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Cálculo de los reportes (spec {@code reportes}), sin base de datos. Datos ficticios: la Dra. Ana Ejemplo (1) con el
 * Sillón 1 como habitual atiende de lunes a viernes de 09:00 a 13:00; el Dr. Bruno Prueba (2) con el Sillón 2 atiende
 * los lunes de 09:00 a 10:00; el Sillón 3 no es habitual de nadie. Semana del lunes 2026-12-07 al domingo 2026-12-13.
 */
class CalculoReportesTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Periodo SEMANA = Periodo.de(LocalDate.of(2026, 12, 7), LocalDate.of(2026, 12, 13));
    private static final Instant AHORA = instante("2026-12-14", "12:00");
    private static final String ONLINE = "paciente-online:2";

    private final CalculoReportes calculo = new CalculoReportes(ZONA);
    private final List<TurnoLeido> turnos = new ArrayList<>();
    private final List<AusenciaLeida> ausencias = new ArrayList<>();
    private final List<BloqueoLeido> bloqueos = new ArrayList<>();
    private final List<ProfesionalLeido> profesionales = new ArrayList<>(List.of(
            new ProfesionalLeido(1L, "Ana", "Ejemplo", true, 1L),
            new ProfesionalLeido(2L, "Bruno", "Prueba", true, 2L)));
    private final List<HorarioLeido> horarios = new ArrayList<>(List.of(
            new HorarioLeido(1L, 1, LocalTime.of(9, 0), LocalTime.of(13, 0)),
            new HorarioLeido(1L, 2, LocalTime.of(9, 0), LocalTime.of(13, 0)),
            new HorarioLeido(1L, 3, LocalTime.of(9, 0), LocalTime.of(13, 0)),
            new HorarioLeido(1L, 4, LocalTime.of(9, 0), LocalTime.of(13, 0)),
            new HorarioLeido(1L, 5, LocalTime.of(9, 0), LocalTime.of(13, 0)),
            new HorarioLeido(2L, 1, LocalTime.of(9, 0), LocalTime.of(10, 0))));
    private final List<SillonLeido> sillones = List.of(
            new SillonLeido(1L, "Sillón 1", true), new SillonLeido(2L, "Sillón 2", true), new SillonLeido(3L, "Sillón 3", true));
    private long proximoId = 1;

    private static Instant instante(String fecha, String hora) {
        return LocalDateTime.of(LocalDate.parse(fecha), LocalTime.parse(hora)).atZone(ZONA).toInstant();
    }

    private TurnoLeido turno(long profesional, long sillon, String fecha, String desde, String hasta, String estado) {
        return turno(profesional, sillon, fecha, desde, hasta, estado, "recepcion", false, null);
    }

    private TurnoLeido turno(long profesional, long sillon, String fecha, String desde, String hasta, String estado,
                             String actorCreacion, boolean reprogramado, String actorCancelacion) {
        TurnoLeido nuevo = new TurnoLeido(proximoId++, profesional, sillon, instante(fecha, desde), instante(fecha, hasta),
                estado, actorCreacion, reprogramado, actorCancelacion);
        turnos.add(nuevo);
        return nuevo;
    }

    private Reporte calcular() {
        return calcular(SEMANA, null);
    }

    private Reporte calcular(Periodo periodo, Long profesionalId) {
        return calculo.calcular(periodo, profesionalId,
                new DatosReporte(profesionales, sillones, horarios, turnos, ausencias, bloqueos), AHORA);
    }

    private static FilaOcupacion fila(List<FilaOcupacion> filas, long id) {
        return filas.stream().filter(f -> f.id() == id).findFirst().orElseThrow();
    }

    @Nested
    class OcupacionPorProfesional {

        @Test
        @DisplayName("Ocupación de una semana")
        void ocupacionDeUnaSemana() {
            turno(1, 1, "2026-12-07", "09:00", "09:30", "ATENDIDO");
            turno(1, 1, "2026-12-08", "09:00", "09:30", "RESERVADO");
            turno(1, 1, "2026-12-09", "09:00", "09:30", "AUSENTE");
            turno(1, 1, "2026-12-10", "09:00", "09:30", "CANCELADO");

            FilaOcupacion ana = fila(calcular().ocupacion().profesionales(), 1);

            assertThat(ana.minutosOcupados()).isEqualTo(90);
            assertThat(ana.minutosAtencion()).isEqualTo(1200);
            assertThat(ana.porcentaje()).isEqualTo(8);
            assertThat(ana.nombre()).isEqualTo("Ana Ejemplo");
        }

        @Test
        @DisplayName("Las ausencias y los bloqueos descuentan atención")
        void ausenciasYBloqueosDescuentan() {
            ausencias.add(new AusenciaLeida(1L, "LICENCIA", instante("2026-12-09", "00:00"), instante("2026-12-10", "00:00")));
            bloqueos.add(new BloqueoLeido(1L, instante("2026-12-10", "10:00"), instante("2026-12-10", "11:00")));

            assertThat(fila(calcular().ocupacion().profesionales(), 1).minutosAtencion()).isEqualTo(900);
        }

        @Test
        @DisplayName("Una ausencia y un bloqueo superpuestos no se descuentan dos veces")
        void superpuestosUnaSolaVez() {
            ausencias.add(new AusenciaLeida(1L, "AUSENCIA", instante("2026-12-10", "09:00"), instante("2026-12-10", "11:00")));
            bloqueos.add(new BloqueoLeido(1L, instante("2026-12-10", "10:00"), instante("2026-12-10", "12:00")));

            assertThat(fila(calcular().ocupacion().profesionales(), 1).minutosAtencion()).isEqualTo(1020);
        }

        @Test
        @DisplayName("Un profesional inactivo con turnos en el período aparece; sin turnos, no")
        void inactivos() {
            profesionales.add(new ProfesionalLeido(4L, "Carla", "Inactiva", false, null));
            profesionales.add(new ProfesionalLeido(5L, "Diego", "Retirado", false, null));
            turno(4, 3, "2026-12-08", "09:00", "09:30", "ATENDIDO");

            assertThat(calcular().ocupacion().profesionales()).extracting(FilaOcupacion::id).containsExactly(1L, 4L, 2L);
        }
    }

    @Nested
    class PorcentajeDeOcupacion {

        @Test
        @DisplayName("Los sobreturnos pueden superar el 100 %")
        void sobreturnosSuperanElCien() {
            turno(2, 2, "2026-12-07", "09:00", "09:30", "RESERVADO");
            turno(2, 2, "2026-12-07", "09:30", "10:00", "RESERVADO");
            turno(2, 3, "2026-12-07", "09:15", "09:45", "RESERVADO", "admin", false, null);

            FilaOcupacion bruno = fila(calcular(Periodo.de(LocalDate.of(2026, 12, 7), LocalDate.of(2026, 12, 7)), null)
                    .ocupacion().profesionales(), 2);

            assertThat(bruno.minutosOcupados()).isEqualTo(90);
            assertThat(bruno.minutosAtencion()).isEqualTo(60);
            assertThat(bruno.porcentaje()).isEqualTo(150);
        }

        @Test
        @DisplayName("Profesional sin horario")
        void profesionalSinHorario() {
            profesionales.add(new ProfesionalLeido(3L, "Carla", "Muestra", true, null));

            FilaOcupacion carla = fila(calcular().ocupacion().profesionales(), 3);

            assertThat(carla.minutosAtencion()).isZero();
            assertThat(carla.porcentaje()).isNull();
        }

        @Test
        @DisplayName("El porcentaje redondea hacia arriba desde 0,5")
        void redondeo() {
            assertThat(CalculoReportes.porcentaje(1, 8)).isEqualTo(13);
            assertThat(CalculoReportes.porcentaje(1, 3)).isEqualTo(33);
            assertThat(CalculoReportes.porcentaje(5, 0)).isNull();
        }
    }

    @Nested
    class OcupacionPorSillon {

        @Test
        @DisplayName("Sillón habitual")
        void sillonHabitual() {
            turno(1, 1, "2026-12-07", "09:00", "10:00", "ATENDIDO");
            turno(2, 1, "2026-12-08", "11:00", "12:00", "RESERVADO");

            FilaOcupacion sillon1 = fila(calcular().ocupacion().sillones(), 1);

            assertThat(sillon1.minutosOcupados()).isEqualTo(120);
            assertThat(sillon1.minutosAtencion()).isEqualTo(1200);
            assertThat(sillon1.porcentaje()).isEqualTo(10);
        }

        @Test
        @DisplayName("Sillón que no es habitual de nadie")
        void sillonQueNoEsHabitual() {
            turno(1, 3, "2026-12-07", "09:00", "09:30", "ATENDIDO");

            FilaOcupacion sillon3 = fila(calcular().ocupacion().sillones(), 3);

            assertThat(sillon3.minutosOcupados()).isEqualTo(30);
            assertThat(sillon3.minutosAtencion()).isZero();
            assertThat(sillon3.porcentaje()).isNull();
        }

        @Test
        @DisplayName("Sin sillones con filtro de profesional")
        void sinSillonesConFiltro() {
            turno(1, 1, "2026-12-07", "09:00", "09:30", "ATENDIDO");

            Reporte reporte = calcular(SEMANA, 1L);

            assertThat(reporte.ocupacion().sillones()).isEmpty();
            assertThat(reporte.ocupacion().profesionales()).extracting(FilaOcupacion::id).containsExactly(1L);
        }
    }

    @Nested
    class OrigenDeLosTurnos {

        @Test
        @DisplayName("Turnos online y del consultorio")
        void onlineYConsultorio() {
            for (int i = 0; i < 3; i++) {
                turno(1, 1, "2026-12-08", "09:00", "09:30", "RESERVADO", ONLINE, false, null);
            }
            turno(1, 1, "2026-12-09", "09:00", "09:30", "RESERVADO", "recepcion", false, null);
            turno(1, 1, "2026-12-09", "10:00", "10:30", "RESERVADO", "recepcion", false, null);
            turno(1, 2, "2026-12-09", "10:15", "10:45", "RESERVADO", "admin", false, null);

            Origen origen = calcular().origen();

            assertThat(origen).isEqualTo(new Origen(3, 3, 0));
        }

        @Test
        @DisplayName("Turno sin registro de creación")
        void sinRegistroDeCreacion() {
            turno(1, 1, "2026-12-08", "09:00", "09:30", "RESERVADO", null, false, null);

            assertThat(calcular().origen()).isEqualTo(new Origen(0, 0, 1));
        }

        @Test
        @DisplayName("Una reprogramación no cuenta dos veces")
        void reprogramacionNoCuentaDosVeces() {
            turno(1, 1, "2026-12-08", "09:00", "09:30", "CANCELADO", ONLINE, true, null);
            turno(1, 1, "2026-12-10", "09:00", "09:30", "RESERVADO", "recepcion", false, null);

            assertThat(calcular().origen()).isEqualTo(new Origen(0, 1, 0));
        }

        @Test
        @DisplayName("El cálculo deja fuera los turnos que empiezan fuera del período")
        void cuentaPorSuInicio() {
            turno(1, 1, "2026-12-13", "23:30", "23:59", "RESERVADO");
            turno(1, 1, "2026-12-14", "00:10", "00:40", "RESERVADO");
            turno(1, 1, "2026-12-06", "23:30", "23:59", "RESERVADO");

            assertThat(calcular().origen().consultorio()).isEqualTo(1);
        }
    }

    @Nested
    class AusenciasDePacientes {

        @Test
        @DisplayName("Tasa de ausentismo")
        void tasaDeAusentismo() {
            turno(1, 1, "2026-12-07", "09:00", "09:30", "AUSENTE");
            turno(1, 1, "2026-12-07", "10:00", "10:30", "AUSENTE");
            for (int i = 0; i < 6; i++) {
                turno(1, 1, "2026-12-08", "09:00", "09:30", "ATENDIDO");
            }
            turno(1, 1, "2026-12-09", "09:00", "09:30", "RESERVADO");
            turno(1, 1, "2026-12-13", "13:00", "13:30", "CONFIRMADO");

            AusenciasPacientes pacientes = calculo.calcular(SEMANA, null,
                    new DatosReporte(profesionales, sillones, horarios, turnos, ausencias, bloqueos),
                    instante("2026-12-10", "12:00")).ausencias().pacientes();

            assertThat(pacientes).isEqualTo(new AusenciasPacientes(2, 6, 25, 1));
        }

        @Test
        @DisplayName("Sin turnos atendidos ni ausentes")
        void sinAtendidosNiAusentes() {
            turno(1, 1, "2026-12-08", "09:00", "09:30", "CANCELADO");

            assertThat(calcular().ausencias().pacientes().tasa()).isNull();
        }
    }

    @Nested
    class AusenciasDeProfesionales {

        @Test
        @DisplayName("Licencia que empieza antes del período")
        void licenciaQueEmpiezaAntes() {
            ausencias.add(new AusenciaLeida(2L, "LICENCIA", instante("2026-12-04", "00:00"), instante("2026-12-08", "00:00")));

            assertThat(calcular().ausencias().profesionales())
                    .containsExactly(new AusenciaProfesional(2L, "Bruno Prueba", "LICENCIA", 1, 1440));
        }

        @Test
        @DisplayName("Se agrupan por profesional y por tipo, y quedan fuera las que no tocan el período")
        void agrupadas() {
            ausencias.add(new AusenciaLeida(1L, "AUSENCIA", instante("2026-12-08", "09:00"), instante("2026-12-08", "11:00")));
            ausencias.add(new AusenciaLeida(1L, "AUSENCIA", instante("2026-12-10", "09:00"), instante("2026-12-10", "10:00")));
            ausencias.add(new AusenciaLeida(1L, "LICENCIA", instante("2026-12-11", "00:00"), instante("2026-12-12", "00:00")));
            ausencias.add(new AusenciaLeida(2L, "AUSENCIA", instante("2026-12-20", "09:00"), instante("2026-12-20", "10:00")));

            assertThat(calcular().ausencias().profesionales()).containsExactly(
                    new AusenciaProfesional(1L, "Ana Ejemplo", "AUSENCIA", 2, 180),
                    new AusenciaProfesional(1L, "Ana Ejemplo", "LICENCIA", 1, 1440));
        }
    }

    @Nested
    class CancelacionesPorQuien {

        @Test
        @DisplayName("Cancelaciones por quién canceló")
        void cancelacionesPorQuienCancelo() {
            turno(1, 1, "2026-12-08", "09:00", "09:30", "CANCELADO", ONLINE, false, ONLINE);
            turno(1, 1, "2026-12-08", "10:00", "10:30", "CANCELADO", ONLINE, false, "paciente-online:7");
            turno(1, 1, "2026-12-09", "09:00", "09:30", "CANCELADO", "recepcion", false, "recepcion");
            turno(1, 1, "2026-12-10", "09:00", "09:30", "CANCELADO", "recepcion", true, null);

            assertThat(calcular().cancelaciones()).isEqualTo(new Cancelaciones(2, 1, 1, 0));
        }

        @Test
        @DisplayName("Una cancelación sin registro cuenta como sin dato")
        void cancelacionSinRegistro() {
            turno(1, 1, "2026-12-08", "09:00", "09:30", "CANCELADO", null, false, null);

            assertThat(calcular().cancelaciones()).isEqualTo(new Cancelaciones(0, 0, 0, 1));
        }
    }
}
