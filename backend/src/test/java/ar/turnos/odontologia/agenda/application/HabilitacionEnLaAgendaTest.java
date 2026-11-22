package ar.turnos.odontologia.agenda.application;

import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.BUENOS_AIRES;
import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.CONSULTA;
import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.DNI_PACIENTE_UNO;
import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.DRA_A;
import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.DR_B;
import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.PACIENTE_UNO;
import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.SILLON_1;
import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.SILLON_2;
import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.a;
import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.relojALas;
import static ar.turnos.odontologia.agenda.domain.reglas.PeriodoSinAtencion.Tipo.AUSENCIA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnos.odontologia.agenda.domain.CodigoRechazo;
import ar.turnos.odontologia.agenda.domain.EstadoTurno;
import ar.turnos.odontologia.agenda.domain.Intervalo;
import ar.turnos.odontologia.agenda.domain.Margen;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.agenda.domain.TurnoRechazadoException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Un test por escenario del delta de {@code agenda-turnos} del change validar-habilitacion-profesional (RN-AG-15).
 * Datos ficticios: además de "Consulta", la prestación "Tratamiento de conducto" de 30 minutos, para la que solo el
 * Dr. B está habilitado. Los dos profesionales están habilitados para "Consulta" y trabajan todo el día.
 */
class HabilitacionEnLaAgendaTest {

    private static final Long CONDUCTO = 2L;
    private static final Clock RELOJ = relojALas(8, 0);

    private final AgendaEnMemoria agenda = new AgendaEnMemoria();
    private final HabilitacionesEnMemoria habilitaciones = new HabilitacionesEnMemoria();
    private final DisponibilidadEnMemoria disponibilidad = new DisponibilidadEnMemoria();

    @BeforeEach
    void prepararAgenda() {
        agenda.recursos.agregarPrestacion(CONDUCTO, Duration.ofMinutes(30));
        habilitaciones.habilitar(DRA_A, CONSULTA).habilitar(DR_B, CONSULTA).habilitar(DR_B, CONDUCTO);
        disponibilidad.deLunesAViernes(DRA_A, LocalTime.of(7, 0), LocalTime.of(21, 0))
                .deLunesAViernes(DR_B, LocalTime.of(7, 0), LocalTime.of(21, 0));
    }

    @Nested
    @DisplayName("Requirement: Profesional habilitado para la prestación")
    class ProfesionalHabilitado {

        @Test
        @DisplayName("Crear un turno con un profesional no habilitado")
        void crearConProfesionalNoHabilitado() {
            // GIVEN que la Dra. A no está habilitada para "Tratamiento de conducto"
            // WHEN se crea un turno de "Tratamiento de conducto" con la Dra. A en el Sillón 1 a las 10:00
            // THEN el turno se rechaza con PROFESIONAL_NO_HABILITADO
            assertRechazo(() -> crear(DRA_A, SILLON_1, CONDUCTO, a(10, 0)), CodigoRechazo.PROFESIONAL_NO_HABILITADO);
            // AND no se crea ningún turno
            assertThat(agenda.turnos.todos()).isEmpty();
        }

        @Test
        @DisplayName("Crear un turno con un profesional habilitado")
        void crearConProfesionalHabilitado() {
            // GIVEN que el Dr. B está habilitado para "Tratamiento de conducto"
            // WHEN se crea un turno de "Tratamiento de conducto" con el Dr. B en el Sillón 1 a las 10:00
            Turno turno = crear(DR_B, SILLON_1, CONDUCTO, a(10, 0));

            // THEN el turno se crea en estado RESERVADO
            assertThat(turno.estado()).isEqualTo(EstadoTurno.RESERVADO);
            assertThat(turno.prestacionId()).isEqualTo(CONDUCTO);
            assertThat(agenda.turnos.todos()).containsExactly(turno);
        }

