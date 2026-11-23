package ar.turnos.odontologia.usuarios.application;

import ar.turnos.odontologia.usuarios.domain.BusquedaInvalidaException;
import ar.turnos.odontologia.usuarios.domain.PacienteResumen;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Caso de uso de búsqueda de pacientes con un puerto en memoria. Datos ficticios. */
class BuscarPacientesTest {

    private final BusquedaPacientesEnMemoria puerto = new BusquedaPacientesEnMemoria();
    private final BuscarPacientes buscar = new BuscarPacientes(puerto);

    @Test
    @DisplayName("Un término de dígitos delega la búsqueda por DNI")
    void porDni() {
        puerto.pacientes.add(new PacienteResumen(3L, "Carla", "Ficticia", "99000003"));

        List<PacienteResumen> resultado = buscar.buscar(" 99000003 ");

        assertThat(resultado).hasSize(1);
        assertThat(puerto.ultimoPrefijoDni).isEqualTo("99000003");
        assertThat(puerto.ultimasPalabras).isNull();
    }

    @Test
    @DisplayName("Un término con letras delega la búsqueda por texto con las palabras escapadas")
    void porTexto() {
        buscar.buscar("Paciente_ Dos");

        assertThat(puerto.ultimasPalabras).containsExactly("paciente\\_", "dos");
        assertThat(puerto.ultimoPrefijoDni).isNull();
    }

    @Test
    @DisplayName("No devuelve más de 10 resultados")
    void limite() {
        IntStream.rangeClosed(1, 12).forEach(i -> puerto.pacientes.add(
                new PacienteResumen((long) i, "Nombre" + i, "Prueba", "9910000" + i)));

        assertThat(buscar.buscar("prueba")).hasSize(10);
    }

    @Test
    @DisplayName("Un término inválido no llega al puerto")
    void invalido() {
        assertThatThrownBy(() -> buscar.buscar("f")).isInstanceOf(BusquedaInvalidaException.class);
        assertThatThrownBy(() -> buscar.buscar(null)).isInstanceOf(BusquedaInvalidaException.class);
        assertThat(puerto.ultimasPalabras).isNull();
        assertThat(puerto.ultimoPrefijoDni).isNull();
    }

    @Test
    @DisplayName("El resumen del paciente no deja datos en el toString")
    void resumenSinDatos() {
        PacienteResumen resumen = new PacienteResumen(3L, "Carla", "Ficticia", "99000003");

        assertThat(resumen.toString()).doesNotContain("Carla", "Ficticia", "99000003");
    }
}
