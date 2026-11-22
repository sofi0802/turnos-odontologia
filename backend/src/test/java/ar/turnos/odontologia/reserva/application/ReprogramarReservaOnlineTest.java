package ar.turnos.odontologia.reserva.application;

import static ar.turnos.odontologia.reserva.application.EscenarioReserva.ACTOR_UNO;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.BA;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.CUENTA_UNO;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.DRA_A;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.DR_B;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.PACIENTE_DOS;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.PACIENTE_UNO;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.SILLON_1;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.SILLON_2;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.en;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ar.turnos.odontologia.agenda.application.ReprogramarTurnoCommand;
import ar.turnos.odontologia.agenda.domain.CodigoRechazo;
import ar.turnos.odontologia.agenda.domain.EstadoTurno;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.reserva.application.ReservasPaciente.TurnoDelPaciente;
import ar.turnos.odontologia.reserva.domain.ReservaRechazadaException;
import java.time.Instant;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReprogramarReservaOnlineTest {

    private static final String NUMERO_UNO = "KKKK-KKKK";

    private final EscenarioReserva e = new EscenarioReserva();
    private final ReprogramarReservaOnline reprogramar = new ReprogramarReservaOnline(e.catalogo, e.reservas,
            e.buscador, e.agenda, e.aviso, e.reloj, BA, 3);

    @BeforeEach
    void franjasDeLosDias() {
        // lunes a viernes: la Dra. A de 08:00 a 13:00 y el Dr. B de 10:00 a 18:00
        for (int dia = 1; dia <= 5; dia++) {
            e.periodos.franja(DRA_A, ar.turnos.odontologia.agenda.domain.reglas.FranjaHoraria.de(dia,
                    LocalTime.of(8, 0), LocalTime.of(13, 0)));
            e.periodos.franja(DR_B, ar.turnos.odontologia.agenda.domain.reglas.FranjaHoraria.de(dia,
                    LocalTime.of(10, 0), LocalTime.of(18, 0)));
        }
    }

    private Turno turnoDeUno(int dia, int hora, int minuto, long profesional, long sillon) {
        return e.turnoConNumero(PACIENTE_UNO, profesional, sillon, dia, hora, minuto, EstadoTurno.RESERVADO,
                "KKKKKKKK");
    }

    private TurnoDelPaciente reprogramar(String numero, Instant inicio, Long profesional) {
        return reprogramar.ejecutar(PACIENTE_UNO, CUENTA_UNO, numero, inicio, profesional);
    }

    private ReservaRechazadaException rechazo(String numero, Instant inicio, Long profesional) {
        Throwable error = catchThrowable(() -> reprogramar(numero, inicio, profesional));
        assertThat(error).isInstanceOf(ReservaRechazadaException.class);
        return (ReservaRechazadaException) error;
    }

    @Test
    @DisplayName("Reprogramación a otro horario: compone la agenda con el DNI de la sesión y avisa")
    void aOtroHorario() {
        Turno original = turnoDeUno(2, 10, 0, DRA_A, SILLON_1);

        TurnoDelPaciente nuevo = reprogramar(NUMERO_UNO, en(2, 11, 0), DRA_A);

        assertThat(e.agenda.reprogramaciones).containsExactly(new ReprogramarTurnoCommand(original.id(), "99000001",
                NUMERO_UNO, en(2, 11, 0), DRA_A, SILLON_1));
        assertThat(e.agenda.actores).containsExactly(ACTOR_UNO);
        assertThat(nuevo.estado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(nuevo.inicio()).isEqualTo(en(2, 11, 0));
        assertThat(nuevo.fin()).isEqualTo(en(2, 11, 30));
        assertThat(nuevo.numeroReserva()).isNotEqualTo(NUMERO_UNO);
        assertThat(e.turnos.porId(original.id()).estado()).isEqualTo(EstadoTurno.CANCELADO);
        assertThat(e.aviso.llamadas).containsExactly("reprogramado:" + nuevo.turnoId());
    }

    @Test
    @DisplayName("Sobre el propio horario el original no cuenta como conflicto")
    void sobreElPropioHorario() {
        turnoDeUno(2, 10, 0, DRA_A, SILLON_1);

        TurnoDelPaciente nuevo = reprogramar(NUMERO_UNO, en(2, 10, 15), DRA_A);

        assertThat(nuevo.inicio()).isEqualTo(en(2, 10, 15));
        assertThat(nuevo.fin()).isEqualTo(en(2, 10, 45));
    }

    @Test
    @DisplayName("Sin preferencia se asigna con la misma regla y sin contar el turno original")
    void sinPreferencia() {
        turnoDeUno(2, 10, 0, DRA_A, SILLON_1);
        e.turno(PACIENTE_DOS, DRA_A, SILLON_1, 2, 8, 0, EstadoTurno.RESERVADO);

        TurnoDelPaciente nuevo = reprogramar(NUMERO_UNO, en(2, 11, 0), null);

        // la Dra. A tiene 1 turno sin el original y el Dr. B ninguno
        assertThat(nuevo.profesionalId()).isEqualTo(DR_B);
        assertThat(e.agenda.reprogramaciones.get(0).sillonId()).isEqualTo(SILLON_2);
    }

    @Test
    @DisplayName("Sin preferencia el turno original no se cuenta: con un solo turno propio hay empate")
    void elOriginalNoSeCuenta() {
        turnoDeUno(2, 10, 0, DRA_A, SILLON_1);
        e.desempate.ultimo = false;

        TurnoDelPaciente nuevo = reprogramar(NUMERO_UNO, en(2, 11, 0), null);

        // sin el original los dos profesionales tienen cero turnos: decide el desempate (el primero)
        assertThat(nuevo.profesionalId()).isEqualTo(DRA_A);
    }

    @Test
    @DisplayName("Un horario no disponible responde SIN_DISPONIBILIDAD y no toca la agenda")
    void horarioNoDisponible() {
        turnoDeUno(2, 10, 0, DRA_A, SILLON_1);
        e.turno(PACIENTE_DOS, DRA_A, SILLON_1, 2, 11, 0, EstadoTurno.RESERVADO);

        ReservaRechazadaException rechazo = rechazo(NUMERO_UNO, en(2, 11, 0), DRA_A);

        assertThat(rechazo.codigo()).isEqualTo("SIN_DISPONIBILIDAD");
        assertThat(e.agenda.reprogramaciones).isEmpty();
        assertThat(e.aviso.llamadas).isEmpty();
    }

    @Test
    @DisplayName("A 23 horas está rechazado y no toca la agenda")
    void fueraDePlazo() {
        turnoDeUno(2, 10, 0, DRA_A, SILLON_1);
        e.reloj.fijarEn(1, 11, 0);

        assertThat(rechazo(NUMERO_UNO, en(3, 10, 0), DRA_A).codigo()).isEqualTo("CANCELACION_FUERA_DE_PLAZO");
        assertThat(e.agenda.reprogramaciones).isEmpty();
    }

    @Test
    @DisplayName("Un número ajeno o inexistente responde RESERVA_INEXISTENTE")
    void ajenoOInexistente() {
        e.turnoConNumero(PACIENTE_DOS, DRA_A, SILLON_1, 2, 11, 0, EstadoTurno.RESERVADO, "MMMMMMMM");

        assertThat(rechazo("MMMM-MMMM", en(3, 10, 0), null).codigo()).isEqualTo("RESERVA_INEXISTENTE");
        assertThat(rechazo("ZZZZ-ZZZZ", en(3, 10, 0), null).codigo()).isEqualTo("RESERVA_INEXISTENTE");
    }

    @Test
    @DisplayName("Un turno propio cancelado responde TURNO_NO_ACTIVO")
    void turnoNoActivo() {
        e.turnoConNumero(PACIENTE_UNO, DRA_A, SILLON_1, 2, 10, 0, EstadoTurno.CANCELADO, "KKKKKKKK");

        assertThat(rechazo(NUMERO_UNO, en(3, 10, 0), DRA_A).codigo()).isEqualTo("TURNO_NO_ACTIVO");
    }

    @Test
    @DisplayName("Si pierde una carrera prueba otra combinación y mantiene el original")
    void carrera() {
        turnoDeUno(2, 10, 0, DRA_A, SILLON_1);
        e.agenda.rechazarProximo(CodigoRechazo.SOLAPAMIENTO_SILLON);

        TurnoDelPaciente nuevo = reprogramar(NUMERO_UNO, en(2, 11, 0), DRA_A);

        assertThat(e.agenda.reprogramaciones).hasSize(2);
        assertThat(e.agenda.reprogramaciones.get(1).sillonId()).isEqualTo(SILLON_2);
        assertThat(nuevo.profesionalId()).isEqualTo(DRA_A);
    }

    @Test
    @DisplayName("Un profesional elegido no habilitado o inexistente se rechaza antes de tocar la agenda")
    void profesionalNoHabilitado() {
        turnoDeUno(2, 10, 0, DRA_A, SILLON_1);
        e.catalogo.profesional(99L, "Cecilia Falsa", true, null);

        assertThat(rechazo(NUMERO_UNO, en(2, 11, 0), 99L).codigo()).isEqualTo("PROFESIONAL_NO_HABILITADO");
        assertThat(e.agenda.reprogramaciones).isEmpty();
    }
}
