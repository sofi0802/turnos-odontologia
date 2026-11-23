package ar.turnos.odontologia.agenda.disponibilidad.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.turnos.odontologia.agenda.application.ReasignarTurnoCommand;
import ar.turnos.odontologia.agenda.application.TurnoRepository;
import ar.turnos.odontologia.agenda.disponibilidad.application.RegistroDisponibilidad.Clase;
import ar.turnos.odontologia.agenda.disponibilidad.application.RegistroDisponibilidad.Periodo;
import ar.turnos.odontologia.agenda.domain.CodigoRechazo;
import ar.turnos.odontologia.agenda.domain.EstadoTurno;
import ar.turnos.odontologia.agenda.domain.Intervalo;
import ar.turnos.odontologia.agenda.domain.NumeroReserva;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.agenda.domain.TurnoRechazadoException;
import ar.turnos.odontologia.agenda.domain.reglas.PeriodoSinAtencion;
import ar.turnos.odontologia.agenda.infrastructure.ServicioAgenda;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Tarea 6.2 de C-07: sin un adaptador de {@link AvisoReasignacion} publicado, la reasignación usa el aviso inactivo
 * y termina bien. Con C-11 en master el contexto completo siempre tiene el adaptador real, así que se prueba sin
 * Spring.
 */
class ServicioReasignacionTest {

    private static final Long DRA_A = 1L;
    private static final Long DR_B = 2L;
    private static final Long AUSENCIA = 10L;
    private static final Long BLOQUEO = 20L;
    private static final Long TURNO = 100L;
    private static final Instant INICIO = Instant.parse("2026-12-09T13:00:00Z");

    private final RegistroDisponibilidad registro = mock(RegistroDisponibilidad.class);
    private final TurnoRepository turnos = mock(TurnoRepository.class);
    private final ServicioAgenda agenda = mock(ServicioAgenda.class);
    private final PlatformTransactionManager transacciones = mock(PlatformTransactionManager.class);
    private final Turno nuevo = turno(101L, DR_B);

    @BeforeEach
    void prepararTurnoAfectado() {
        when(transacciones.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(registro.existeProfesional(DRA_A)).thenReturn(true);
        PeriodoSinAtencion licencia = PeriodoSinAtencion.de(PeriodoSinAtencion.Tipo.LICENCIA,
                Instant.parse("2026-12-07T03:00:00Z"), Instant.parse("2026-12-12T03:00:00Z"));
        when(registro.periodo(DRA_A, Clase.AUSENCIA, AUSENCIA))
                .thenReturn(Optional.of(new Periodo(AUSENCIA, DRA_A, licencia, null)));
        when(turnos.buscarQueIntersecan(eq(DRA_A), eq(null), any())).thenReturn(List.of(turno(TURNO, DRA_A)));
        when(agenda.reasignar(any(ReasignarTurnoCommand.class), eq("recepcion"))).thenReturn(nuevo);
    }

    @Test
    @DisplayName("Sin módulo de notificaciones la reasignación usa el aviso inactivo")
    void sinAdaptadorUsaElAvisoInactivo() {
        ServicioReasignacion servicio = new ServicioReasignacion(registro, turnos, agenda,
                new StaticListableBeanFactory().getBeanProvider(AvisoReasignacion.class), transacciones);

        Turno resultado = servicio.reasignar(DRA_A, Clase.AUSENCIA, AUSENCIA, TURNO, DR_B, null, null, "recepcion");

        assertThat(resultado).isSameAs(nuevo);
    }

    @Test
    @DisplayName("Con la clase BLOQUEO busca el período entre los bloqueos y verifica que el turno esté afectado")
    void conBloqueoUsaLosBloqueos() {
        PeriodoSinAtencion bloqueo = PeriodoSinAtencion.de(PeriodoSinAtencion.Tipo.BLOQUEO,
                Instant.parse("2026-12-09T12:00:00Z"), Instant.parse("2026-12-09T15:00:00Z"));
        when(registro.periodo(DRA_A, Clase.BLOQUEO, BLOQUEO))
                .thenReturn(Optional.of(new Periodo(BLOQUEO, DRA_A, bloqueo, null)));
        ServicioReasignacion servicio = servicio();

        Turno resultado = servicio.reasignar(DRA_A, Clase.BLOQUEO, BLOQUEO, TURNO, DR_B, null, null, "recepcion");

        assertThat(resultado).isSameAs(nuevo);
    }

    @Test
    @DisplayName("Un id de ausencia citado como bloqueo no existe: 404 y no se reasigna nada")
    void elIdDeUnaAusenciaNoSirveComoBloqueo() {
        // AUSENCIA existe como ausencia (ver prepararTurnoAfectado), pero no como bloqueo
        ServicioReasignacion servicio = servicio();

        assertThatThrownBy(() -> servicio.reasignar(DRA_A, Clase.BLOQUEO, AUSENCIA, TURNO, DR_B, null, null,
                "recepcion")).isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("No existe el bloqueo");
        verify(agenda, never()).reasignar(any(ReasignarTurnoCommand.class), any());
    }

    @Test
    @DisplayName("Un turno que no se cruza con el bloqueo se rechaza con TURNO_NO_AFECTADO")
    void turnoFueraDelBloqueo() {
        PeriodoSinAtencion bloqueo = PeriodoSinAtencion.de(PeriodoSinAtencion.Tipo.BLOQUEO,
                Instant.parse("2026-12-09T20:00:00Z"), Instant.parse("2026-12-09T21:00:00Z"));
        when(registro.periodo(DRA_A, Clase.BLOQUEO, BLOQUEO))
                .thenReturn(Optional.of(new Periodo(BLOQUEO, DRA_A, bloqueo, null)));
        ServicioReasignacion servicio = servicio();

        assertThatThrownBy(() -> servicio.reasignar(DRA_A, Clase.BLOQUEO, BLOQUEO, TURNO, DR_B, null, null,
                "recepcion")).isInstanceOfSatisfying(TurnoRechazadoException.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoRechazo.TURNO_NO_AFECTADO));
        verify(agenda, never()).reasignar(any(ReasignarTurnoCommand.class), any());
    }

    private ServicioReasignacion servicio() {
        return new ServicioReasignacion(registro, turnos, agenda,
                new StaticListableBeanFactory().getBeanProvider(AvisoReasignacion.class), transacciones);
    }

    private static Turno turno(Long id, Long profesional) {
        return new Turno(id, 1L, profesional, 1L, 1L, new Intervalo(INICIO, INICIO.plusSeconds(1800)),
                EstadoTurno.RESERVADO, new NumeroReserva("K7M2-QX9P"));
    }
}
