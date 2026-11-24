package ar.turnos.odontologia.usuarios.infrastructure;

import ar.turnos.odontologia.usuarios.domain.Rol;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "usuario")
public class UsuarioEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private Rol rol;

    @Column(name = "profesional_id")
    private Long profesionalId;

    @Column(name = "paciente_id")
    private Long pacienteId;

    private boolean activo = true;

    @Column(name = "debe_cambiar_contrasena")
    private boolean debeCambiarContrasena;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private Instant creadoEn;

    protected UsuarioEntity() {}

    public UsuarioEntity(String username, String passwordHash, Rol rol, Long profesionalId, Long pacienteId) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.rol = rol;
        this.profesionalId = profesionalId;
        this.pacienteId = pacienteId;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Rol getRol() { return rol; }
    public Long getProfesionalId() { return profesionalId; }
    public Long getPacienteId() { return pacienteId; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public boolean isDebeCambiarContrasena() { return debeCambiarContrasena; }

    public void setDebeCambiarContrasena(boolean debeCambiarContrasena) { this.debeCambiarContrasena = debeCambiarContrasena; }

    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Instant getCreadoEn() { return creadoEn; }
}
