package ar.turnos.odontologia.reserva.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.agenda.domain.EstadoTurno;
import ar.turnos.odontologia.reserva.IntegracionReservaBase;
import ar.turnos.odontologia.reserva.application.ReservasPaciente;
import ar.turnos.odontologia.reserva.application.ReservasPaciente.TurnoDelPaciente;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Adaptador JDBC de solo lectura de los turnos del paciente, contra PostgreSQL real (design D10). */
@Tag("integration")
class ReservasPacienteJdbcIntegrationTest extends IntegracionReservaBase {

    @Autowired ReservasPaciente reservas;

    @Test
    @DisplayName("Un número de reserva de otro paciente devuelve vacío")
    void numeroDeOtroPacienteDevuelveVacio() {
        long ajeno = turnoActivo(pacienteDos, draA, sillon1, 2, 11, 0);

        assertThat(reservas.buscar(numeroDe(ajeno), pacienteUno)).isEmpty();
        assertThat(reservas.buscar(numeroDe(ajeno), pacienteDos)).isPresent();
    }

    @Test
    @DisplayName("Un número de reserva inexistente devuelve vacío")
    void numeroInexistenteDevuelveVacio() {
        assertThat(reservas.buscar("ZZZZ-ZZZZ", pacienteUno)).isEmpty();
    }

    @Test
    @DisplayName("La búsqueda por número trae el turno con la prestación y el profesional")
    void buscarTraeLaVista() {
        long propio = turnoActivo(pacienteUno, draA, sillon1, 2, 10, 0);

        TurnoDelPaciente turno = reservas.buscar(numeroDe(propio), pacienteUno).orElseThrow();

        assertThat(turno.turnoId()).isEqualTo(propio);
        assertThat(turno.numeroReserva()).isEqualTo(numeroDe(propio));
        assertThat(turno.estado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(turno.prestacionId()).isEqualTo(consulta);
        assertThat(turno.prestacionNombre()).isEqualTo("Consulta");
        assertThat(turno.profesionalId()).isEqualTo(draA);
        assertThat(turno.profesionalNombre()).isEqualTo("Ana Ejemplo");
        assertThat(turno.fin()).isEqualTo(turno.inicio().plusSeconds(30 * 60));
    }

    @Test
    @DisplayName("Los turnos del paciente salen en cualquier estado, ordenados por inicio, y sin los de otros")
    void turnosDelPaciente() {
        long tercero = turnoActivo(pacienteUno, draA, sillon1, 3, 10, 0);
        long primero = turnoCancelado(pacienteUno, draA, sillon1, 2, 10, 0);
        long segundo = turnoActivo(pacienteUno, drB, sillon2, 2, 15, 0);
        turnoActivo(pacienteDos, draA, sillon1, 2, 11, 0);

        List<TurnoDelPaciente> turnos = reservas.turnosDe(pacienteUno);

        assertThat(turnos).extracting(TurnoDelPaciente::turnoId).containsExactly(primero, segundo, tercero);
        assertThat(turnos).extracting(TurnoDelPaciente::estado)
                .containsExactly(EstadoTurno.CANCELADO, EstadoTurno.RESERVADO, EstadoTurno.RESERVADO);
        assertThat(turnos.get(0).inicio()).isBefore(Instant.parse("2026-12-03T00:00:00Z"));
    }

    @Test
    @DisplayName("El DNI es el del paciente indicado")
    void dniDelPaciente() {
        assertThat(reservas.dni(pacienteUno)).hasValue(DNI_UNO);
        assertThat(reservas.dni(999_999L)).isEmpty();
    }
}
