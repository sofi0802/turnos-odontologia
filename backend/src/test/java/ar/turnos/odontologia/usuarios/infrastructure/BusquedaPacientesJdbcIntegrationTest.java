package ar.turnos.odontologia.usuarios.infrastructure;

import ar.turnos.odontologia.shared.security.IntegracionSeguridadBase;
import ar.turnos.odontologia.usuarios.application.BusquedaPacientes;
import ar.turnos.odontologia.usuarios.domain.PacienteResumen;
import ar.turnos.odontologia.usuarios.domain.TerminoBusqueda;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Adaptador JDBC de la búsqueda sobre PostgreSQL real. Datos ficticios. */
@Tag("integration")
class BusquedaPacientesJdbcIntegrationTest extends IntegracionSeguridadBase {

    @Autowired
    BusquedaPacientes busqueda;

    @BeforeEach
    void pacientes() {
        crearPacienteSinCuenta("Paciente", "Uno", "99000001", "paciente.uno@example.com");
        crearPacienteSinCuenta("Paciente", "Dos", "99000002", "paciente.dos@example.com");
        crearPacienteSinCuenta("Carla", "Ficticia", "99000003", "carla.ficticia@example.com");
        crearPacienteSinCuenta("Julián", "Ejemplar", "99000004", "julian.ejemplar@example.com");
    }

    private List<PacienteResumen> texto(String q) {
        return busqueda.porTexto(TerminoBusqueda.de(q).palabrasParaLike(), 10);
    }

    private static List<String> nombres(List<PacienteResumen> resultado) {
        return resultado.stream().map(p -> p.nombre() + " " + p.apellido()).toList();
    }

    @Test
    @DisplayName("El DNI se busca por prefijo y se ordena por DNI")
    void dniPorPrefijo() {
        assertThat(busqueda.porDni("9900000", 10)).extracting(PacienteResumen::dni)
                .containsExactly("99000001", "99000002", "99000003", "99000004");
        assertThat(busqueda.porDni("99000003", 10)).extracting(PacienteResumen::dni).containsExactly("99000003");
        assertThat(busqueda.porDni("12345678", 10)).isEmpty();
    }

    @Test
    @DisplayName("El texto busca cada palabra en el nombre o el apellido")
    void textoPorPalabras() {
        assertThat(nombres(texto("fict"))).containsExactly("Carla Ficticia");
        assertThat(nombres(texto("paciente dos"))).containsExactly("Paciente Dos");
        assertThat(nombres(texto("carla fic"))).containsExactly("Carla Ficticia");
        assertThat(texto("zzz")).isEmpty();
    }

    @Test
    @DisplayName("El texto no distingue mayúsculas ni tildes en ninguno de los dos lados")
    void sinMayusculasNiTildes() {
        assertThat(nombres(texto("JULIAN"))).containsExactly("Julián Ejemplar");
        assertThat(nombres(texto("julián"))).containsExactly("Julián Ejemplar");
        assertThat(nombres(texto("EJEMPLAR"))).containsExactly("Julián Ejemplar");
    }

    @Test
    @DisplayName("La eñe se busca con n y con ñ")
    void enie() {
        crearPacienteSinCuenta("Nuño", "Peña", "99000005", "nuno.pena@example.com");

        assertThat(nombres(texto("nuno"))).containsExactly("Nuño Peña");
        assertThat(nombres(texto("PEÑA"))).containsExactly("Nuño Peña");
    }

    @Test
    @DisplayName("El texto se ordena por apellido, nombre e id")
    void orden() {
        crearPacienteSinCuenta("Zoe", "Prueba", "99100001", "a@example.com");
        crearPacienteSinCuenta("Álvaro", "Prueba", "99100002", "b@example.com");
        crearPacienteSinCuenta("Zoe", "Prueba", "99100003", "c@example.com");

        List<PacienteResumen> resultado = texto("prueba");

        assertThat(resultado).extracting(PacienteResumen::dni).containsExactly("99100002", "99100001", "99100003");
        assertThat(nombres(texto("paciente"))).containsExactly("Paciente Dos", "Paciente Uno");
    }

    @Test
    @DisplayName("El límite corta la lista en 10")
    void limite() {
        for (int i = 1; i <= 12; i++) {
            crearPacienteSinCuenta("Nombre" + i, "Prueba", "9920000" + (i < 10 ? "0" + i : i), "p" + i + "@example.com");
        }

        assertThat(texto("prueba")).hasSize(10);
        assertThat(busqueda.porDni("992", 10)).hasSize(10);
    }

    @Test
    @DisplayName("Los comodines se buscan como texto")
    void comodines() {
        assertThat(texto("%%")).isEmpty();
        assertThat(texto("%_")).isEmpty();
        assertThat(texto("p_ciente")).isEmpty();
        assertThat(texto("\\\\")).isEmpty();
    }
}
