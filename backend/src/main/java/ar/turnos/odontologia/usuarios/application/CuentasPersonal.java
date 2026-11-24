package ar.turnos.odontologia.usuarios.application;

import ar.turnos.odontologia.usuarios.domain.CodigoCuenta;
import ar.turnos.odontologia.usuarios.domain.CuentaRechazadaException;
import ar.turnos.odontologia.usuarios.domain.DatosCuentaInvalidosException;
import ar.turnos.odontologia.usuarios.domain.GeneradorContrasenaTemporal;
import ar.turnos.odontologia.usuarios.domain.NombreUsuario;
import ar.turnos.odontologia.usuarios.domain.Rol;
import ar.turnos.odontologia.usuarios.domain.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Cuentas del personal administradas por el ADMIN (C-41, spec cuentas-personal). Las contraseñas temporales las
 * genera el servidor y solo se devuelven en la respuesta que las crea; nunca van a los logs.
 */
@Service
public class CuentasPersonal {

    private static final Logger log = LoggerFactory.getLogger(CuentasPersonal.class);
    private static final Set<Rol> ROLES_DEL_PERSONAL = EnumSet.of(Rol.ADMIN, Rol.RECEPCIONISTA, Rol.ODONTOLOGO);

    private final UsuarioRepository usuarios;
    private final ProfesionalesParaCuentas profesionales;
    private final PasswordEncoder encoder;
    private final GeneradorContrasenaTemporal generador = new GeneradorContrasenaTemporal(new SecureRandom());

    public CuentasPersonal(UsuarioRepository usuarios, ProfesionalesParaCuentas profesionales, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.profesionales = profesionales;
        this.encoder = encoder;
    }

    /** Una cuenta con la contraseña temporal en claro, que solo viaja en esta respuesta. */
    public record CuentaConTemporal(Usuario cuenta, String contrasenaTemporal) {
        @Override
        public String toString() {
            return "CuentaConTemporal[username=" + cuenta.username().valor() + ", contrasenaTemporal=***]";
        }
    }

    @Transactional(readOnly = true)
    public List<Usuario> listar() {
        return usuarios.listarPersonal();
    }

    @Transactional
    public CuentaConTemporal crear(String usernameCrudo, String rolCrudo, Long profesionalId, String actor) {
        Rol rol = rolDelPersonal(rolCrudo);
        validarVinculo(rol, profesionalId);
        NombreUsuario username = NombreUsuario.de(usernameCrudo);
        if (usuarios.existePorUsername(username)) {
            throw new CuentaRechazadaException(CodigoCuenta.USUARIO_EXISTENTE);
        }
        if (profesionalId != null) {
            if (!profesionales.estaActivo(profesionalId)) {
                throw new CuentaRechazadaException(CodigoCuenta.PROFESIONAL_NO_DISPONIBLE);
            }
            if (usuarios.existePorProfesional(profesionalId)) {
                throw new CuentaRechazadaException(CodigoCuenta.PROFESIONAL_CON_CUENTA);
            }
        }
        String temporal = generador.generar();
        Usuario cuenta = usuarios.guardar(new Usuario(null, username, encoder.encode(temporal), rol, profesionalId,
                null, true, true));
        log.info("Cuenta del personal '{}' ({}) creada por '{}'", cuenta.username().valor(), rol, actor);
        return new CuentaConTemporal(cuenta, temporal);
    }

    @Transactional
    public Usuario desactivar(long id, long actorId, String actor) {
        Usuario cuenta = cuentaDelPersonal(id);
        noSobreLaPropia(id, actorId);
        Usuario desactivada = usuarios.actualizar(cuenta.conActivo(false));
        log.info("Cuenta del personal '{}' desactivada por '{}'", cuenta.username().valor(), actor);
        return desactivada;
    }

    @Transactional
    public Usuario activar(long id, String actor) {
        Usuario cuenta = cuentaDelPersonal(id);
        Usuario activada = usuarios.actualizar(cuenta.conActivo(true));
        log.info("Cuenta del personal '{}' reactivada por '{}'", cuenta.username().valor(), actor);
        return activada;
    }

    @Transactional
    public CuentaConTemporal restablecer(long id, long actorId, String actor) {
        Usuario cuenta = cuentaDelPersonal(id);
        noSobreLaPropia(id, actorId);
        String temporal = generador.generar();
        Usuario restablecida = usuarios.actualizar(cuenta.conContrasena(encoder.encode(temporal), true));
        log.info("Contraseña de la cuenta del personal '{}' restablecida por '{}'", cuenta.username().valor(), actor);
        return new CuentaConTemporal(restablecida, temporal);
    }

    /**
     * Cambio de la propia contraseña (spec seguridad-autenticacion, C-41). Borra la marca de temporal. La política
     * de la nueva la valida el pedido; acá se verifica la actual y que la nueva sea distinta.
     */
    @Transactional
    public Usuario cambiarPropia(long usuarioId, String actual, String nueva) {
        Usuario cuenta = usuarios.buscarPorId(usuarioId)
                .filter(u -> ROLES_DEL_PERSONAL.contains(u.rol()) && u.activo())
                .orElseThrow(() -> new CuentaRechazadaException(CodigoCuenta.USUARIO_INEXISTENTE));
        if (actual == null || !encoder.matches(actual, cuenta.passwordHash())) {
            throw new CuentaRechazadaException(CodigoCuenta.CONTRASENA_ACTUAL_INCORRECTA);
        }
        if (actual.equals(nueva)) {
            throw new DatosCuentaInvalidosException("nueva", "La contraseña nueva tiene que ser distinta de la actual");
        }
        Usuario cambiada = usuarios.actualizar(cuenta.conContrasena(encoder.encode(nueva), false));
        log.info("La cuenta del personal '{}' cambió su contraseña", cuenta.username().valor());
        return cambiada;
    }

    private static Rol rolDelPersonal(String crudo) {
        if (crudo == null || crudo.isBlank()) {
            throw new DatosCuentaInvalidosException("rol", "Elegí el rol de la cuenta");
        }
        Rol rol;
        try {
            rol = Rol.valueOf(crudo.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DatosCuentaInvalidosException("rol", "El rol debe ser ADMIN, RECEPCIONISTA u ODONTOLOGO");
        }
        if (!ROLES_DEL_PERSONAL.contains(rol)) {
            throw new DatosCuentaInvalidosException("rol", "El rol debe ser ADMIN, RECEPCIONISTA u ODONTOLOGO");
        }
        return rol;
    }

    private static void validarVinculo(Rol rol, Long profesionalId) {
        if (rol == Rol.ODONTOLOGO && profesionalId == null) {
            throw new DatosCuentaInvalidosException("profesionalId", "Una cuenta de odontólogo necesita su profesional");
        }
        if (rol == Rol.RECEPCIONISTA && profesionalId != null) {
            throw new DatosCuentaInvalidosException("profesionalId", "Una cuenta de recepción no se vincula a un profesional");
        }
    }

    private Usuario cuentaDelPersonal(long id) {
        return usuarios.buscarPorId(id)
                .filter(u -> ROLES_DEL_PERSONAL.contains(u.rol()))
                .orElseThrow(() -> new CuentaRechazadaException(CodigoCuenta.USUARIO_INEXISTENTE));
    }

    private static void noSobreLaPropia(long id, long actorId) {
        if (id == actorId) {
            throw new CuentaRechazadaException(CodigoCuenta.OPERACION_SOBRE_CUENTA_PROPIA);
        }
    }
}
