package ar.turnos.odontologia.pagos.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.pagos.domain.ReglaFinal.Destino;
import ar.turnos.odontologia.pagos.domain.ReglaFinal.FinDelTurno;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-SE-06: qué pasa con la seña pagada según cómo terminó el turno. */
class ReglaFinalTest {

    private static final Instant INICIO = Instant.parse("2026-12-07T19:00:00Z");

    private static Instant antes(long horas) {
        return INICIO.minus(Duration.ofHours(horas));
    }

    @Test
    @DisplayName("cancelado online por el paciente se reintegra")
    void online() {
        assertThat(ReglaFinal.decidir(FinDelTurno.cancelado(true, antes(30), INICIO, null))).isEqualTo(Destino.REINTEGRO);
    }

    @Test
    @DisplayName("el personal cancela con 24 horas justas de anticipación y se reintegra")
    void personalConAnticipacion() {
        assertThat(ReglaFinal.decidir(FinDelTurno.cancelado(false, antes(24), INICIO, Causa.PACIENTE)))
                .isEqualTo(Destino.REINTEGRO);
    }

    @Test
    @DisplayName("el personal cancela tarde por el consultorio y se reintegra")
    void tardePorElConsultorio() {
        assertThat(ReglaFinal.decidir(FinDelTurno.cancelado(false, antes(3), INICIO, Causa.CONSULTORIO)))
                .isEqualTo(Destino.REINTEGRO);
    }

    @Test
    @DisplayName("el personal cancela tarde a pedido del paciente y se retiene")
    void tardeAPedidoDelPaciente() {
        assertThat(ReglaFinal.decidir(FinDelTurno.cancelado(false, antes(23), INICIO, Causa.PACIENTE)))
                .isEqualTo(Destino.RETENCION);
    }

    @Test
    @DisplayName("una cancelación tarde sin causa registrada favorece al paciente")
    void tardeSinCausa() {
        assertThat(ReglaFinal.decidir(FinDelTurno.cancelado(false, antes(2), INICIO, null)))
                .isEqualTo(Destino.REINTEGRO);
    }

    @Test
    @DisplayName("el paciente ausente pierde la seña")
    void ausente() {
        assertThat(ReglaFinal.decidir(FinDelTurno.porAusencia())).isEqualTo(Destino.RETENCION);
    }
}
