package ar.turnos.odontologia.reserva.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.agenda.domain.reglas.FranjaHoraria;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GrillaHorariosTest {

    private static final ZoneId BA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate MARTES = LocalDate.of(2026, 12, 1);
    private static final Instant TEMPRANO = hora(8, 0);
    private static final Duration TREINTA = Duration.ofMinutes(30);

    private static Instant hora(int h, int m) {
        return LocalDateTime.of(MARTES, LocalTime.of(h, m)).atZone(BA).toInstant();
    }

    private static FranjaHoraria martes(int desde, int hasta) {
        return FranjaHoraria.de(2, LocalTime.of(desde, 0), LocalTime.of(hasta, 0));
    }

    private List<Instant> inicios(List<FranjaHoraria> franjas, Instant ahora, int paso) {
        return GrillaHorarios.inicios(MARTES, BA, franjas, TREINTA, ahora, paso);
    }

    @Test
    @DisplayName("Franja de una hora con turno de 30 minutos da tres inicios")
    void franjaConTresInicios() {
        assertThat(inicios(List.of(martes(10, 11)), TEMPRANO, 15))
                .containsExactly(hora(10, 0), hora(10, 15), hora(10, 30));
    }

    @Test
    @DisplayName("Un día sin franja da la lista vacía")
    void diaSinFranja() {
        FranjaHoraria miercoles = FranjaHoraria.de(3, LocalTime.of(10, 0), LocalTime.of(11, 0));
        assertThat(inicios(List.of(miercoles), TEMPRANO, 15)).isEmpty();
        assertThat(inicios(List.of(), TEMPRANO, 15)).isEmpty();
    }

    @Test
    @DisplayName("El reloj a las 10:20 deja solo las 10:30")
    void relojDejaSoloElUltimo() {
        assertThat(inicios(List.of(martes(10, 11)), hora(10, 20), 15)).containsExactly(hora(10, 30));
    }

    @Test
    @DisplayName("Un inicio igual al reloj se incluye")
    void inicioIgualAlRelojSeIncluye() {
        assertThat(inicios(List.of(martes(10, 11)), hora(10, 15), 15)).containsExactly(hora(10, 15), hora(10, 30));
    }

    @Test
    @DisplayName("El paso es configurable y las franjas se unen ordenadas")
    void pasoConfigurableYVariasFranjas() {
        assertThat(inicios(List.of(martes(14, 15), martes(10, 11)), TEMPRANO, 30))
                .containsExactly(hora(10, 0), hora(10, 30), hora(14, 0), hora(14, 30));
    }

    @Test
    @DisplayName("Una franja más corta que la prestación no da inicios")
    void franjaMasCortaQueLaPrestacion() {
        FranjaHoraria veinte = new FranjaHoraria(DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(10, 20));
        assertThat(inicios(List.of(veinte), TEMPRANO, 15)).isEmpty();
    }
}
