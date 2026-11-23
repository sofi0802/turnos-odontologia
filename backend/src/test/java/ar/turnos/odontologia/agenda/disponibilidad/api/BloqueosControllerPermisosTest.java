package ar.turnos.odontologia.agenda.disponibilidad.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Los permisos de {@link BloqueosController} (design D16), comprobados sin base de datos. Los tests de integración
 * cubren el efecto de punta a punta; este fija que cada método declare su propia regla y que la clase no imponga
 * una más estricta que las de sus métodos.
 */
class BloqueosControllerPermisosTest {

    private static final String LECTURA = "hasAnyRole('ADMIN', 'RECEPCIONISTA', 'ODONTOLOGO')";
    private static final String ESCRITURA = "hasAnyRole('ADMIN', 'ODONTOLOGO')";
    private static final String GESTION = "hasAnyRole('ADMIN', 'RECEPCIONISTA')";

    @Test
    @DisplayName("La clase no impone un permiso: cada método declara el suyo")
    void laClaseNoImponePermiso() {
        assertThat(BloqueosController.class.getAnnotation(PreAuthorize.class)).isNull();
        Arrays.stream(BloqueosController.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic())
                .forEach(m -> assertThat(m.getAnnotation(PreAuthorize.class))
                        .as("el método %s declara su @PreAuthorize", m.getName()).isNotNull());
    }

    @Test
    @DisplayName("Consultar: ADMIN, recepción y odontólogo")
    void consultar() {
        assertThat(permiso("consultar")).isEqualTo(LECTURA);
    }

    @Test
    @DisplayName("Registrar y borrar: ADMIN y odontólogo, sin la recepción")
    void registrarYBorrar() {
        assertThat(permiso("registrar")).isEqualTo(ESCRITURA);
        assertThat(permiso("borrar")).isEqualTo(ESCRITURA);
    }

    @Test
    @DisplayName("Reasignar un turno afectado: ADMIN y recepción, sin el odontólogo")
    void reasignar() {
        assertThat(permiso("reasignar")).isEqualTo(GESTION);
    }

    private static String permiso(String metodo) {
        Method encontrado = Arrays.stream(BloqueosController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(metodo))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No existe el método " + metodo));
        return encontrado.getAnnotation(PreAuthorize.class).value();
    }
}
