package ar.turnos.odontologia.agenda.application;

import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.CONSULTA;
import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.DRA_A;
import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.DR_B;
import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.OTRO_PACIENTE;
import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.PACIENTE_UNO;
import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.SILLON_1;
import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.a;
import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.relojALas;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnos.odontologia.agenda.domain.AusenciaNoPermitidaException;
import ar.turnos.odontologia.agenda.domain.CodigoRechazo;
import ar.turnos.odontologia.agenda.domain.EstadoTurno;
import ar.turnos.odontologia.agenda.domain.Intervalo;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.agenda.domain.TurnoInexistenteException;
import ar.turnos.odontologia.agenda.domain.TurnoRechazadoException;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Ausencia del paciente (C-13, design D6; C-43: tolerancia de 15 minutos y marca desde la recepción), con dobles en
 * memoria y reloj fijo. El turno es de 10:00 a 10:30.
 */
class MarcarAusenciaTest {

    private final AgendaEnMemoria agenda = new AgendaEnMemoria();

    @Nested
    class OrdenDeVerificacion {

        @Test
        @DisplayName("Un turno inexistente o de otro paciente es TurnoInexistenteException")
        void inexistenteODeOtroPaciente() {
            Turno deOtro = agenda.existente(OTRO_PACIENTE, DRA_A, SILLON_1, a(10, 0), EstadoTurno.RESERVADO);

            assertThatThrownBy(() -> marcar(relojALas(10, 10), 999L, PACIENTE_UNO, DRA_A))
                    .isInstanceOf(TurnoInexistenteException.class);
            // aunque además sea de otro profesional y no haya empezado: no revela nada del turno
            assertThatThrownBy(() -> marcar(relojALas(9, 0), deOtro.id(), PACIENTE_UNO, DR_B))
                    .isInstanceOf(TurnoInexistenteException.class);
            assertThat(estado(deOtro)).isEqualTo(EstadoTurno.RESERVADO);
        }

        @Test
        @DisplayName("Otro profesional es AusenciaNoPermitidaException antes que el estado y el horario")
        void otroProfesional() {
            Turno cancelado = agenda.dePacienteUno(a(10, 0), EstadoTurno.CANCELADO);

            assertThatThrownBy(() -> marcar(relojALas(9, 0), cancelado.id(), PACIENTE_UNO, DR_B))
                    .isInstanceOf(AusenciaNoPermitidaException.class);
        }

        @Test
        @DisplayName("Un turno no activo es TURNO_NO_ACTIVO antes que el horario")
        void noActivo() {
            Turno cancelado = agenda.dePacienteUno(a(10, 0), EstadoTurno.CANCELADO);

            assertRechazo(() -> marcar(relojALas(9, 0), cancelado.id(), PACIENTE_UNO, DRA_A),
                    CodigoRechazo.TURNO_NO_ACTIVO);
        }

        @Test
        @DisplayName("Antes de la tolerancia es AUSENCIA_ANTES_DE_TOLERANCIA y queda sin cambios")
        void antesDeLaTolerancia() {
            Turno turno = agenda.dePacienteUno(a(10, 0), EstadoTurno.RESERVADO);

            assertRechazo(() -> marcar(relojALas(9, 50), turno.id(), PACIENTE_UNO, DRA_A),
                    CodigoRechazo.AUSENCIA_ANTES_DE_TOLERANCIA);
            assertRechazo(() -> marcar(relojALas(10, 14), turno.id(), PACIENTE_UNO, DRA_A),
                    CodigoRechazo.AUSENCIA_ANTES_DE_TOLERANCIA);
            assertThat(estado(turno)).isEqualTo(EstadoTurno.RESERVADO);
        }