        @Test
        @DisplayName("Reprogramar a un profesional no habilitado")
        void reprogramarAProfesionalNoHabilitado() {
            // GIVEN un turno activo de "Paciente Uno" de "Tratamiento de conducto" de 10:00 a 10:30 con el Dr. B
            Turno original = deConducto(DR_B, SILLON_1, a(10, 0));
            // AND que la Dra. A no está habilitada para "Tratamiento de conducto"

            // WHEN el personal lo reprograma a las 11:00 con la Dra. A
            // THEN la operación se rechaza con PROFESIONAL_NO_HABILITADO
            assertRechazo(() -> reprogramar(original, a(11, 0), DRA_A), CodigoRechazo.PROFESIONAL_NO_HABILITADO);
            // AND el turno original sigue en estado RESERVADO de 10:00 a 10:30 con el Dr. B
            Turno guardado = agenda.turnos.porId(original.id());
            assertThat(guardado.estado()).isEqualTo(EstadoTurno.RESERVADO);
            assertThat(guardado.intervalo()).isEqualTo(new Intervalo(a(10, 0), a(10, 30)));
            assertThat(guardado.profesionalId()).isEqualTo(DR_B);
            // AND no se crea ningún turno nuevo
            assertThat(agenda.turnos.todos()).hasSize(1);
        }

        @Test
        @DisplayName("Reprogramar a otro profesional habilitado")
        void reprogramarAOtroProfesionalHabilitado() {
            // Triangulación: cambiar a un profesional que sí está habilitado se acepta
            Turno original = agenda.dePacienteUno(a(10, 0), EstadoTurno.RESERVADO);

            ReprogramarTurno.Reprogramacion resultado = reprogramar(original, a(11, 0), DR_B);

            assertThat(resultado.nuevo().profesionalId()).isEqualTo(DR_B);
            assertThat(agenda.turnos.porId(original.id()).estado()).isEqualTo(EstadoTurno.CANCELADO);
        }

        @Test
        @DisplayName("Reprogramar con el mismo profesional")
        void reprogramarConElMismoProfesional() {
            // GIVEN un turno activo de "Paciente Uno" de "Consulta" de 10:00 a 10:30 con la Dra. A en el Sillón 1
            Turno original = agenda.dePacienteUno(a(10, 0), EstadoTurno.RESERVADO);
            // AND que la Dra. A dejó de estar habilitada para "Consulta" después de darse el turno
            habilitaciones.quitar(DRA_A, CONSULTA);

            // WHEN el personal lo reprograma a las 11:00 sin cambiar de profesional
            ReprogramarTurno.Reprogramacion resultado = reprogramar(original, a(11, 0), null);

            // THEN el turno original queda en estado CANCELADO
            assertThat(agenda.turnos.porId(original.id()).estado()).isEqualTo(EstadoTurno.CANCELADO);
            // AND existe un turno nuevo de "Paciente Uno" de 11:00 a 11:30 con la Dra. A en estado RESERVADO
            Turno nuevo = agenda.turnos.porId(resultado.nuevo().id());
            assertThat(nuevo.pacienteId()).isEqualTo(PACIENTE_UNO);
            assertThat(nuevo.profesionalId()).isEqualTo(DRA_A);
            assertThat(nuevo.intervalo()).isEqualTo(new Intervalo(a(11, 0), a(11, 30)));
            assertThat(nuevo.estado()).isEqualTo(EstadoTurno.RESERVADO);
        }

        @Test
        @DisplayName("Sobreturno con un profesional no habilitado")
        void sobreturnoConProfesionalNoHabilitado() {
            // GIVEN que la Dra. A no está habilitada para "Tratamiento de conducto"
            // AND un motivo administrativo "Urgencia con el Paciente Uno"
            // WHEN el personal crea un sobreturno de "Tratamiento de conducto" con la Dra. A a las 10:00
            // THEN la operación se rechaza con PROFESIONAL_NO_HABILITADO
            assertRechazo(() -> sobreturno(DRA_A, CONDUCTO, a(10, 0), "Urgencia con el Paciente Uno"),
                    CodigoRechazo.PROFESIONAL_NO_HABILITADO);
            // AND no se crea ningún turno
            assertThat(agenda.turnos.todos()).isEmpty();
        }

        @Test
        @DisplayName("Sobreturno sin motivo con un profesional no habilitado")
        void sobreturnoSinMotivoYNoHabilitado() {
            // Triangulación: la validación del pedido va primero
            assertRechazo(() -> sobreturno(DRA_A, CONDUCTO, a(10, 0), " "), CodigoRechazo.DATOS_INVALIDOS);
        }

