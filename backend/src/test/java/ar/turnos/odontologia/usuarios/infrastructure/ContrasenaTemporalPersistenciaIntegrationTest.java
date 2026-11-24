package ar.turnos.odontologia.usuarios.infrastructure;

import ar.turnos.odontologia.shared.security.IntegracionSeguridadBase;
import ar.turnos.odontologia.usuarios.application.UsuarioRepository;
import ar.turnos.odontologia.usuarios.domain.NombreUsuario;
import ar.turnos.odontologia.usuarios.domain.Rol;
import ar.turnos.odontologia.usuarios.domain.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** Columna debe_cambiar_contrasena (V21, C-41 design D5). PostgreSQL real, datos ficticios. */
@Tag("integration")
class ContrasenaTemporalPersistenciaIntegrationTest extends IntegracionSeguridadBase {

    @Autowired UsuarioRepository usuarios;

    @Test
    @DisplayName("Una cuenta que ya existía queda sin contraseña temporal")
    void cuentaExistenteSinMarca() {
        // se inserta sin nombrar la columna nueva, como las filas que había antes de V21
        crearPersonal("recepcion", "RECEPCIONISTA", contrasenaFicticia());

        Usuario usuario = usuarios.buscarPorUsername(NombreUsuario.de("recepcion")).orElseThrow();

        assertThat(usuario.debeCambiarContrasena()).isFalse();
        assertThat(jdbc.queryForObject("SELECT debe_cambiar_contrasena FROM usuario WHERE username = 'recepcion'",
                Boolean.class)).isFalse();
    }

    @Test
    @DisplayName("Una cuenta guardada con contraseña temporal conserva la marca")
    void cuentaConMarca() {
        usuarios.guardar(new Usuario(null, NombreUsuario.de("recepcion2"), passwordEncoder.encode(contrasenaFicticia()),
                Rol.RECEPCIONISTA, null, null, true, true));

        Usuario usuario = usuarios.buscarPorUsername(NombreUsuario.de("recepcion2")).orElseThrow();

        assertThat(usuario.debeCambiarContrasena()).isTrue();
    }
}