        @Test
        @DisplayName("Al cumplirse la tolerancia, el turno pasa a AUSENTE")
        void exito() {
            Turno reservado = agenda.dePacienteUno(a(10, 0), EstadoTurno.RESERVADO);
            Turno confirmado = agenda.dePacienteUno(a(11, 0), EstadoTurno.CONFIRMADO);

            Turno ausente = marcar(relojALas(10, 15), reservado.id(), PACIENTE_UNO, DRA_A);
            marcar(relojALas(12, 0), confirmado.id(), PACIENTE_UNO, DRA_A);

            assertThat(ausente.estado()).isEqualTo(EstadoTurno.AUSENTE);
            assertThat(ausente.esActivo()).isFalse();
            assertThat(estado(reservado)).isEqualTo(EstadoTurno.AUSENTE);
            assertThat(estado(confirmado)).isEqualTo(EstadoTurno.AUSENTE);
        }
    }

    @Nested
    class ConservaLosCamposDeC08 {

        @Test
        @DisplayName("Un sobreturno ausente sigue siendo sobreturno, con su motivo")
        void sobreturno() {
            Turno sobreturno = agenda.turnos.guardar(new Turno(null, PACIENTE_UNO, DRA_A, SILLON_1, CONSULTA,
                    new Intervalo(a(10, 0), a(10, 30)), EstadoTurno.RESERVADO, agenda.numeros.generar(),
                    true, "Urgencia ficticia", false, null));

            Turno ausente = marcar(relojALas(10, 20), sobreturno.id(), PACIENTE_UNO, DRA_A);

            assertThat(ausente.esSobreturno()).isTrue();
            assertThat(ausente.motivoSobreturno()).isEqualTo("Urgencia ficticia");
            assertThat(ausente.numeroReserva()).isEqualTo(sobreturno.numeroReserva());
        }

        @Test
        @DisplayName("Un turno extendido ausente conserva su extensión y su motivo")
        void extendido() {
            Turno extendido = agenda.turnos.guardar(new Turno(null, PACIENTE_UNO, DRA_A, SILLON_1, CONSULTA,
                    new Intervalo(a(10, 0), a(10, 45)), EstadoTurno.RESERVADO, agenda.numeros.generar(),
                    false, null, true, "Complicación ficticia"));

            Turno ausente = marcar(relojALas(10, 20), extendido.id(), PACIENTE_UNO, DRA_A);

            assertThat(ausente.extendido()).isTrue();
            assertThat(ausente.motivoExtension()).isEqualTo("Complicación ficticia");
            assertThat(ausente.intervalo().fin()).isEqualTo(a(10, 45));
        }
    }

    @Nested
    class DesdeLaRecepcion {

        @Test
        @DisplayName("La recepción marca la ausencia sin paciente ni profesional que comprobar")
        void recepcion() {
            Turno turno = agenda.dePacienteUno(a(10, 0), EstadoTurno.RESERVADO);

            Turno ausente = new MarcarAusencia(agenda.turnos, relojALas(10, 20))
                    .ejecutar(MarcarAusenciaCommand.deRecepcion(turno.id()));

            assertThat(ausente.estado()).isEqualTo(EstadoTurno.AUSENTE);
            assertThat(estado(turno)).isEqualTo(EstadoTurno.AUSENTE);
        }

        @Test
        @DisplayName("Un turno inexistente es TurnoInexistenteException también para la recepción")
        void inexistente() {
            assertThatThrownBy(() -> new MarcarAusencia(agenda.turnos, relojALas(10, 20))
                    .ejecutar(MarcarAusenciaCommand.deRecepcion(999L)))
                    .isInstanceOf(TurnoInexistenteException.class);
        }
    }

    private Turno marcar(Clock reloj, Long turnoId, Long pacienteId, Long profesionalId) {
        return new MarcarAusencia(agenda.turnos, reloj)
                .ejecutar(new MarcarAusenciaCommand(turnoId, pacienteId, profesionalId));
    }

    private EstadoTurno estado(Turno turno) {
        return agenda.turnos.porId(turno.id()).estado();
    }

    private static void assertRechazo(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion,
                                      CodigoRechazo codigo) {
        assertThatThrownBy(accion).isInstanceOf(TurnoRechazadoException.class).extracting("codigo").isEqualTo(codigo);
    }
}
