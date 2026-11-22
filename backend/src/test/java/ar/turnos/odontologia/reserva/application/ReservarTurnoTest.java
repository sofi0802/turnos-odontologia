package ar.turnos.odontologia.reserva.application;

import static ar.turnos.odontologia.reserva.application.EscenarioReserva.ACTOR_UNO;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.BA;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.CONDUCTO;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.CONSULTA;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.CUENTA_UNO;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.DRA_A;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.DR_B;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.PACIENTE_DOS;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.PACIENTE_UNO;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.SILLON_1;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.SILLON_2;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.hora;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnos.odontologia.agenda.application.CrearTurnoCommand;
import ar.turnos.odontologia.agenda.domain.CodigoRechazo;
import ar.turnos.odontologia.agenda.domain.EstadoTurno;
import ar.turnos.odontologia.agenda.domain.ReferenciaInexistenteException;
import ar.turnos.odontologia.reserva.application.ReservasPaciente.TurnoDelPaciente;
import ar.turnos.odontologia.reserva.domain.ReservaRechazadaException;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReservarTurnoTest {

    private final EscenarioReserva e = new EscenarioReserva();
    private final ReservarTurno reservar = new ReservarTurno(e.catalogo, e.buscador, e.agenda, e.aviso, e.reloj, BA, 3);

    @BeforeEach
    void franjasDelMartes() {
        e.martes(DRA_A, 8, 13);
        e.martes(DR_B, 10, 18);
    }

    private TurnoDelPaciente reservar(long prestacion, Long profesional, Instant inicio) {
        return reservar.ejecutar(PACIENTE_UNO, CUENTA_UNO, prestacion, profesional, inicio);
    }

    private String codigoDelRechazo(Long prestacion, Long profesional, Instant inicio) {
        Throwable error = org.assertj.core.api.Assertions.catchThrowable(() -> reservar(prestacion, profesional, inicio));
        assertThat(error).isInstanceOf(ReservaRechazadaException.class);
        return ((ReservaRechazadaException) error).codigo();
    }

    // ---------- asignación del profesional (RN-RO-05) ----------

    @Test
    @DisplayName("Sin preferencia con dos profesionales libres")
    void sinPreferenciaConDosLibres() {
        e.turno(PACIENTE_DOS, DRA_A, SILLON_1, 8, 0);
        e.turno(PACIENTE_DOS, DRA_A, SILLON_1, 9, 0);
        e.turno(PACIENTE_DOS, DR_B, SILLON_2, 14, 0);

        TurnoDelPaciente reserva = reservar(CONSULTA, null, hora(11, 0));

        assertThat(reserva.profesionalId()).isEqualTo(DR_B);
        assertThat(reserva.profesionalNombre()).isEqualTo("Bruno Prueba");
    }

    @Test
    @DisplayName("Empate resuelto al azar: el desempate elige el último")
    void empateElUltimo() {
        e.turno(PACIENTE_DOS, DRA_A, SILLON_1, 8, 0);
        e.turno(PACIENTE_DOS, DR_B, SILLON_2, 14, 0);
        e.desempate.ultimo = true;

        assertThat(reservar(CONSULTA, null, hora(11, 0)).profesionalId()).isEqualTo(DR_B);
    }

    @Test
    @DisplayName("Empate resuelto al azar: el desempate elige el primero con una agenda igual")
    void empateElPrimero() {
        e.turno(PACIENTE_DOS, DRA_A, SILLON_1, 8, 0);
        e.turno(PACIENTE_DOS, DR_B, SILLON_2, 14, 0);
        e.desempate.ultimo = false;

        assertThat(reservar(CONSULTA, null, hora(11, 0)).profesionalId()).isEqualTo(DRA_A);
    }

    @Test
    @DisplayName("Profesional no habilitado excluido de la asignación")
    void noHabilitadoExcluido() {
        e.turno(PACIENTE_DOS, DR_B, SILLON_2, 14, 0);
        e.turno(PACIENTE_DOS, DR_B, SILLON_2, 15, 0);
        e.turno(PACIENTE_DOS, DR_B, SILLON_2, 16, 0);

        assertThat(reservar(CONDUCTO, null, hora(11, 0)).profesionalId()).isEqualTo(DR_B);
    }

    @Test
    @DisplayName("Solo se asigna un profesional libre")
    void soloUnProfesionalLibre() {
        e.turno(PACIENTE_DOS, DRA_A, SILLON_1, 11, 0);
        e.turno(PACIENTE_DOS, DR_B, SILLON_2, 14, 0);
        e.turno(PACIENTE_DOS, DR_B, SILLON_2, 15, 0);
        e.turno(PACIENTE_DOS, DR_B, SILLON_2, 16, 0);

        assertThat(reservar(CONSULTA, null, hora(11, 0)).profesionalId()).isEqualTo(DR_B);
    }

    @Test
    @DisplayName("Los turnos cancelados no cuentan")
    void canceladosNoCuentan() {
        e.cancelado(PACIENTE_DOS, DRA_A, SILLON_1, 8, 0);
        e.cancelado(PACIENTE_DOS, DRA_A, SILLON_1, 9, 0);
        e.cancelado(PACIENTE_DOS, DRA_A, SILLON_1, 12, 0);
        e.turno(PACIENTE_DOS, DR_B, SILLON_2, 14, 0);

        assertThat(reservar(CONSULTA, null, hora(11, 0)).profesionalId()).isEqualTo(DRA_A);
    }

    // ---------- asignación del sillón (RN-RO-06) y datos que se mandan a la agenda ----------

    @Test
    @DisplayName("La reserva usa el sillón habitual, el paciente de la sesión y el actor online")
    void comandoEnviadoALaAgenda() {
        TurnoDelPaciente reserva = reservar(CONSULTA, DR_B, hora(10, 0));

        assertThat(e.agenda.creaciones).containsExactly(
                new CrearTurnoCommand(PACIENTE_UNO, DR_B, SILLON_2, CONSULTA, hora(10, 0)));
        assertThat(e.agenda.actores).containsExactly(ACTOR_UNO);
        assertThat(reserva.estado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(reserva.fin()).isEqualTo(hora(10, 30));
        assertThat(reserva.prestacionNombre()).isEqualTo("Consulta");
    }

    @Test
    @DisplayName("Con el sillón habitual ocupado usa el primer sillón libre")
    void habitualOcupado() {
        e.turno(PACIENTE_DOS, DR_B, SILLON_1, 10, 0);

        reservar(CONSULTA, DRA_A, hora(10, 0));

        assertThat(e.agenda.creaciones.get(0).sillonId()).isEqualTo(SILLON_2);
    }

    // ---------- carreras: otra combinación antes de rechazar ----------

    @Test
    @DisplayName("Si pierde una carrera por el sillón prueba la siguiente combinación")
    void reintentaConLaSiguienteCombinacion() {
        e.agenda.rechazarProximo(CodigoRechazo.SOLAPAMIENTO_SILLON);

        TurnoDelPaciente reserva = reservar(CONSULTA, DRA_A, hora(10, 0));

        assertThat(e.agenda.creaciones).hasSize(2);
        assertThat(e.agenda.creaciones.get(0).sillonId()).isEqualTo(SILLON_1);
        assertThat(e.agenda.creaciones.get(1).sillonId()).isEqualTo(SILLON_2);
        assertThat(reserva.profesionalId()).isEqualTo(DRA_A);
        assertThat(e.turnos.activos()).isEqualTo(1);
    }

    @Test
    @DisplayName("Cuando se agotan las combinaciones responde SIN_DISPONIBILIDAD con alternativas")
    void seAgotanLasCombinaciones() {
        e.agenda.rechazarProximo(CodigoRechazo.SOLAPAMIENTO_PROFESIONAL);
        e.agenda.rechazarProximo(CodigoRechazo.MARGEN_INSUFICIENTE);

        Throwable error = org.assertj.core.api.Assertions.catchThrowable(() -> reservar(CONSULTA, DRA_A, hora(10, 0)));

        assertThat(error).isInstanceOf(ReservaRechazadaException.class);
        ReservaRechazadaException rechazo = (ReservaRechazadaException) error;
        assertThat(rechazo.codigo()).isEqualTo("SIN_DISPONIBILIDAD");
        assertThat(rechazo.alternativas()).hasSize(3);
        assertThat(e.agenda.creaciones).hasSize(2);
    }

    @Test
    @DisplayName("Sin preferencia, tras perder con un profesional prueba con el otro")
    void sinPreferenciaProbandoElOtroProfesional() {
        // otro paciente gana la carrera por la Dra. A a las 11:00: su turno aparece cuando la agenda rechaza el mío
        e.agenda.rechazarProximo(CodigoRechazo.SOLAPAMIENTO_PROFESIONAL,
                () -> e.turno(PACIENTE_DOS, DRA_A, SILLON_1, 11, 0));

        TurnoDelPaciente reserva = reservar(CONSULTA, null, hora(11, 0));

        assertThat(reserva.profesionalId()).isEqualTo(DR_B);
    }

    // ---------- horario no disponible ----------

    @Test
    @DisplayName("Un horario ocupado responde SIN_DISPONIBILIDAD con hasta tres alternativas posteriores")
    void horarioOcupadoConAlternativas() {
        e.turno(PACIENTE_DOS, DRA_A, SILLON_1, 10, 0);

        Throwable error = org.assertj.core.api.Assertions.catchThrowable(() -> reservar(CONSULTA, DRA_A, hora(10, 15)));

        ReservaRechazadaException rechazo = (ReservaRechazadaException) error;
        assertThat(rechazo.codigo()).isEqualTo("SIN_DISPONIBILIDAD");
        assertThat(rechazo.alternativas()).extracting(h -> h.inicio().atZone(BA).toLocalTime())
                .containsExactly(LocalTime.of(10, 45), LocalTime.of(11, 0), LocalTime.of(11, 15));
        assertThat(e.agenda.creaciones).isEmpty();
    }

    @Test
    @DisplayName("Fuera de la franja, fuera de la grilla o sin profesional libre responde SIN_DISPONIBILIDAD")
    void otrosHorariosNoDisponibles() {
        assertThat(codigoDelRechazo(CONSULTA, DRA_A, hora(13, 0))).isEqualTo("SIN_DISPONIBILIDAD");
        assertThat(codigoDelRechazo(CONSULTA, DRA_A, hora(10, 5))).isEqualTo("SIN_DISPONIBILIDAD");
        e.turno(PACIENTE_DOS, DRA_A, SILLON_1, 11, 0);
        e.turno(PACIENTE_DOS, DR_B, SILLON_2, 11, 0);
        assertThat(codigoDelRechazo(CONSULTA, null, hora(11, 0))).isEqualTo("SIN_DISPONIBILIDAD");
        assertThat(e.agenda.creaciones).isEmpty();
    }

    // ---------- rechazos de negocio y validación ----------

    @Test
    @DisplayName("Reserva en el pasado")
    void reservaEnElPasado() {
        e.reloj.fijarEn(1, 10, 20);

        assertThat(codigoDelRechazo(CONSULTA, DRA_A, hora(10, 0))).isEqualTo("INICIO_EN_EL_PASADO");
        assertThat(e.agenda.creaciones).isEmpty();
    }

    @Test
    @DisplayName("Profesional elegido no habilitado")
    void profesionalNoHabilitado() {
        assertThat(codigoDelRechazo(CONDUCTO, DRA_A, hora(10, 0))).isEqualTo("PROFESIONAL_NO_HABILITADO");
        assertThat(e.agenda.creaciones).isEmpty();
    }

    @Test
    @DisplayName("Prestación inactiva y profesional inactivo")
    void recursosInactivos() {
        e.catalogo.prestacion(CONSULTA, "Consulta", 30, false);
        assertThat(codigoDelRechazo(CONSULTA, DRA_A, hora(10, 0))).isEqualTo("RECURSO_INACTIVO");

        e.catalogo.prestacion(CONSULTA, "Consulta", 30, true);
        e.catalogo.profesional(DRA_A, "Ana Ejemplo", false, SILLON_1);
        assertThat(codigoDelRechazo(CONSULTA, DRA_A, hora(10, 0))).isEqualTo("RECURSO_INACTIVO");
    }

    @Test
    @DisplayName("Prestación o profesional inexistentes son datos inválidos")
    void referenciasInexistentes() {
        assertThatThrownBy(() -> reservar(999L, DRA_A, hora(10, 0)))
                .isInstanceOfSatisfying(ReferenciaInexistenteException.class,
                        ex -> assertThat(ex.campo()).isEqualTo("prestacionId"));
        assertThatThrownBy(() -> reservar(CONSULTA, 999L, hora(10, 0)))
                .isInstanceOfSatisfying(ReferenciaInexistenteException.class,
                        ex -> assertThat(ex.campo()).isEqualTo("profesionalId"));
    }

    // ---------- traducción de rechazos de la agenda (design D7) ----------

    @Test
    @DisplayName("Los códigos de disponibilidad de la agenda salen como SIN_DISPONIBILIDAD")
    void disponibilidadSeTraduce() {
        for (CodigoRechazo codigo : List.of(CodigoRechazo.PROFESIONAL_AUSENTE, CodigoRechazo.AGENDA_BLOQUEADA,
                CodigoRechazo.FUERA_DE_HORARIO, CodigoRechazo.SIN_SILLON_DISPONIBLE)) {
            e.agenda.rechazarProximo(codigo);
            assertThat(codigoDelRechazo(CONSULTA, DRA_A, hora(10, 0))).as(codigo.name())
                    .isEqualTo("SIN_DISPONIBILIDAD");
        }
    }

    @Test
    @DisplayName("INICIO_EN_EL_PASADO, PROFESIONAL_NO_HABILITADO y RECURSO_INACTIVO de la agenda salen tal cual")
    void codigosQueSalenTalCual() {
        for (CodigoRechazo codigo : List.of(CodigoRechazo.INICIO_EN_EL_PASADO, CodigoRechazo.PROFESIONAL_NO_HABILITADO,
                CodigoRechazo.RECURSO_INACTIVO)) {
            e.agenda.rechazarProximo(codigo);
            assertThat(codigoDelRechazo(CONSULTA, DRA_A, hora(10, 0))).isEqualTo(codigo.name());
        }
    }
}
