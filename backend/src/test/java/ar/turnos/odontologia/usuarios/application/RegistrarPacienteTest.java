package ar.turnos.odontologia.usuarios.application;

import ar.turnos.odontologia.usuarios.domain.CodigoRegistro;
import ar.turnos.odontologia.usuarios.domain.PerfilPaciente;
import ar.turnos.odontologia.usuarios.domain.RegistroRechazadoException;
import ar.turnos.odontologia.usuarios.domain.Rol;
import ar.turnos.odontologia.usuarios.domain.Usuario;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrarPacienteTest {

    private static final String CLAVE = "clave-ficticia-1";

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private UsuarioRepositoryEnMemoria usuarios;
    private PacienteRegistroEnMemoria pacientes;
    private RegistrarPaciente registrar;
    private final List<Long> consentimientos = new ArrayList<>();

    @BeforeEach
    void preparar() {
        usuarios = new UsuarioRepositoryEnMemoria();
        pacientes = new PacienteRegistroEnMemoria();
        registrar = new RegistrarPaciente(usuarios, pacientes, encoder, consentimientos::add);
    }

    private static RegistrarPaciente.Datos datos(String dni, String email, String clave) {
        return new RegistrarPaciente.Datos("Paciente", "Uno", dni, email, "1155550001", clave);
    }

    @Test
    @DisplayName("Registro correcto: crea el paciente y la cuenta PACIENTE con el email en minúsculas")
    void registroCorrecto() {
        Usuario cuenta = registrar.registrar(datos("99000001", "Paciente.Uno@Example.com", CLAVE));

        assertThat(cuenta.username().valor()).isEqualTo("paciente.uno@example.com");
        assertThat(cuenta.rol()).isEqualTo(Rol.PACIENTE);
        assertThat(cuenta.activo()).isTrue();
        assertThat(cuenta.profesionalId()).isNull();
        assertThat(pacientes.todos()).containsExactly(
                new PerfilPaciente("Paciente", "Uno", "99000001", "paciente.uno@example.com", "1155550001"));
        assertThat(cuenta.pacienteId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Registro correcto: guarda un hash BCrypt y no la contraseña")
    void guardaElHash() {
        Usuario cuenta = registrar.registrar(datos("99000001", "paciente.uno@example.com", CLAVE));

        assertThat(cuenta.passwordHash()).isNotEqualTo(CLAVE).startsWith("$2");
        assertThat(encoder.matches(CLAVE, cuenta.passwordHash())).isTrue();
    }

    @Test
    @DisplayName("Registro correcto: el teléfono es opcional")
    void telefonoOpcional() {
        registrar.registrar(new RegistrarPaciente.Datos("Paciente", "Uno", "99000001",
                "paciente.uno@example.com", null, CLAVE));

        assertThat(pacientes.todos()).hasSize(1);
    }

    @Test
    @DisplayName("DNI de un paciente cargado por el consultorio")
    void dniExistente() {
        pacientes.cargar(new PerfilPaciente("Paciente", "Uno", "99000001", null, null));

        assertThatThrownBy(() -> registrar.registrar(datos("99000001", "paciente.uno@example.com", CLAVE)))
                .isInstanceOfSatisfying(RegistroRechazadoException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoRegistro.DNI_YA_REGISTRADO));
        assertThat(usuarios.todos()).isEmpty();
        assertThat(pacientes.todos()).hasSize(1);
    }

    @Test
    @DisplayName("Email ya usado por otra cuenta, sin distinguir mayúsculas")
    void emailExistente() {
        registrar.registrar(datos("99000001", "paciente.uno@example.com", CLAVE));

        assertThatThrownBy(() -> registrar.registrar(datos("99000002", "PACIENTE.UNO@example.com", CLAVE)))
                .isInstanceOfSatisfying(RegistroRechazadoException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoRegistro.EMAIL_YA_REGISTRADO));
        assertThat(usuarios.todos()).hasSize(1);
        assertThat(pacientes.existePorDni("99000002")).isFalse();
    }

    @Test
    @DisplayName("Si el DNI y el email ya existen, se informa primero el DNI")
    void dniTieneprioridadSobreEmail() {
        registrar.registrar(datos("99000001", "paciente.uno@example.com", CLAVE));

        assertThatThrownBy(() -> registrar.registrar(datos("99000001", "paciente.uno@example.com", CLAVE)))
                .isInstanceOfSatisfying(RegistroRechazadoException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoRegistro.DNI_YA_REGISTRADO));
    }

    @Test
    @DisplayName("Una contraseña fuera de la política no crea nada")
    void contrasenaFueraDePolitica() {
        assertThatThrownBy(() -> registrar.registrar(datos("99000001", "paciente.uno@example.com", "corta")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(usuarios.todos()).isEmpty();
        assertThat(pacientes.todos()).isEmpty();
    }

    @Test
    void conLaCasillaMarcadaSeAvisaElConsentimientoDelPacienteCreado() {
        registrar.registrar(datos("99000001", "paciente.uno@example.com", CLAVE), true);

        assertThat(consentimientos).hasSize(1);
        assertThat(pacientes.existePorDni("99000001")).isTrue();
    }

    @Test
    void sinLaCasillaNoSeAvisaElConsentimiento() {
        registrar.registrar(datos("99000001", "paciente.uno@example.com", CLAVE), false);
        registrar.registrar(datos("99000002", "paciente.dos@example.com", CLAVE));

        assertThat(consentimientos).isEmpty();
    }
}
