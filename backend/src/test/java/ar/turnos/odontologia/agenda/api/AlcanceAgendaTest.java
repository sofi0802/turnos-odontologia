package ar.turnos.odontologia.agenda.api;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.usuarios.domain.Rol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-AG-18: qué profesional puede ver cada rol en la agenda (C-35). */
class AlcanceAgendaTest {

    private static final long DRA_A = 1L;
    private static final long DR_B = 2L;

    @Test
    @DisplayName("El odontólogo sin filtro ve solo su profesional")
    void odontologoSinFiltro() {
        AlcanceAgenda alcance = AlcanceAgenda.para(Rol.ODONTOLOGO, DRA_A, null);

        assertThat(alcance.sinTurnos()).isFalse();
        assertThat(alcance.profesionalId()).isEqualTo(DRA_A);
    }

    @Test
    @DisplayName("El odontólogo que pide otro profesional igual ve el suyo")
    void odontologoPideOtroProfesional() {
        AlcanceAgenda alcance = AlcanceAgenda.para(Rol.ODONTOLOGO, DRA_A, DR_B);

        assertThat(alcance.sinTurnos()).isFalse();
        assertThat(alcance.profesionalId()).isEqualTo(DRA_A);
    }

    @Test
    @DisplayName("Un odontólogo sin profesional vinculado no ve ningún turno")
    void odontologoSinProfesional() {
        AlcanceAgenda alcance = AlcanceAgenda.para(Rol.ODONTOLOGO, null, DR_B);

        assertThat(alcance.sinTurnos()).isTrue();
    }

    @Test
    @DisplayName("Recepción usa el filtro que pide")
    void recepcionUsaElFiltroPedido() {
        assertThat(AlcanceAgenda.para(Rol.RECEPCIONISTA, null, DR_B).profesionalId()).isEqualTo(DR_B);
        assertThat(AlcanceAgenda.para(Rol.RECEPCIONISTA, null, null).profesionalId()).isNull();
    }

    @Test
    @DisplayName("Un administrador vinculado a un profesional igual ve a todos")
    void adminVinculadoVeATodos() {
        AlcanceAgenda alcance = AlcanceAgenda.para(Rol.ADMIN, DR_B, null);

        assertThat(alcance.sinTurnos()).isFalse();
        assertThat(alcance.profesionalId()).isNull();
    }
}
