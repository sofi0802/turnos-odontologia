package ar.turnos.odontologia.usuarios.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Contraseña temporal (C-41 design D2). */
class GeneradorContrasenaTemporalTest {

    private final GeneradorContrasenaTemporal generador = new GeneradorContrasenaTemporal(new SecureRandom());

    @RepeatedTest(50)
    @DisplayName("Tiene 14 caracteres del alfabeto sin confusos y cumple la política")
    void formato() {
        String contrasena = generador.generar();

        assertThat(contrasena).hasSize(GeneradorContrasenaTemporal.LARGO);
        assertThat(contrasena).matches("[A-Za-z0-9]+");
        assertThat(contrasena).doesNotContain("0", "O", "1", "l", "I");
        assertThat(PoliticaContrasena.cumple(contrasena)).isTrue();
    }

    @RepeatedTest(50)
    @DisplayName("Lleva al menos una mayúscula, una minúscula y un dígito")
    void clasesDeCaracteres() {
        String contrasena = generador.generar();

        assertThat(contrasena).containsPattern("[A-Z]");
        assertThat(contrasena).containsPattern("[a-z]");
        assertThat(contrasena).containsPattern("[2-9]");
    }

    @Test
    @DisplayName("Dos llamadas dan contraseñas distintas")
    void distintas() {
        Set<String> generadas = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            generadas.add(generador.generar());
        }

        assertThat(generadas).hasSize(100);
    }
}
