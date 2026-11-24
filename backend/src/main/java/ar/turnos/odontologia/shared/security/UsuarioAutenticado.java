package ar.turnos.odontologia.shared.security;

import ar.turnos.odontologia.usuarios.domain.Rol;
import ar.turnos.odontologia.usuarios.domain.Usuario;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;

/**
 * Principal de la sesión. Lleva el vínculo con el paciente o el profesional para que ningún endpoint
 * acepte un id del pedido. Borra el hash de la contraseña después de autenticar (CredentialsContainer),
 * así no queda en la sesión.
 */
public final class UsuarioAutenticado implements UserDetails, CredentialsContainer {

    private static final long serialVersionUID = 1L;

    private final long usuarioId;
    private final String username;
    private final Rol rol;
    private final Long pacienteId;
    private final Long profesionalId;
    private final boolean activo;
    private final boolean debeCambiarContrasena;
    private final String huellaCredencial;
    private String passwordHash;

    public UsuarioAutenticado(long usuarioId, String username, String passwordHash, Rol rol,
                              Long pacienteId, Long profesionalId, boolean activo) {
        this(usuarioId, username, passwordHash, rol, pacienteId, profesionalId, activo, false);
    }

    public UsuarioAutenticado(long usuarioId, String username, String passwordHash, Rol rol,
                              Long pacienteId, Long profesionalId, boolean activo, boolean debeCambiarContrasena) {
        this.usuarioId = usuarioId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.rol = rol;
        this.pacienteId = pacienteId;
        this.profesionalId = profesionalId;
        this.activo = activo;
        this.debeCambiarContrasena = debeCambiarContrasena;
        this.huellaCredencial = huella(passwordHash);
    }

    public static UsuarioAutenticado de(Usuario usuario) {
        return new UsuarioAutenticado(usuario.id(), usuario.username().valor(), usuario.passwordHash(),
                usuario.rol(), usuario.pacienteId(), usuario.profesionalId(), usuario.activo(),
                usuario.debeCambiarContrasena());
    }

    public long usuarioId() { return usuarioId; }
    public Rol rol() { return rol; }
    public Long pacienteId() { return pacienteId; }
    public Long profesionalId() { return profesionalId; }

    /**
     * SHA-256 del hash de la contraseña con el que se autenticó. El hash se borra después del login, pero la huella
     * queda para que el filtro de verificación detecte un restablecimiento o un cambio de contraseña y cierre las
     * sesiones viejas (C-41 design D3). No sirve para autenticar.
     */
    public String huellaCredencial() { return huellaCredencial; }

    /** Huella de un hash de contraseña (o {@code null} si no hay hash). */
    public static String huella(String passwordHash) {
        if (passwordHash == null) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(passwordHash.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /** La cuenta entró con una contraseña temporal que todavía no cambió (C-41). */
    public boolean debeCambiarContrasena() { return debeCambiarContrasena; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()));
    }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public String getUsername() { return username; }

    @Override
    public boolean isEnabled() { return activo; }

    @Override
    public void eraseCredentials() { this.passwordHash = null; }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof UsuarioAutenticado u && u.usuarioId == usuarioId;
    }

    @Override
    public int hashCode() { return Long.hashCode(usuarioId); }

    @Override
    public String toString() {
        return "UsuarioAutenticado[usuarioId=" + usuarioId + ", username=" + username + ", rol=" + rol + "]";
    }
}
