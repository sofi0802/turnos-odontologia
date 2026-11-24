package ar.turnos.odontologia.usuarios.application;

import ar.turnos.odontologia.usuarios.domain.NombreUsuario;
import ar.turnos.odontologia.usuarios.domain.Rol;
import ar.turnos.odontologia.usuarios.domain.Usuario;

import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de usuarios. */
public interface UsuarioRepository {

    Optional<Usuario> buscarPorUsername(NombreUsuario username);

    boolean existePorUsername(NombreUsuario username);

    boolean existeConRol(Rol rol);

    /** Guarda un usuario nuevo y lo devuelve con su id. */
    Usuario guardar(Usuario usuario);

    Optional<Usuario> buscarPorId(long id);

    /** Las cuentas ADMIN, RECEPCIONISTA y ODONTOLOGO, ordenadas por usuario (C-41). */
    List<Usuario> listarPersonal();

    boolean existePorProfesional(long profesionalId);

    /** Guarda los cambios de un usuario que ya existe (estado, contraseña y marca de temporal). */
    Usuario actualizar(Usuario usuario);
}
