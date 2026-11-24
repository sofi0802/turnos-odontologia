package ar.turnos.odontologia.agenda.application;

import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.BUENOS_AIRES;
import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.CONSULTA;
import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.DRA_A;
import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.DR_B;
import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.OTRO_PACIENTE;
import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.SILLON_1;
import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.SILLON_2;
import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.a;
import static ar.turnos.odontologia.agenda.application.AgendaEnMemoria.relojALas;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnos.odontologia.agenda.application.AuditoriaTurnos.Accion;
import ar.turnos.odontologia.agenda.domain.CodigoRechazo;
import ar.turnos.odontologia.agenda.domain.EstadoTurno;
import ar.turnos.odontologia.agenda.domain.Intervalo;
import ar.turnos.odontologia.agenda.domain.Margen;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.agenda.domain.TurnoInexistenteException;
import ar.turnos.odontologia.agenda.domain.TurnoRechazadoException;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Llegada y deshacer la ausencia (C-43, design D2, D5 y D6), con dobles en memoria y reloj fijo. El turno de
 * "Paciente Uno" es de 10:00 a 10:30 con la Dra. A en el Sillón 1; el margen es de 10 minutos.
 */
class AsistenciaTest {

    private final AgendaEnMemoria agenda = new AgendaEnMemoria();
    private final AuditoriaEnMemoria auditoria = new AuditoriaEnMemoria();

    @Nested
    class Llegada {

        @Test
        @DisplayName("Llegada tarde: sigue activo con la hora de llegada")
        void llegadaTarde() {
            Turno turno = agenda.dePacienteUno(a(10, 0), EstadoTurno.RESERVADO);

            Turno resultado = llegada(relojALas(10, 12), turno.id());

            assertThat(resultado.estado()).isEqualTo(EstadoTurno.RESERVADO);
            assertThat(resultado.llegadaEn()).isEqualTo(a(10, 12));
            assertThat(agenda.turnos.porId(turno.id()).llegadaEn()).isEqualTo(a(10, 12));
        }

        @Test
        @DisplayName("Dentro del margen sigue activo; al terminar el margen pasa a ATENDIDO en la misma operación")
        void correccionDeUnTurnoSinRegistro() {
            Turno enElMargen = agenda.dePacienteUno(a(10, 0), EstadoTurno.CONFIRMADO);
            Turno terminado = agenda.dePacienteUno(a(8, 0), EstadoTurno.RESERVADO);

            assertThat(llegada(relojALas(10, 39), enElMargen.id()).estado()).isEqualTo(EstadoTurno.CONFIRMADO);
            Turno corregido = llegada(relojALas(10, 39), terminado.id());

            assertThat(corregido.estado()).isEqualTo(EstadoTurno.ATENDIDO);
            assertThat(corregido.llegadaEn()).isEqualTo(a(10, 39));
        }

        @Test
        @DisplayName("Un turno inexistente es TurnoInexistenteException")
        void inexistente() {
            assertThatThrownBy(() -> llegada(relojALas(10, 0), 999L)).isInstanceOf(TurnoInexistenteException.class);
        }
    }

    @Nested
    class Deshacer {

        @Test
        @DisplayName("Vuelve a CONFIRMADO si la auditoría tiene la confirmación")
        void vuelveAConfirmado() {
            Turno turno = agenda.dePacienteUno(a(10, 0), EstadoTurno.AUSENTE);
            auditoria.registrar(turno.id(), Accion.CONFIRMAR, "paciente-token", null);

            assertThat(deshacer(relojALas(10, 22), turno.id()).estado()).isEqualTo(EstadoTurno.CONFIRMADO);
            assertThat(agenda.turnos.porId(turno.id()).estado()).isEqualTo(EstadoTurno.CONFIRMADO);
        }

        @Test
        @DisplayName("Vuelve a RESERVADO si nunca se confirmó")
        void vuelveAReservado() {
            Turno turno = agenda.dePacienteUno(a(10, 0), EstadoTurno.AUSENTE);

            assertThat(deshacer(relojALas(10, 22), turno.id()).estado()).isEqualTo(EstadoTurno.RESERVADO);
        }

        @Test
        @DisplayName("Con el sillón ocupado por otro turno es SOLAPAMIENTO_SILLON y queda AUSENTE")
        void sillonOcupado() {
            Turno turno = agenda.dePacienteUno(a(10, 0), EstadoTurno.AUSENTE);
            agenda.turnos.guardar(new Turno(null, OTRO_PACIENTE, DR_B, SILLON_1, CONSULTA,
                    new Intervalo(a(10, 20), a(10, 40)), EstadoTurno.RESERVADO, agenda.numeros.generar(),
                    true, "Urgencia ficticia", false, null));

            assertRechazo(() -> deshacer(relojALas(10, 22), turno.id()), CodigoRechazo.SOLAPAMIENTO_SILLON);
            assertThat(agenda.turnos.porId(turno.id()).estado()).isEqualTo(EstadoTurno.AUSENTE);
        }

