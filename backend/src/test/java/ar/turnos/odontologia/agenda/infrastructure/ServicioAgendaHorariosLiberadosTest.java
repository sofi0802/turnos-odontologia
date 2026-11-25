package ar.turnos.odontologia.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.turnos.odontologia.agenda.application.AuditoriaTurnos;
import ar.turnos.odontologia.agenda.application.CancelarTurno;
import ar.turnos.odontologia.agenda.application.CancelarTurnoCommand;
import ar.turnos.odontologia.agenda.application.ConfirmarTurno;
import ar.turnos.odontologia.agenda.application.ConsultaAgenda;
import ar.turnos.odontologia.agenda.application.CrearSobreturno;
import ar.turnos.odontologia.agenda.application.CrearTurno;
import ar.turnos.odontologia.agenda.application.ExtenderTurno;
import ar.turnos.odontologia.agenda.application.HorariosLiberados;
import ar.turnos.odontologia.agenda.application.MarcarAusencia;
import ar.turnos.odontologia.agenda.application.MarcarAusenciaCommand;
import ar.turnos.odontologia.agenda.application.ReasignarTurno;
import ar.turnos.odontologia.agenda.application.ReasignarTurnoCommand;
import ar.turnos.odontologia.agenda.application.ReprogramarTurno;
import ar.turnos.odontologia.agenda.application.ReprogramarTurno.Reprogramacion;
import ar.turnos.odontologia.agenda.application.ReprogramarTurnoCommand;
import ar.turnos.odontologia.agenda.domain.EstadoTurno;
import ar.turnos.odontologia.agenda.domain.Intervalo;
import ar.turnos.odontologia.agenda.domain.NumeroReserva;
import ar.turnos.odontologia.agenda.domain.Turno;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * C-15 (design D2): la agenda le pasa a la lista de espera el horario que libera una cancelación o una
 * reprogramación, y una falla de la lista nunca deshace la operación. La reasignación y la ausencia no liberan nada.
 */
class ServicioAgendaHorariosLiberadosTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String ACTOR = "recepcion";

    private final CancelarTurno cancelar = mock(CancelarTurno.class);
    private final ReprogramarTurno reprogramar = mock(ReprogramarTurno.class);
    private final ReasignarTurno reasignar = mock(ReasignarTurno.class);
    private final MarcarAusencia marcarAusencia = mock(MarcarAusencia.class);
    private final AuditoriaTurnos auditoria = mock(AuditoriaTurnos.class);
    private final PlatformTransactionManager transacciones = mock(PlatformTransactionManager.class);
    private final List<Turno> liberados = new ArrayList<>();

    private HorariosLiberados lista = liberados::add;

    private ServicioAgenda servicio() {
        return new ServicioAgenda(mock(CrearTurno.class), mock(CrearSobreturno.class), mock(ExtenderTurno.class),
                cancelar, reprogramar, mock(ConfirmarTurno.class), reasignar, marcarAusencia,
                mock(CasosAsistencia.class), mock(ConsultaAgenda.class), auditoria, transacciones, () -> lista);
    }

    @BeforeEach
    void transaccionesFalsas() {
        when(transacciones.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @Test
    @DisplayName("La cancelación le pasa el turno cancelado a la lista de espera")
    void cancelarLibera() {
        Turno cancelado = turno(7L, 10).cancelado();
        when(cancelar.ejecutar(any(CancelarTurnoCommand.class))).thenReturn(cancelado);

        servicio().cancelar(new CancelarTurnoCommand(7L, "99000003", "K7M2-QX9P"), ACTOR);

        assertThat(liberados).containsExactly(cancelado);
    }

    @Test
    @DisplayName("La cancelación se guarda aunque la oferta falle")
    void laCancelacionSobreviveALaOferta() {
        Turno cancelado = turno(7L, 10).cancelado();
        when(cancelar.ejecutar(any(CancelarTurnoCommand.class))).thenReturn(cancelado);
        lista = turno -> {
            throw new IllegalStateException("falla simulada de la lista de espera");
        };

        Turno resultado = servicio().cancelar(new CancelarTurnoCommand(7L, "99000003", "K7M2-QX9P"), ACTOR);

        assertThat(resultado.estado()).isEqualTo(EstadoTurno.CANCELADO);
        verify(auditoria).registrarCancelacion(7L, ACTOR, null);
        verify(transacciones).commit(any());
    }

    @Test
    @DisplayName("La reprogramación libera el horario viejo")
    void reprogramarLiberaElOriginal() {
        Turno original = turno(7L, 10).cancelado();
        Turno nuevo = turno(8L, 11);
        when(reprogramar.ejecutar(any(ReprogramarTurnoCommand.class))).thenReturn(new Reprogramacion(original, nuevo));

        servicio().reprogramar(new ReprogramarTurnoCommand(7L, "99000003", "K7M2-QX9P", a(11, 0), null, null), ACTOR);

        assertThat(liberados).containsExactly(original);
    }

    @Test
    @DisplayName("La reasignación por ausencia o bloqueo del profesional no genera ofertas")
    void reasignarNoLibera() {
        when(reasignar.ejecutar(any(ReasignarTurnoCommand.class)))
                .thenReturn(new Reprogramacion(turno(7L, 10).cancelado(), turno(8L, 11)));

        servicio().reasignar(new ReasignarTurnoCommand(7L, 2L, a(11, 0), 2L), ACTOR);

        assertThat(liberados).isEmpty();
    }

    @Test
    @DisplayName("La ausencia del paciente no genera ofertas")
    void ausenciaNoLibera() {
        when(marcarAusencia.ejecutar(any(MarcarAusenciaCommand.class))).thenReturn(turno(7L, 10));

        servicio().marcarAusencia(MarcarAusenciaCommand.deRecepcion(7L), ACTOR);

        assertThat(liberados).isEmpty();
    }

    private static Turno turno(Long id, int hora) {
        return Turno.reservar(1L, 1L, 1L, 1L, new Intervalo(a(hora, 0), a(hora, 30)),
                new NumeroReserva("K7M2-QX9P")).conId(id);
    }

    private static Instant a(int hora, int minuto) {
        return LocalDateTime.of(2026, 12, 3, hora, minuto).atZone(BUENOS_AIRES).toInstant();
    }
}
