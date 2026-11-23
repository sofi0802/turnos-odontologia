package ar.turnos.odontologia.configuracion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.turnos.odontologia.configuracion.domain.CodigoError;
import ar.turnos.odontologia.configuracion.domain.ConfiguracionException;
import ar.turnos.odontologia.configuracion.infrastructure.PrestacionRepository;
import ar.turnos.odontologia.configuracion.infrastructure.ProfesionalRepository;
import ar.turnos.odontologia.configuracion.infrastructure.SillonRepository;
import ar.turnos.odontologia.shared.persistence.ProfesionalEntity;
import ar.turnos.odontologia.shared.persistence.SillonEntity;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Sillón habitual exclusivo (RN-RO-09, design D15) sin base de datos. */
class ProfesionalServiceTest {

    private static final Long ANA = 1L;
    private static final Long BRUNO = 2L;
    private static final Long SILLON_1 = 10L;

    private final ProfesionalRepository profesionales = mock(ProfesionalRepository.class);
    private final SillonRepository sillones = mock(SillonRepository.class);
    private final PrestacionRepository prestaciones = mock(PrestacionRepository.class);
    private final ProfesionalService servicio = new ProfesionalService(profesionales, sillones, prestaciones);

    private final ProfesionalEntity ana = profesional(ANA, "Ana", "Ejemplo", "MP-0001");
    private final ProfesionalEntity bruno = profesional(BRUNO, "Bruno", "Prueba", "MP-0002");
    private final SillonEntity sillon1 = sillon(SILLON_1, "Sillón 1");

    @BeforeEach
    void preparar() {
        when(profesionales.findById(ANA)).thenReturn(Optional.of(ana));
        when(profesionales.findById(BRUNO)).thenReturn(Optional.of(bruno));
        when(sillones.findById(SILLON_1)).thenReturn(Optional.of(sillon1));
    }

    @Test
    @DisplayName("Un sillón habitual de otro profesional se rechaza con SILLON_HABITUAL_OCUPADO y no cambia nada")
    void rechazaElSillonDeOtroProfesional() {
        ana.asignarSillonHabitual(sillon1);
        when(profesionales.existsBySillonHabitualIdAndIdNot(SILLON_1, BRUNO)).thenReturn(true);

        assertThatThrownBy(() -> servicio.asignarSillonHabitual(BRUNO, SILLON_1))
                .isInstanceOfSatisfying(ConfiguracionException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoError.SILLON_HABITUAL_OCUPADO));
        assertThat(bruno.getSillonHabitual()).isNull();
        assertThat(ana.getSillonHabitual()).isSameAs(sillon1);
    }

    @Test
    @DisplayName("Un sillón libre, o ya asignado al mismo profesional, se acepta")
    void aceptaElSillonLibreOElPropio() {
        when(profesionales.existsBySillonHabitualIdAndIdNot(SILLON_1, ANA)).thenReturn(false);

        assertThat(servicio.asignarSillonHabitual(ANA, SILLON_1).sillonHabitualId()).isEqualTo(SILLON_1);
        // volver a asignarlo: la consulta excluye al propio profesional, así que no lo cuenta como ocupante
        assertThat(servicio.asignarSillonHabitual(ANA, SILLON_1).sillonHabitualId()).isEqualTo(SILLON_1);
    }

    @Test
    @DisplayName("Quitar el sillón habitual no consulta la exclusividad")
    void quitarElSillonNoConsultaLaExclusividad() {
        ana.asignarSillonHabitual(sillon1);

        assertThat(servicio.asignarSillonHabitual(ANA, null).sillonHabitualId()).isNull();
    }

    private static ProfesionalEntity profesional(Long id, String nombre, String apellido, String matricula) {
        ProfesionalEntity profesional = new ProfesionalEntity(nombre, apellido, matricula);
        ReflectionTestUtils.setField(profesional, "id", id);
        return profesional;
    }

    private static SillonEntity sillon(Long id, String nombre) {
        SillonEntity sillon = new SillonEntity(nombre);
        ReflectionTestUtils.setField(sillon, "id", id);
        return sillon;
    }
}
