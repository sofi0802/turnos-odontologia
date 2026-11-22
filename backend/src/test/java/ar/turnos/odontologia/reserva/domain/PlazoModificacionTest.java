package ar.turnos.odontologia.reserva.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlazoModificacionTest {

    private static final Instant AHORA = Instant.parse("2026-12-01T13:00:00Z");

    @Test
    @DisplayName("Exactamente 24 horas antes está permitido")
    void exactas24Horas() {
        assertThat(PlazoModificacion.permitido(AHORA, AHORA.plus(Duration.ofHours(24)))).isTrue();
    }

    @Test
    @DisplayName("23 horas 59 minutos antes está rechazado")
    void pocoMenosDe24Horas() {
        assertThat(PlazoModificacion.permitido(AHORA, AHORA.plus(Duration.ofHours(24)).minusSeconds(60))).isFalse();
    }

    @Test
    @DisplayName("Un turno ya iniciado está rechazado")
    void turnoYaIniciado() {
        assertThat(PlazoModificacion.permitido(AHORA, AHORA.minusSeconds(1))).isFalse();
        assertThat(PlazoModificacion.permitido(AHORA, AHORA)).isFalse();
    }

    @Test
    @DisplayName("Con mucha anticipación está permitido")
    void muchaAnticipacion() {
        assertThat(PlazoModificacion.permitido(AHORA, AHORA.plus(Duration.ofDays(10)))).isTrue();
    }
}