        @Test
        @DisplayName("Con el profesional ocupado en otro sillón es SOLAPAMIENTO_PROFESIONAL")
        void profesionalOcupado() {
            Turno turno = agenda.dePacienteUno(a(10, 0), EstadoTurno.AUSENTE);
            agenda.existente(OTRO_PACIENTE, DRA_A, SILLON_2, a(10, 20), EstadoTurno.RESERVADO);

            assertRechazo(() -> deshacer(relojALas(10, 22), turno.id()), CodigoRechazo.SOLAPAMIENTO_PROFESIONAL);
        }

        @Test
        @DisplayName("Un turno en el margen del sillón es MARGEN_INSUFICIENTE")
        void margen() {
            Turno turno = agenda.dePacienteUno(a(10, 0), EstadoTurno.AUSENTE);
            agenda.existente(OTRO_PACIENTE, DR_B, SILLON_1, a(10, 35), EstadoTurno.RESERVADO);

            assertRechazo(() -> deshacer(relojALas(10, 22), turno.id()), CodigoRechazo.MARGEN_INSUFICIENTE);
        }

        @Test
        @DisplayName("Los turnos no activos del horario no lo bloquean")
        void turnosNoActivos() {
            Turno turno = agenda.dePacienteUno(a(10, 0), EstadoTurno.AUSENTE);
            agenda.existente(OTRO_PACIENTE, DRA_A, SILLON_1, a(10, 20), EstadoTurno.CANCELADO);

            assertThat(deshacer(relojALas(10, 22), turno.id()).estado()).isEqualTo(EstadoTurno.RESERVADO);
        }

        @Test
        @DisplayName("Un sobreturno ausente se restaura aunque el profesional tenga otro turno a esa hora")
        void sobreturno() {
            agenda.existente(OTRO_PACIENTE, DRA_A, SILLON_2, a(10, 0), EstadoTurno.RESERVADO);
            Turno sobreturno = agenda.turnos.guardar(new Turno(null, AgendaEnMemoria.PACIENTE_UNO, DRA_A, SILLON_1,
                    CONSULTA, new Intervalo(a(10, 0), a(10, 30)), EstadoTurno.AUSENTE, agenda.numeros.generar(),
                    true, "Urgencia ficticia", false, null));

            assertThat(deshacer(relojALas(10, 22), sobreturno.id()).estado()).isEqualTo(EstadoTurno.RESERVADO);
        }

        @Test
        @DisplayName("Después del fin es TURNO_TERMINADO, y un turno no ausente, TURNO_NO_AUSENTE")
        void rechazosDelDominio() {
            Turno ausente = agenda.dePacienteUno(a(10, 0), EstadoTurno.AUSENTE);
            Turno activo = agenda.dePacienteUno(a(11, 0), EstadoTurno.RESERVADO);

            assertRechazo(() -> deshacer(relojALas(10, 31), ausente.id()), CodigoRechazo.TURNO_TERMINADO);
            assertRechazo(() -> deshacer(relojALas(10, 22), activo.id()), CodigoRechazo.TURNO_NO_AUSENTE);
        }
    }

    private Turno llegada(Clock reloj, Long turnoId) {
        return new RegistrarLlegada(agenda.turnos, reloj, BUENOS_AIRES, Margen.PREDETERMINADO).ejecutar(turnoId);
    }

    private Turno deshacer(Clock reloj, Long turnoId) {
        return new DeshacerAusencia(agenda.turnos, auditoria, reloj, Margen.PREDETERMINADO).ejecutar(turnoId);
    }

    private static void assertRechazo(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion,
                                      CodigoRechazo codigo) {
        assertThatThrownBy(accion).isInstanceOf(TurnoRechazadoException.class).extracting("codigo").isEqualTo(codigo);
    }

    /** Doble de {@link AuditoriaTurnos}: guarda el par turno y acción. */
    private static final class AuditoriaEnMemoria implements AuditoriaTurnos {

        private final Set<String> registros = new HashSet<>();

        @Override
        public void registrar(Long turnoId, Accion accion, String actor, Long turnoRelacionadoId) {
            registros.add(turnoId + ":" + accion);
        }

        @Override
        public boolean registrada(Long turnoId, Accion accion) {
            return registros.contains(turnoId + ":" + accion);
        }
    }
}
