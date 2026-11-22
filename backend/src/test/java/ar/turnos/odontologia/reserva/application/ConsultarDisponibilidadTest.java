package ar.turnos.odontologia.reserva.application;

import static ar.turnos.odontologia.reserva.application.EscenarioReserva.BA;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.CONDUCTO;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.CONSULTA;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.DRA_A;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.DR_B;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.SILLON_1;
import static ar.turnos.odontologia.reserva.application.EscenarioReserva.hora;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnos.odontologia.agenda.domain.Intervalo;
import ar.turnos.odontologia.agenda.domain.ReferenciaInexistenteException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConsultarDisponibilidadTest {

    private static final LocalDate MARTES = LocalDate.of(2026, 12, 1);

    private final EscenarioReserva e = new EscenarioReserva();
    private final ConsultarDisponibilidad consulta = new ConsultarDisponibilidad(e.catalogo, e.buscador);

    private List<String> inicios(Long prestacion, Long profesional) {
        return consulta.ejecutar(prestacion, MARTES, profesional).stream()
                .map(h -> h.inicio().atZone(BA).toLocalTime().toString()).toList();
    }

    @Test
    @DisplayName("Unión sin horarios repetidos")
    void unionSinRepetidos() {
        e.martes(DRA_A, LocalTime.of(10, 0), LocalTime.of(11, 0));
        e.martes(DR_B, LocalTime.of(10, 30), LocalTime.of(11, 30));

        assertThat(inicios(CONSULTA, null)).containsExactly("10:00", "10:15", "10:30", "10:45", "11:00");
    }

    @Test
    @DisplayName("Cada horario trae inicio y fin con la duración de la prestación")
    void inicioYFin() {
        e.martes(DRA_A, 10, 11);

        List<Intervalo> horarios = consulta.ejecutar(CONSULTA, MARTES, DRA_A);

        assertThat(horarios).hasSize(3);
        assertThat(horarios.get(2).inicio()).isEqualTo(hora(10, 30));
        assertThat(horarios.get(2).fin()).isEqualTo(hora(11, 0));
    }

    @Test
    @DisplayName("Profesional no habilitado excluido")
    void noHabilitadoExcluido() {
        e.martes(DRA_A, 10, 11);
        e.martes(DR_B, 11, 12);

        assertThat(inicios(CONDUCTO, null)).containsExactly("11:00", "11:15", "11:30");
    }

    @Test
    @DisplayName("Profesional inactivo excluido")
    void inactivoExcluido() {
        e.catalogo.profesional(DRA_A, "Ana Ejemplo", false, SILLON_1);
        e.martes(DRA_A, 10, 11);
        e.martes(DR_B, 11, 12);

        assertThat(inicios(CONSULTA, null)).containsExactly("11:00", "11:15", "11:30");
    }

    @Test
    @DisplayName("Profesional elegido no habilitado")
    void elegidoNoHabilitado() {
        e.martes(DRA_A, 10, 11);

        assertThat(inicios(CONDUCTO, DRA_A)).isEmpty();
    }

    @Test
    @DisplayName("Profesional elegido inactivo")
    void elegidoInactivo() {
        e.catalogo.profesional(DRA_A, "Ana Ejemplo", false, SILLON_1);
        e.martes(DRA_A, 10, 11);

        assertThat(inicios(CONSULTA, DRA_A)).isEmpty();
    }

    @Test
    @DisplayName("Prestación inactiva")
    void prestacionInactiva() {
        e.catalogo.prestacion(CONSULTA, "Consulta", 30, false);
        e.martes(DRA_A, 10, 11);

        assertThat(inicios(CONSULTA, DRA_A)).isEmpty();
        assertThat(inicios(CONSULTA, null)).isEmpty();
    }

    @Test
    @DisplayName("Un horario necesita un sillón libre")
    void necesitaSillonLibre() {
        e.catalogo.sillones(SILLON_1);
        e.martes(DRA_A, LocalTime.of(10, 0), LocalTime.of(11, 30));
        e.turno(99L, DR_B, SILLON_1, 10, 0);

        assertThat(inicios(CONSULTA, DRA_A)).containsExactly("10:45", "11:00");
    }

    @Test
    @DisplayName("Prestación inexistente")
    void prestacionInexistente() {
        assertThatThrownBy(() -> consulta.ejecutar(999L, MARTES, null))
                .isInstanceOf(ReferenciaInexistenteException.class)
                .extracting(ex -> ((ReferenciaInexistenteException) ex).campo()).isEqualTo("prestacionId");
    }

    @Test
    @DisplayName("Profesional inexistente")
    void profesionalInexistente() {
        assertThatThrownBy(() -> consulta.ejecutar(CONSULTA, MARTES, 999L))
                .isInstanceOf(ReferenciaInexistenteException.class)
                .extracting(ex -> ((ReferenciaInexistenteException) ex).campo()).isEqualTo("profesionalId");
    }

    @Test
    @DisplayName("Una fecha pasada da la lista vacía")
    void fechaPasada() {
        e.martes(DRA_A, 10, 11);

        assertThat(consulta.ejecutar(CONSULTA, MARTES.minusDays(7), DRA_A)).isEmpty();
    }
}
