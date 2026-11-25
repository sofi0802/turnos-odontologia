package ar.turnos.odontologia.reserva.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.turnos.odontologia.reserva.application.CatalogoReserva.Prestacion;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** C-15: la validación de la preferencia de la lista de espera no depende de que el reloj quede quieto. */
class ReservaPorListaEsperaTest {

    /** Un reloj que avanza un milisegundo en cada lectura, como el del sistema. */
    private static final class RelojQueAvanza extends Clock {
        private final AtomicLong milis = new AtomicLong(Instant.parse("2026-11-25T03:00:00Z").toEpochMilli());

        @Override
        public ZoneId getZone() {
            return ZoneId.of("America/Argentina/Buenos_Aires");
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(milis.getAndIncrement());
        }
    }

    @Test
    @DisplayName("Sin preferencia de profesional, la validación no falla con INICIO_EN_EL_PASADO")
    void sinPreferenciaConElRelojAvanzando() {
        CatalogoReserva catalogo = mock(CatalogoReserva.class);
        when(catalogo.prestacion(1L)).thenReturn(Optional.of(new Prestacion(1L, "Consulta", Duration.ofMinutes(30), true)));
        when(catalogo.profesionalesHabilitados(1L)).thenReturn(List.of());
        Clock reloj = new RelojQueAvanza();
        ReservaPorListaEspera reserva = new ReservaPorListaEspera(catalogo, mock(BuscadorDeHuecos.class),
                mock(AgendaDeTurnos.class), mock(AvisoReservaOnline.class), reloj, reloj.getZone());

        assertThatCode(() -> reserva.validarPreferencia(1L, null)).doesNotThrowAnyException();
    }
}
