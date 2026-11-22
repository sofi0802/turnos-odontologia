package ar.turnos.odontologia.reserva.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.reserva.IntegracionReservaBase;
import ar.turnos.odontologia.reserva.application.CatalogoReserva;
import ar.turnos.odontologia.reserva.application.CatalogoReserva.Profesional;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Adaptador JDBC de solo lectura del catálogo de la reserva, contra PostgreSQL real (design D10). */
@Tag("integration")
class CatalogoReservaJdbcIntegrationTest extends IntegracionReservaBase {

    @Autowired CatalogoReserva catalogo;

    @Test
    @DisplayName("La prestación trae nombre, duración y estado")
    void prestacion() {
        assertThat(catalogo.prestacion(consulta)).hasValueSatisfying(p -> {
            assertThat(p.nombre()).isEqualTo("Consulta");
            assertThat(p.duracion()).isEqualTo(Duration.ofMinutes(30));
            assertThat(p.activa()).isTrue();
        });
        jdbc.update("UPDATE prestacion SET activo = FALSE WHERE id = ?", consulta);
        assertThat(catalogo.prestacion(consulta)).hasValueSatisfying(p -> assertThat(p.activa()).isFalse());
        assertThat(catalogo.prestacion(999_999L)).isEmpty();
    }

    @Test
    @DisplayName("El profesional trae su nombre completo, su estado y su sillón habitual")
    void profesional() {
        assertThat(catalogo.profesional(draA)).hasValueSatisfying(p -> {
            assertThat(p.nombre()).isEqualTo("Ana Ejemplo");
            assertThat(p.activo()).isTrue();
            assertThat(p.sillonHabitualId()).isEqualTo(sillon1);
        });
        jdbc.update("UPDATE profesional SET sillon_habitual_id = NULL, activo = FALSE WHERE id = ?", drB);
        assertThat(catalogo.profesional(drB)).hasValueSatisfying(p -> {
            assertThat(p.activo()).isFalse();
            assertThat(p.sillonHabitualId()).isNull();
        });
        assertThat(catalogo.profesional(999_999L)).isEmpty();
    }

    @Test
    @DisplayName("Solo se listan los profesionales activos y habilitados para la prestación")
    void profesionalesHabilitados() {
        assertThat(catalogo.profesionalesHabilitados(consulta)).extracting(Profesional::id)
                .containsExactly(draA, drB);
        assertThat(catalogo.profesionalesHabilitados(conducto)).extracting(Profesional::id).containsExactly(drB);
        jdbc.update("UPDATE profesional SET activo = FALSE WHERE id = ?", draA);
        assertThat(catalogo.profesionalesHabilitados(consulta)).extracting(Profesional::id).containsExactly(drB);
    }

    @Test
    @DisplayName("La habilitación no depende de que el profesional esté activo")
    void estaHabilitado() {
        assertThat(catalogo.estaHabilitado(draA, consulta)).isTrue();
        assertThat(catalogo.estaHabilitado(draA, conducto)).isFalse();
        jdbc.update("UPDATE profesional SET activo = FALSE WHERE id = ?", drB);
        assertThat(catalogo.estaHabilitado(drB, conducto)).isTrue();
    }

    @Test
    @DisplayName("Los sillones activos salen por id y las prestaciones activas por nombre")
    void sillonesYPrestacionesActivas() {
        assertThat(catalogo.sillonesActivos()).containsExactly(sillon1, sillon2);
        jdbc.update("UPDATE sillon SET activo = FALSE WHERE id = ?", sillon1);
        assertThat(catalogo.sillonesActivos()).containsExactly(sillon2);

        jdbc.update("INSERT INTO prestacion(nombre, duracion_minutos, activo) VALUES ('Limpieza', 30, FALSE)");
        assertThat(catalogo.prestacionesActivas()).extracting(CatalogoReserva.Prestacion::nombre)
                .containsExactly("Consulta", "Tratamiento de conducto");
    }
}
