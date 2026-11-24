package ar.turnos.odontologia.agenda.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Asistencia en el dominio (C-43, spec {@code agenda-turnos}): llegada, ausencia con la tolerancia fija de 15 minutos
 * y deshacer la ausencia. El turno es del 2026-12-01 de 10:00 a 10:30 en Buenos Aires.
 */
class TurnoAsistenciaTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");

    private static Instant diciembre(int dia, int hora, int minuto) {
        return LocalDateTime.of(2026, 12, dia, hora, minuto).atZone(BUENOS_AIRES).toInstant();
    }

    private static Turno turno(EstadoTurno estado) {
        return new Turno(5L, 1L, 2L, 3L, 4L, new Intervalo(diciembre(1, 10, 0), diciembre(1, 10, 30)), estado,
                new NumeroReserva("ABCD-2345"));
    }

    private static void rechazo(Runnable operacion, CodigoRechazo codigo) {
        assertThatThrownBy(operacion::run).isInstanceOf(TurnoRechazadoException.class)
                .extracting("codigo").isEqualTo(codigo);
    }

    @Test
    @DisplayName("Ausencia antes del inicio")
    void ausenciaAntesDelInicio() {
        rechazo(() -> turno(EstadoTurno.RESERVADO).ausente(diciembre(1, 9, 50)),
                CodigoRechazo.AUSENCIA_ANTES_DE_TOLERANCIA);
    }

    @Test
    @DisplayName("Ausencia dentro de la tolerancia")
    void ausenciaDentroDeLaTolerancia() {
        rechazo(() -> turno(EstadoTurno.RESERVADO).ausente(diciembre(1, 10, 14)),
                CodigoRechazo.AUSENCIA_ANTES_DE_TOLERANCIA);
    }

    @Test
    @DisplayName("Ausencia al cumplirse la tolerancia")
    void ausenciaAlCumplirseLaTolerancia() {
        Turno confirmado = turno(EstadoTurno.CONFIRMADO);

        Turno ausente = confirmado.ausente(diciembre(1, 10, 15));

        assertThat(ausente.estado()).isEqualTo(EstadoTurno.AUSENTE);
        assertThat(ausente).usingRecursiveComparison().ignoringFields("estado").isEqualTo(confirmado);
    }

    @Test
    @DisplayName("Ausencia de un paciente que llegó")
    void ausenciaConLlegada() {
        Turno conLlegada = turno(EstadoTurno.RESERVADO).conLlegada(diciembre(1, 10, 5), BUENOS_AIRES);

        rechazo(() -> conLlegada.ausente(diciembre(1, 10, 20)), CodigoRechazo.LLEGADA_REGISTRADA);
    }

    @Test
    @DisplayName("Ausencia de un turno que no está activo")
    void ausenciaDeUnTurnoNoActivo() {
        rechazo(() -> turno(EstadoTurno.CANCELADO).ausente(diciembre(1, 10, 20)), CodigoRechazo.TURNO_NO_ACTIVO);
    }

    @Test
    @DisplayName("Llegada tarde: el turno sigue activo con la hora de llegada")
    void llegadaTarde() {
        Turno reservado = turno(EstadoTurno.RESERVADO);

        Turno conLlegada = reservado.conLlegada(diciembre(1, 10, 12), BUENOS_AIRES);

        assertThat(conLlegada.estado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(conLlegada.llegadaEn()).isEqualTo(diciembre(1, 10, 12));
        assertThat(conLlegada.esActivo()).isTrue();
        assertThat(reservado.llegadaEn()).isNull();
    }

    @Test
    @DisplayName("La llegada se puede registrar desde las 00:00 del día del turno")
    void llegadaDesdeElInicioDelDia() {
        assertThat(turno(EstadoTurno.RESERVADO).conLlegada(diciembre(1, 0, 0), BUENOS_AIRES).llegadaEn())
                .isEqualTo(diciembre(1, 0, 0));
    }

    @Test
    @DisplayName("Llegada antes del día del turno")
    void llegadaAnticipada() {
        rechazo(() -> turno(EstadoTurno.RESERVADO).conLlegada(diciembre(1, 0, 0).minusSeconds(1), BUENOS_AIRES),
                CodigoRechazo.LLEGADA_ANTICIPADA);
    }

    @Test
    @DisplayName("Segunda llegada")
    void segundaLlegada() {
        Turno conLlegada = turno(EstadoTurno.RESERVADO).conLlegada(diciembre(1, 10, 5), BUENOS_AIRES);

        rechazo(() -> conLlegada.conLlegada(diciembre(1, 10, 6), BUENOS_AIRES), CodigoRechazo.LLEGADA_REGISTRADA);
    }

    @Test
    @DisplayName("Llegada a un turno ausente")
    void llegadaAUnTurnoAusente() {
        rechazo(() -> turno(EstadoTurno.AUSENTE).conLlegada(diciembre(1, 10, 20), BUENOS_AIRES),
                CodigoRechazo.TURNO_NO_ACTIVO);
    }

    @Test
    @DisplayName("Atendido conserva la llegada y deja de ser activo")
    void atendido() {
        Turno atendido = turno(EstadoTurno.CONFIRMADO).conLlegada(diciembre(1, 10, 5), BUENOS_AIRES).atendido();

        assertThat(atendido.estado()).isEqualTo(EstadoTurno.ATENDIDO);
        assertThat(atendido.llegadaEn()).isEqualTo(diciembre(1, 10, 5));
        assertThat(atendido.esActivo()).isFalse();
    }

    @Test
    @DisplayName("Deshacer la ausencia vuelve al estado previo")
    void deshacerVuelveAlEstadoPrevio() {
        Turno ausente = turno(EstadoTurno.AUSENTE);

        assertThat(ausente.sinAusencia(EstadoTurno.CONFIRMADO, diciembre(1, 10, 22)).estado())
                .isEqualTo(EstadoTurno.CONFIRMADO);
        assertThat(ausente.sinAusencia(EstadoTurno.RESERVADO, diciembre(1, 10, 22)).estado())
                .isEqualTo(EstadoTurno.RESERVADO);
    }

    @Test
    @DisplayName("Deshacer un turno que no está ausente")
    void deshacerUnTurnoNoAusente() {
        rechazo(() -> turno(EstadoTurno.RESERVADO).sinAusencia(EstadoTurno.RESERVADO, diciembre(1, 10, 22)),
                CodigoRechazo.TURNO_NO_AUSENTE);
    }

    @Test
    @DisplayName("Deshacer después del fin")
    void deshacerDespuesDelFin() {
        rechazo(() -> turno(EstadoTurno.AUSENTE).sinAusencia(EstadoTurno.RESERVADO, diciembre(1, 10, 31)),
                CodigoRechazo.TURNO_TERMINADO);
        rechazo(() -> turno(EstadoTurno.AUSENTE).sinAusencia(EstadoTurno.RESERVADO, diciembre(1, 10, 30)),
                CodigoRechazo.TURNO_TERMINADO);
    }
}
