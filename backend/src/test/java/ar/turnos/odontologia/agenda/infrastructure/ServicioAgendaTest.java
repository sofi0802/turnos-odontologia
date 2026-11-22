package ar.turnos.odontologia.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.turnos.odontologia.agenda.application.AuditoriaTurnos;
import ar.turnos.odontologia.agenda.application.AuditoriaTurnos.Accion;
import ar.turnos.odontologia.agenda.application.CancelarTurno;
import ar.turnos.odontologia.agenda.application.ConfirmarTurno;
import ar.turnos.odontologia.agenda.application.ConsultaAgenda;
import ar.turnos.odontologia.agenda.application.CrearSobreturno;
import ar.turnos.odontologia.agenda.application.CrearSobreturnoCommand;
import ar.turnos.odontologia.agenda.application.CrearTurno;
import ar.turnos.odontologia.agenda.application.CrearTurnoCommand;
import ar.turnos.odontologia.agenda.application.ExtenderTurno;
import ar.turnos.odontologia.agenda.application.ExtenderTurnoCommand;
import ar.turnos.odontologia.agenda.application.MarcarAusencia;
import ar.turnos.odontologia.agenda.application.ReasignarTurno;
import ar.turnos.odontologia.agenda.application.ReprogramarTurno;
import ar.turnos.odontologia.agenda.application.SillonTomadoException;
import ar.turnos.odontologia.agenda.domain.Intervalo;
import ar.turnos.odontologia.agenda.domain.NumeroReserva;
import ar.turnos.odontologia.agenda.domain.Turno;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** Reintento transaccional de las cuatro operaciones que pueden abortar por un bloqueo (design D6). */
class ServicioAgendaTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String ACTOR = "recepcion";
    private static final PessimisticLockingFailureException BLOQUEO =
            new PessimisticLockingFailureException("bloqueo simulado por el test");

    private final CrearTurno crear = mock(CrearTurno.class);
    private final CrearSobreturno crearSobreturno = mock(CrearSobreturno.class);
    private final ExtenderTurno extender = mock(ExtenderTurno.class);
    private final CancelarTurno cancelar = mock(CancelarTurno.class);
    private final ReprogramarTurno reprogramar = mock(ReprogramarTurno.class);
    private final ConfirmarTurno confirmar = mock(ConfirmarTurno.class);
    private final ReasignarTurno reasignar = mock(ReasignarTurno.class);
    private final MarcarAusencia marcarAusencia = mock(MarcarAusencia.class);
    private final ConsultaAgenda consulta = mock(ConsultaAgenda.class);
    private final AuditoriaTurnos auditoria = mock(AuditoriaTurnos.class);
    private final PlatformTransactionManager transacciones = mock(PlatformTransactionManager.class);

    private ServicioAgenda servicio;

    @BeforeEach
    void armarServicio() {
        when(transacciones.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        servicio = new ServicioAgenda(crear, crearSobreturno, extender, cancelar, reprogramar,
                confirmar, reasignar, marcarAusencia, consulta, auditoria, transacciones);
    }

    @Test
    @DisplayName("El reintento del sobreturno audita solo la tentativa que confirma")
    void reintentoDelSobreturno() {
        when(crearSobreturno.ejecutar(any(CrearSobreturnoCommand.class), anySet()))
                .thenThrow(BLOQUEO).thenReturn(turno());

        Turno resultado = servicio.crearSobreturno(comandoSobreturno(), ACTOR);

        assertThat(resultado.id()).isEqualTo(7L);
        verify(crearSobreturno, times(2)).ejecutar(any(CrearSobreturnoCommand.class), anySet());
        verify(auditoria, times(1)).registrar(7L, Accion.SOBRETURNO, ACTOR, null);
    }

    @Test
    @DisplayName("El sobreturno reintenta sin el sillón que se tomó en una carrera")
    void reintentoSinElSillonTomado() {
        when(crearSobreturno.ejecutar(any(CrearSobreturnoCommand.class), eq(Set.of())))
                .thenThrow(new SillonTomadoException(1L));
        when(crearSobreturno.ejecutar(any(CrearSobreturnoCommand.class), eq(Set.of(1L))))
                .thenReturn(turno());

        Turno resultado = servicio.crearSobreturno(comandoSobreturno(), ACTOR);

        assertThat(resultado.id()).isEqualTo(7L);
        verify(crearSobreturno, times(2)).ejecutar(any(CrearSobreturnoCommand.class), anySet());
        verify(auditoria, times(1)).registrar(7L, Accion.SOBRETURNO, ACTOR, null);
    }

    @Test
    @DisplayName("Si se toman todos los sillones el sobreturno propaga SIN_SILLON_DISPONIBLE")
    void reintentoAcotadoPorLosSillones() {
        when(crearSobreturno.ejecutar(any(CrearSobreturnoCommand.class), eq(Set.of())))
                .thenThrow(new SillonTomadoException(1L));
        when(crearSobreturno.ejecutar(any(CrearSobreturnoCommand.class), eq(Set.of(1L))))
                .thenThrow(new SillonTomadoException(2L));
        when(crearSobreturno.ejecutar(any(CrearSobreturnoCommand.class), eq(Set.of(1L, 2L))))
                .thenThrow(new ar.turnos.odontologia.agenda.domain.TurnoRechazadoException(
                        ar.turnos.odontologia.agenda.domain.CodigoRechazo.SIN_SILLON_DISPONIBLE, "sin sillón"));

        assertThatThrownBy(() -> servicio.crearSobreturno(comandoSobreturno(), ACTOR))
                .isInstanceOf(ar.turnos.odontologia.agenda.domain.TurnoRechazadoException.class);

        verify(crearSobreturno, times(3)).ejecutar(any(CrearSobreturnoCommand.class), anySet());
        verify(auditoria, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("El reintento de la extensión audita solo la tentativa que confirma")
    void reintentoDeLaExtension() {
        when(extender.ejecutar(any(ExtenderTurnoCommand.class)))
                .thenThrow(BLOQUEO).thenReturn(turno());

        Turno resultado = servicio.extender(comandoExtension(), ACTOR);

        assertThat(resultado.id()).isEqualTo(7L);
        verify(extender, times(2)).ejecutar(any(ExtenderTurnoCommand.class));
        verify(auditoria, times(1)).registrar(7L, Accion.EXTENDER, ACTOR, null);
    }

    @Test
    @DisplayName("Tres fallos consecutivos propagan el bloqueo en el sobreturno")
    void tresFallosEnElSobreturno() {
        when(crearSobreturno.ejecutar(any(CrearSobreturnoCommand.class), anySet())).thenThrow(BLOQUEO);

        assertThatThrownBy(() -> servicio.crearSobreturno(comandoSobreturno(), ACTOR))
                .isSameAs(BLOQUEO);

        verify(crearSobreturno, times(3)).ejecutar(any(CrearSobreturnoCommand.class), anySet());
        verify(auditoria, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Tres fallos consecutivos propagan el bloqueo en la extensión")
    void tresFallosEnLaExtension() {
        when(extender.ejecutar(any(ExtenderTurnoCommand.class))).thenThrow(BLOQUEO);

        assertThatThrownBy(() -> servicio.extender(comandoExtension(), ACTOR))
                .isSameAs(BLOQUEO);

        verify(extender, times(3)).ejecutar(any(ExtenderTurnoCommand.class));
        verify(auditoria, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("La creación normal comparte el mismo reintento que las operaciones nuevas")
    void reintentoDeLaCreacionNormal() {
        when(crear.ejecutar(any(CrearTurnoCommand.class))).thenThrow(BLOQUEO).thenReturn(turno());

        Turno resultado = servicio.crear(new CrearTurnoCommand(1L, 1L, 1L, 1L, a(10, 0)), ACTOR);

        assertThat(resultado.id()).isEqualTo(7L);
        verify(crear, times(2)).ejecutar(any(CrearTurnoCommand.class));
        verify(auditoria, times(1)).registrar(eq(7L), eq(Accion.CREAR), eq(ACTOR), any());
    }

    private static Turno turno() {
        return Turno.reservar(1L, 1L, 1L, 1L, new Intervalo(a(10, 0), a(10, 30)),
                new NumeroReserva("K7M2-QX9P")).conId(7L);
    }

    private static CrearSobreturnoCommand comandoSobreturno() {
        return new CrearSobreturnoCommand(1L, 1L, 1L, a(10, 0), "Urgencia administrativa");
    }

    private static ExtenderTurnoCommand comandoExtension() {
        return new ExtenderTurnoCommand(7L, a(10, 45), "Complicación administrativa");
    }

    private static Instant a(int hora, int minuto) {
        return LocalDateTime.of(2026, 12, 1, hora, minuto).atZone(BUENOS_AIRES).toInstant();
    }
}
