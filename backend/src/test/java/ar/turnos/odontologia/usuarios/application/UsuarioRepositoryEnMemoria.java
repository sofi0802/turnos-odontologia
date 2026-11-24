package ar.turnos.odontologia.usuarios.application;

import ar.turnos.odontologia.usuarios.domain.NombreUsuario;
import ar.turnos.odontologia.usuarios.domain.Rol;
import ar.turnos.odontologia.usuarios.domain.Usuario;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Doble de prueba de {@link UsuarioRepository}. */
public class UsuarioRepositoryEnMemoria implements UsuarioRepository {

    private final List<Usuario> usuarios = new ArrayList<>();
    private long ultimoId = 0;

    @Override
    public Optional<Usuario> buscarPorUsername(NombreUsuario username) {
        return usuarios.stream().filter(u -> u.username().equals(username)).findFirst();
    }

    @Override
    public boolean existePorUsername(NombreUsuario username) {
        return buscarPorUsername(username).isPresent();
    }

    @Override
    public boolean existeConRol(Rol rol) {
        return usuarios.stream().anyMatch(u -> u.rol() == rol);
    }

    @Override
    public Usuario guardar(Usuario usuario) {
        Usuario guardado = new Usuario(++ultimoId, usuario.username(), usuario.passwordHash(), usuario.rol(),
                usuario.profesionalId(), usuario.pacienteId(), usuario.activo(), usuario.debeCambiarContrasena());
        usuarios.add(guardado);
        return guardado;
    }

    @Override
    public Optional<Usuario> buscarPorId(long id) {
        return usuarios.stream().filter(u -> u.id() == id).findFirst();
    }

    @Override
    public List<Usuario> listarPersonal() {
        return usuarios.stream().filter(u -> u.rol() != Rol.PACIENTE)
                .sorted(java.util.Comparator.comparing(u -> u.username().valor())).toList();
    }

    @Override
    public boolean existePorProfesional(long profesionalId) {
        return usuarios.stream().anyMatch(u -> u.profesionalId() != null && u.profesionalId() == profesionalId);
    }

    @Override
    public Usuario actualizar(Usuario usuario) {
        usuarios.replaceAll(u -> u.id().equals(usuario.id()) ? usuario : u);
        return usuario;
    }

    public List<Usuario> todos() {
        return List.copyOf(usuarios);
    }
}
