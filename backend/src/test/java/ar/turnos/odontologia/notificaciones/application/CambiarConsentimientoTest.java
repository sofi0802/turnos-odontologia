package ar.turnos.odontologia.notificaciones.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ar.turnos.odontologia.notificaciones.domain.CambioConsentimiento;
import ar.turnos.odontologia.notificaciones.domain.EstadoConsentimiento;
import ar.turnos.odontologia.notificaciones.domain.EventoConsentimiento;
import ar.turnos.odontologia.notificaciones.domain.MedioConsentimiento;
import ar.turnos.odontologia.notificaciones.domain.OrigenConsentimiento;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** C-17: escenarios del historial del consentimiento ("Historial de cambios", "Un pedido sin cambio..."). */
class CambiarConsentimientoTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final long PACIENTE_UNO = 1L;
    private static final long RECEPCION = 7L;

    private final ConsentimientosEnMemoria repositorio = new ConsentimientosEnMemoria();
    private Instant ahora;
    private CambiarConsentimiento cambiar;

    private static Instant el(int dia, int hora) {
        return LocalDateTime.of(2026, 12, dia, hora, 0).atZone(BUENOS_AIRES).toInstant();
    }

    private void aLas(Instant momento) {
        ahora = momento;
        cambiar = new CambiarConsentimiento(repositorio, Clock.fixed(ahora, ZoneOffset.UTC));
    }

    @BeforeEach
    void pacienteSinConsentimiento() {
        repositorio.agregarPaciente(PACIENTE_UNO);
        repositorio.agregarUsuario(RECEPCION, "recepcion");
        aLas(el(1, 10));
    }

    @Test
    @DisplayName("Otorgar y revocar deja dos registros")
    void otorgarYRevocarDejaDosRegistros() {
        cambiar.ejecutar(PACIENTE_UNO, CambioConsentimiento.delPaciente(true));
        aLas(el(3, 18));
        EstadoConsentimiento estado = cambiar.ejecutar(PACIENTE_UNO, CambioConsentimiento.delPaciente(false));

        assertThat(estado.historial()).extracting(EventoConsentimiento::otorgado, EventoConsentimiento::origen)
                .containsExactly(
                        tuple(true, OrigenConsentimiento.PACIENTE),
                        tuple(false, OrigenConsentimiento.PACIENTE));
        assertThat(estado.otorgado()).isFalse();
        assertThat(estado.actualizadoEn()).isEqualTo(el(3, 18));
    }

    @Test
    @DisplayName("El historial no se pierde")
    void elHistorialNoSePierde() {
        cambiar.ejecutar(PACIENTE_UNO, CambioConsentimiento.deLaRecepcion(true, MedioConsentimiento.TELEFONO, RECEPCION));
        aLas(el(2, 9));
        cambiar.ejecutar(PACIENTE_UNO, CambioConsentimiento.delPaciente(false));
        aLas(el(4, 11));
        EstadoConsentimiento estado = cambiar.ejecutar(PACIENTE_UNO, CambioConsentimiento.delPaciente(true));

        assertThat(estado.historial()).hasSize(3);
        EventoConsentimiento primero = estado.historial().get(0);
        assertThat(primero.origen()).isEqualTo(OrigenConsentimiento.RECEPCION);
        assertThat(primero.medio()).isEqualTo(MedioConsentimiento.TELEFONO);
        assertThat(primero.registradoPor()).isEqualTo("recepcion");
        assertThat(estado.otorgado()).isTrue();
    }

    @Test
    @DisplayName("Otorgar dos veces")
    void otorgarDosVeces() {
        cambiar.ejecutar(PACIENTE_UNO, CambioConsentimiento.delPaciente(true));
        aLas(el(2, 10));
        EstadoConsentimiento estado = cambiar.ejecutar(PACIENTE_UNO, CambioConsentimiento.delPaciente(true));

        assertThat(estado.otorgado()).isTrue();
        assertThat(estado.actualizadoEn()).isEqualTo(el(1, 10));
        assertThat(estado.historial()).hasSize(1);
    }

    @Test
    void revocarSinHaberOtorgadoNoAgregaHistorial() {
        EstadoConsentimiento estado = cambiar.ejecutar(PACIENTE_UNO, CambioConsentimiento.delPaciente(false));

        assertThat(estado.otorgado()).isFalse();
        assertThat(estado.actualizadoEn()).isNull();
        assertThat(estado.historial()).isEmpty();
    }

    @Test
    void unPacienteQueNoExisteSeRechaza() {
        assertThatThrownBy(() -> cambiar.ejecutar(99L, CambioConsentimiento.delPaciente(true)))
                .isInstanceOf(PacienteNoEncontradoException.class);
    }

    @Test
    void laRecepcionNecesitaMedioYUsuario() {
        assertThatThrownBy(() -> CambioConsentimiento.deLaRecepcion(true, null, RECEPCION))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CambioConsentimiento.deLaRecepcion(true, MedioConsentimiento.PRESENCIAL, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elRegistroOtorgaConOrigenRegistro() {
        EstadoConsentimiento estado = cambiar.ejecutar(PACIENTE_UNO, CambioConsentimiento.alRegistrarse());

        assertThat(estado.otorgado()).isTrue();
        assertThat(estado.historial().get(0).origen()).isEqualTo(OrigenConsentimiento.REGISTRO);
        assertThat(estado.historial().get(0).medio()).isNull();
    }
}