        @Test
        @DisplayName("Profesional inactivo y no habilitado")
        void profesionalInactivoYNoHabilitado() {
            // GIVEN que la Dra. A está inactiva y no está habilitada para "Tratamiento de conducto"
            agenda.recursos.desactivarProfesional(DRA_A);

            // WHEN se crea un turno de "Tratamiento de conducto" con la Dra. A en el Sillón 1 a las 10:00
            // THEN el turno se rechaza con RECURSO_INACTIVO
            assertRechazo(() -> crear(DRA_A, SILLON_1, CONDUCTO, a(10, 0)), CodigoRechazo.RECURSO_INACTIVO);
        }
    }

    @Nested
    @DisplayName("Requirement: Un solo error con prioridad fija")
    class Prioridad {

        @Test
        @DisplayName("Profesional no habilitado y ausente a la vez")
        void noHabilitadoYAusenteALaVez() {
            // GIVEN que la Dra. A no está habilitada para "Tratamiento de conducto"
            // AND una AUSENCIA de la Dra. A el 2026-12-01 de 10:00 a 12:00
            disponibilidad.periodo(DRA_A, AUSENCIA, a(10, 0), a(12, 0));

            // WHEN se crea un turno de "Tratamiento de conducto" con la Dra. A en el Sillón 1 a las 10:15
            // THEN el turno se rechaza con PROFESIONAL_NO_HABILITADO
            assertRechazo(() -> crear(DRA_A, SILLON_1, CONDUCTO, a(10, 15)), CodigoRechazo.PROFESIONAL_NO_HABILITADO);
        }

        @Test
        @DisplayName("Profesional no habilitado y solapamiento a la vez")
        void noHabilitadoYSolapamientoALaVez() {
            // Triangulación: también gana sobre los solapamientos
            agenda.dePacienteUno(a(10, 0), EstadoTurno.RESERVADO);

            assertRechazo(() -> crear(DRA_A, SILLON_1, CONDUCTO, a(10, 15)), CodigoRechazo.PROFESIONAL_NO_HABILITADO);
        }
    }

    private CrearTurno crearTurno() {
        return new CrearTurno(agenda.turnos, agenda.recursos, agenda.numeros, RELOJ, Margen.PREDETERMINADO,
                disponibilidad, BUENOS_AIRES, habilitaciones);
    }

    private Turno crear(Long profesional, Long sillon, Long prestacion, Instant inicio) {
        return crearTurno().ejecutar(new CrearTurnoCommand(PACIENTE_UNO, profesional, sillon, prestacion, inicio));
    }

    private Turno deConducto(Long profesional, Long sillon, Instant inicio) {
        return agenda.turnos.guardar(new Turno(null, PACIENTE_UNO, profesional, sillon, CONDUCTO,
                new Intervalo(inicio, inicio.plus(Duration.ofMinutes(30))), EstadoTurno.RESERVADO,
                agenda.numeros.generar()));
    }

    private ReprogramarTurno.Reprogramacion reprogramar(Turno original, Instant inicio, Long profesional) {
        return new ReprogramarTurno(agenda.turnos, crearTurno()).ejecutar(new ReprogramarTurnoCommand(
                original.id(), DNI_PACIENTE_UNO, original.numeroReserva().valor(), inicio, profesional, null));
    }

    private Turno sobreturno(Long profesional, Long prestacion, Instant inicio, String motivo) {
        CrearSobreturno caso = new CrearSobreturno(agenda.turnos, agenda.recursos,
                new SillonesDisponiblesEnMemoria(agenda.recursos, SILLON_1, SILLON_2), agenda.numeros, RELOJ,
                Margen.PREDETERMINADO, disponibilidad, BUENOS_AIRES, habilitaciones);
        return caso.ejecutar(new CrearSobreturnoCommand(PACIENTE_UNO, profesional, prestacion, inicio, motivo));
    }

    private static void assertRechazo(ThrowingCallable accion, CodigoRechazo esperado) {
        assertThatThrownBy(accion)
                .isInstanceOf(TurnoRechazadoException.class)
                .extracting(e -> ((TurnoRechazadoException) e).codigo())
                .isEqualTo(esperado);
    }
}
