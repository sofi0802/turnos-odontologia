package ar.turnos.odontologia.reserva.application;

import static ar.turnos.odontologia.reserva.application.EscenarioReserva.BA;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.CONSULTA;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.CUENTA_UNO;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.DRA_A;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.DR_B;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.PACIENTE_DOS;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.PACIENTE_UNO;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.SILLON_1;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.en;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ar.turnos.odontologia.agenda.domain.EstadoTurno;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.reserva.application.ReservasPaciente.TurnoDelPaciente;
import ar.turnos.odontologia.reserva.domain.ReservaRechazadaException;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** El aviso de reserva y cancelación (design D9): solo con la operación confirmada y sin depender de él. */
class AvisoReservaOnlineTest {

    private static final String NUMERO_UNO = "KKKK-KKKK";

    private final EscenarioReserva e = new EscenarioReserva();
    private final ReservarTurno reservar = new ReservarTurno(e.catalogo, e.buscador, e.agenda, e.aviso, e.reloj, BA, 3);
    private final CancelarReservaOnline cancelar = new CancelarReservaOnline(e.reservas, e.agenda, e.aviso, e.reloj);
    private final ReprogramarReservaOnline reprogramar = new ReprogramarReservaOnline(e.catalogo, e.reservas,
            e.buscador, e.agenda, e.aviso, e.reloj, BA, 3);

    private Logger logDelAviso;
    private ListAppender<ILoggingEvent> registros;

    @BeforeEach
    void escucharElLog() {
        for (int dia = 1; dia <= 5; dia++) {
            e.periodos.franja(DRA_A, ar.turnos.odontologia.agenda.domain.reglas.FranjaHoraria.de(dia,
                    LocalTime.of(8, 0), LocalTime.of(13, 0)));
            e.periodos.franja(DR_B, ar.turnos.odontologia.agenda.domain.reglas.FranjaHoraria.de(dia,
                    LocalTime.of(10, 0), LocalTime.of(18, 0)));
        }
        logDelAviso = (Logger) LoggerFactory.getLogger(AvisoReservaOnlineInactivo.class);
        registros = new ListAppender<>();
        registros.start();
        logDelAviso.addAppender(registros);
    }

    @AfterEach
    void dejarDeEscuchar() {
        logDelAviso.detachAppender(registros);
    }

    private Turno turnoDeUno() {
        return e.turnoConNumero(PACIENTE_UNO, DRA_A, SILLON_1, 2, 10, 0, EstadoTurno.RESERVADO, "KKKKKKKK");
    }

    @Test
    @DisplayName("Reservar avisa con el id del turno una vez confirmada")
    void reservarAvisaConElId() {
        TurnoDelPaciente reserva = reservar.ejecutar(PACIENTE_UNO, CUENTA_UNO, CONSULTA, DRA_A, en(2, 10, 0));

        assertThat(e.aviso.llamadas).containsExactly("reservado:" + reserva.turnoId());
    }

    @Test
    @DisplayName("Cancelar avisa con el id del turno cancelado")
    void cancelarAvisaConElId() {
        Turno turno = turnoDeUno();

        cancelar.ejecutar(PACIENTE_UNO, CUENTA_UNO, NUMERO_UNO);

        assertThat(e.aviso.llamadas).containsExactly("cancelado:" + turno.id());
    }

    @Test
    @DisplayName("Reprogramar avisa con el id del turno nuevo")
    void reprogramarAvisaConElIdNuevo() {
        turnoDeUno();

        TurnoDelPaciente nuevo = reprogramar.ejecutar(PACIENTE_UNO, CUENTA_UNO, NUMERO_UNO, en(2, 11, 0), DRA_A);

        assertThat(e.aviso.llamadas).containsExactly("reprogramado:" + nuevo.turnoId());
    }

    @Test
    @DisplayName("No avisa cuando la operación se rechaza")
    void noAvisaSiSeRechaza() {
        turnoDeUno();
        e.turno(PACIENTE_DOS, DRA_A, SILLON_1, 2, 11, 0, EstadoTurno.RESERVADO);

        Throwable reserva = catchThrowable(() -> reservar.ejecutar(PACIENTE_UNO, CUENTA_UNO, CONSULTA, DRA_A,
                en(2, 11, 0)));
        Throwable reprogramacion = catchThrowable(() -> reprogramar.ejecutar(PACIENTE_UNO, CUENTA_UNO, NUMERO_UNO,
                en(2, 11, 0), DRA_A));
        e.reloj.fijarEn(1, 11, 0);
        Throwable cancelacion = catchThrowable(() -> cancelar.ejecutar(PACIENTE_UNO, CUENTA_UNO, NUMERO_UNO));

        assertThat(List.of(reserva, reprogramacion, cancelacion)).allSatisfy(error -> assertThat(error)
                .isInstanceOf(ReservaRechazadaException.class));
        assertThat(e.aviso.llamadas).isEmpty();
    }

    @Test
    @DisplayName("Una excepción del aviso no deshace la reserva")
    void fallaDelAvisoNoDeshaceLaReserva() {
        e.aviso.fallar = true;

        TurnoDelPaciente reserva = reservar.ejecutar(PACIENTE_UNO, CUENTA_UNO, CONSULTA, DRA_A, en(2, 10, 0));

        assertThat(reserva.estado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(e.turnos.activos()).isEqualTo(1);
        assertThat(e.aviso.llamadas).hasSize(1);
    }

    @Test
    @DisplayName("Una excepción del aviso no deshace la cancelación ni la reprogramación")
    void fallaDelAvisoNoDeshaceCancelarNiReprogramar() {
        Turno turno = turnoDeUno();
        e.aviso.fallar = true;

        TurnoDelPaciente nuevo = reprogramar.ejecutar(PACIENTE_UNO, CUENTA_UNO, NUMERO_UNO, en(2, 11, 0), DRA_A);
        assertThat(nuevo.estado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(e.turnos.porId(turno.id()).estado()).isEqualTo(EstadoTurno.CANCELADO);

        TurnoDelPaciente cancelado = cancelar.ejecutar(PACIENTE_UNO, CUENTA_UNO, nuevo.numeroReserva());
        assertThat(cancelado.estado()).isEqualTo(EstadoTurno.CANCELADO);
    }

    @Test
    @DisplayName("El aviso inactivo deja en el log solo el id del turno")
    void elLogDelAvisoInactivoNoTieneDatosPersonales() {
        AvisoReservaOnline inactivo = new AvisoReservaOnlineInactivo();

        inactivo.reservado(42L);
        inactivo.cancelado(43L);
        inactivo.reprogramado(44L);

        assertThat(registros.list).hasSize(3);
        assertThat(registros.list).extracting(ILoggingEvent::getFormattedMessage)
                .allSatisfy(mensaje -> assertThat(mensaje).matches(".*\\b4[234]\\b.*")
                        .doesNotContain("Paciente").doesNotContain("99000001").doesNotContain("@")
                        .doesNotContain("KKKK"));
    }
}
