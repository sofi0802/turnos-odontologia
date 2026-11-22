package ar.turnos.odontologia.notificaciones.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Armado de los links del mensaje: dirección base más una ruta configurable, nunca datos del turno ni del paciente. */
class EnlacesTest {

    @Test
    void usaLasRutasPorDefecto() {
        Enlaces enlaces = Enlaces.de("https://turnos.example.test");

        assertThat(enlaces.cancelar()).isEqualTo("https://turnos.example.test/mis-turnos/cancelar");
        assertThat(enlaces.reprogramar()).isEqualTo("https://turnos.example.test/mis-turnos/reprogramar");
        assertThat(enlaces.confirmar("tok-1")).isEqualTo("https://turnos.example.test/confirmar/tok-1");
    }

    @Test
    void respetaLasRutasConfiguradas() {
        Enlaces enlaces = new Enlaces("https://turnos.example.test", "/paciente/baja", "/paciente/cambio");

        assertThat(enlaces.cancelar()).isEqualTo("https://turnos.example.test/paciente/baja");
        assertThat(enlaces.reprogramar()).isEqualTo("https://turnos.example.test/paciente/cambio");
    }

    @Test
    void unaBarraDeMasOMenosNoRompeElLink() {
        Enlaces enlaces = new Enlaces("https://turnos.example.test/", "mis-turnos/cancelar", "/mis-turnos/reprogramar");

        assertThat(enlaces.cancelar()).isEqualTo("https://turnos.example.test/mis-turnos/cancelar");
        assertThat(enlaces.reprogramar()).isEqualTo("https://turnos.example.test/mis-turnos/reprogramar");
    }

    @Test
    void losLinksDeCancelarYReprogramarNoTienenParametrosNiCredenciales() {
        Enlaces enlaces = Enlaces.de("https://turnos.example.test");

        assertThat(enlaces.cancelar()).doesNotContain("?", "#", "=", "@");
        assertThat(enlaces.reprogramar()).doesNotContain("?", "#", "=", "@");
    }
}
