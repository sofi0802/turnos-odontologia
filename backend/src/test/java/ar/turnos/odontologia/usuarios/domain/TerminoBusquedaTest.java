package ar.turnos.odontologia.usuarios.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Término de búsqueda de pacientes (design D2 a D4). Datos ficticios. */
class TerminoBusquedaTest {

    @Test
    @DisplayName("Un término con solo dígitos busca por DNI")
    void digitosBuscanPorDni() {
        TerminoBusqueda termino = TerminoBusqueda.de("99000003");

        assertThat(termino.porDni()).isTrue();
        assertThat(termino.dni()).isEqualTo("99000003");
    }

    @Test
    @DisplayName("Un término con letras busca por palabras sin tildes y en minúsculas")
    void letrasBuscanPorPalabras() {
        TerminoBusqueda termino = TerminoBusqueda.de("JULIÁN Ejemplar");

        assertThat(termino.porDni()).isFalse();
        assertThat(termino.palabras()).containsExactly("julian", "ejemplar");
    }

    @Test
    @DisplayName("La eñe se normaliza a n")
    void enie() {
        assertThat(TerminoBusqueda.de("Ñandú").palabras()).containsExactly("nandu");
    }

    @Test
    @DisplayName("Se recortan los espacios de los extremos y se colapsan los del medio")
    void recorteDeEspacios() {
        assertThat(TerminoBusqueda.de("  paciente   dos ").palabras()).containsExactly("paciente", "dos");
        assertThat(TerminoBusqueda.de(" 99000001 ").dni()).isEqualTo("99000001");
    }

    @Test
    @DisplayName("Un término de menos de 2 caracteres es inválido")
    void unaLetra() {
        assertThatThrownBy(() -> TerminoBusqueda.de("f")).isInstanceOf(BusquedaInvalidaException.class);
    }

    @Test
    @DisplayName("Un DNI de menos de 3 dígitos es inválido")
    void dniCorto() {
        assertThatThrownBy(() -> TerminoBusqueda.de("99")).isInstanceOf(BusquedaInvalidaException.class);
    }

    @Test
    @DisplayName("Un término vacío, en blanco o ausente es inválido")
    void vacio() {
        assertThatThrownBy(() -> TerminoBusqueda.de("")).isInstanceOf(BusquedaInvalidaException.class);
        assertThatThrownBy(() -> TerminoBusqueda.de("   ")).isInstanceOf(BusquedaInvalidaException.class);
        assertThatThrownBy(() -> TerminoBusqueda.de(null)).isInstanceOf(BusquedaInvalidaException.class);
    }

    @Test
    @DisplayName("Un término de más de 50 caracteres es inválido y el mensaje no lo repite")
    void demasiadoLargo() {
        String largo = "a".repeat(51);

        assertThat(TerminoBusqueda.de("a".repeat(50)).palabras()).hasSize(1);
        assertThatThrownBy(() -> TerminoBusqueda.de(largo))
                .isInstanceOf(BusquedaInvalidaException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(largo));
    }

    @Test
    @DisplayName("Los caracteres % _ y \\ se escapan para el LIKE")
    void comodinesEscapados() {
        assertThat(TerminoBusqueda.de("a%b_c\\d").palabrasParaLike()).containsExactly("a\\%b\\_c\\\\d");
        assertThat(TerminoBusqueda.de("%%").palabrasParaLike()).containsExactly("\\%\\%");
    }

    @Test
    @DisplayName("Se usan como máximo 5 palabras")
    void maximoCincoPalabras() {
        assertThat(TerminoBusqueda.de("a1 b2 c3 d4 e5 f6 g7").palabras()).hasSize(5);
    }

    @Test
    @DisplayName("El toString no deja el término en un log")
    void toStringSinDatos() {
        TerminoBusqueda texto = TerminoBusqueda.de("Ficticia");
        TerminoBusqueda dni = TerminoBusqueda.de("99000003");

        assertThat(texto.toString()).doesNotContainIgnoringCase("ficticia");
        assertThat(dni.toString()).doesNotContain("99000003");
        assertThat(List.of(texto, dni).toString()).doesNotContain("99000003");
    }
}
