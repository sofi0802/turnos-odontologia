package ar.turnos.odontologia.reserva.application;

import static ar.turnos.odontologia.reserva.application.EscenarioReserva.ACTOR_UNO;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.DRA_A;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.PACIENTE_DOS;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.PACIENTE_UNO;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.SILLON_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ar.turnos.odontologia.agenda.application.CancelarTurnoCommand;
import ar.turnos.odontologia.agenda.domain.EstadoTurno;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.reserva.application.ReservasPaciente.TurnoDelPaciente;
import ar.turnos.odontologia.reserva.domain.ReservaRechazadaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CancelarReservaOnlineTest {

    private static final String NUMERO_UNO = "KKKK-KKKK";

    private final EscenarioReserva e = new EscenarioReserva();
    private final CancelarReservaOnline cancelar = new CancelarReservaOnline(e.reservas, e.agenda, e.aviso, e.reloj);

    private Turno turnoDeUnoMañana(int hora) {
        return e.turnoConNumero(PACIENTE_UNO, DRA_A, SILLON_1, 2, hora, 0, EstadoTurno.RESERVADO, "KKKKKKKK");
    }

    private String codigoDelRechazo(String numero) {
        Throwable error = catchThrowable(() -> cancelar.ejecutar(PACIENTE_UNO, EscenarioReserva.CUENTA_UNO, numero));
        assertThat(error).isInstanceOf(ReservaRechazadaException.class);
        return ((ReservaRechazadaException) error).codigo();
    }

    @Test
    @DisplayName("Cancela con el DNI del paciente de la sesión, el actor online y avisa")
    void cancelaConAnticipacion() {
        Turno turno = turnoDeUnoMañana(10);

        TurnoDelPaciente cancelado = cancelar.ejecutar(PACIENTE_UNO, EscenarioReserva.CUENTA_UNO, NUMERO_UNO);

        assertThat(cancelado.estado()).isEqualTo(EstadoTurno.CANCELADO);
        assertThat(cancelado.numeroReserva()).isEqualTo(NUMERO_UNO);
        assertThat(cancelado.profesionalNombre()).isEqualTo("Ana Ejemplo");
        assertThat(e.agenda.cancelaciones).containsExactly(new CancelarTurnoCommand(turno.id(), "99000001", NUMERO_UNO));
        assertThat(e.agenda.actores).containsExactly(ACTOR_UNO);
        assertThat(e.aviso.llamadas).containsExactly("cancelado:" + turno.id());
    }

    @Test
    @DisplayName("El número se normaliza: mayúsculas y sin espacios en los bordes")
    void normalizaElNumero() {
        turnoDeUnoMañana(10);

        TurnoDelPaciente cancelado = cancelar.ejecutar(PACIENTE_UNO, EscenarioReserva.CUENTA_UNO, "  kkkk-kkkk ");

        assertThat(cancelado.estado()).isEqualTo(EstadoTurno.CANCELADO);
    }

    @Test
    @DisplayName("Exactamente 24 horas antes está permitido")
    void exactas24Horas() {
        turnoDeUnoMañana(10);
        e.reloj.fijarEn(1, 10, 0);

        assertThat(cancelar.ejecutar(PACIENTE_UNO, EscenarioReserva.CUENTA_UNO, NUMERO_UNO).estado())
                .isEqualTo(EstadoTurno.CANCELADO);
    }

    @Test
    @DisplayName("A 23 horas está rechazado y no toca la agenda")
    void fueraDePlazo() {
        turnoDeUnoMañana(10);
        e.reloj.fijarEn(1, 11, 0);

        assertThat(codigoDelRechazo(NUMERO_UNO)).isEqualTo("CANCELACION_FUERA_DE_PLAZO");
        assertThat(e.agenda.cancelaciones).isEmpty();
        assertThat(e.aviso.llamadas).isEmpty();
    }

    @Test
    @DisplayName("Un número de otro paciente o inexistente responde lo mismo: RESERVA_INEXISTENTE")
    void ajenoOInexistente() {
        e.turnoConNumero(PACIENTE_DOS, DRA_A, SILLON_1, 2, 11, 0, EstadoTurno.RESERVADO, "MMMMMMMM");

        assertThat(codigoDelRechazo("MMMM-MMMM")).isEqualTo("RESERVA_INEXISTENTE");
        assertThat(codigoDelRechazo("ZZZZ-ZZZZ")).isEqualTo("RESERVA_INEXISTENTE");
        assertThat(e.agenda.cancelaciones).isEmpty();
    }

    @Test
    @DisplayName("Un turno propio que no está activo responde TURNO_NO_ACTIVO, antes que el plazo")
    void turnoNoActivo() {
        e.turnoConNumero(PACIENTE_UNO, DRA_A, SILLON_1, 1, 9, 0, EstadoTurno.CANCELADO, "KKKKKKKK");

        assertThat(codigoDelRechazo(NUMERO_UNO)).isEqualTo("TURNO_NO_ACTIVO");
        assertThat(e.agenda.cancelaciones).isEmpty();
    }
}
