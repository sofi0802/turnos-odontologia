package ar.turnos.odontologia.clinica.application;

import ar.turnos.odontologia.clinica.application.RegistroPlantillas.Plantilla;
import ar.turnos.odontologia.clinica.application.RegistroPlantillas.Version;
import ar.turnos.odontologia.clinica.domain.ClinicaException;
import ar.turnos.odontologia.clinica.domain.CodigoClinica;
import ar.turnos.odontologia.clinica.domain.VersionPlantilla;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plantillas de consentimiento editables por el {@code ADMIN} (C-27, RN-CL-09, design D2, D3 y D6). Cada edición con
 * cambios crea una versión nueva; las anteriores no se tocan. Nunca escribe logs con el título ni el texto.
 */
@Service
@Transactional
public class ServicioPlantillas {

    private final RegistroPlantillas registro;
    private final Clock reloj;

    public ServicioPlantillas(RegistroPlantillas registro, Clock reloj) {
        this.registro = registro;
        this.reloj = reloj;
    }

    /**
     * @throws ClinicaException {@code DATOS_INVALIDOS} o {@code PLANTILLA_DUPLICADA}
     */
    public Plantilla crear(Long usuarioId, String titulo, String texto) {
        VersionPlantilla contenido = VersionPlantilla.nueva(titulo, texto);
        exigirTituloLibre(contenido.titulo(), null);
        long id = registro.crear(usuarioId, reloj.instant(), contenido);
        return registro.buscar(id).orElseThrow();
    }

    /**
     * @param versionBase la versión sobre la que trabajó el {@code ADMIN}
     * @throws ClinicaException {@code RECURSO_NO_ENCONTRADO}, {@code DATOS_INVALIDOS}, {@code VERSION_DESACTUALIZADA}
     *                          o {@code PLANTILLA_DUPLICADA}
     */
    public Edicion editar(Long usuarioId, long plantillaId, Integer versionBase, String titulo, String texto) {
        Plantilla actual = registro.bloquearParaEditar(plantillaId).orElseThrow(ServicioPlantillas::inexistente);
        Optional<VersionPlantilla> nueva = actual.dominio().editar(versionBase, titulo, texto);
        if (nueva.isEmpty()) {
            return new Edicion(actual, false);
        }
        exigirTituloLibre(nueva.get().titulo(), plantillaId);
        int numero = actual.ultima().numero() + 1;
        if (!registro.agregarVersion(plantillaId, numero, nueva.get(), usuarioId, reloj.instant())) {
            throw new ClinicaException(CodigoClinica.VERSION_DESACTUALIZADA,
                    "La plantilla cambió: hay que volver a cargar la última versión");
        }
        return new Edicion(registro.buscar(plantillaId).orElseThrow(), true);
    }

    /**
     * @throws ClinicaException {@code DATOS_INVALIDOS} si falta {@code activa} o {@code RECURSO_NO_ENCONTRADO}
     */
    public Plantilla cambiarEstado(long plantillaId, Boolean activa) {
        if (activa == null) {
            throw new ClinicaException(CodigoClinica.DATOS_INVALIDOS, "Falta indicar si la plantilla está activa");
        }
        if (!registro.cambiarEstado(plantillaId, activa)) {
            throw inexistente();
        }
        return registro.buscar(plantillaId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<Plantilla> listar(boolean incluirInactivas) {
        return registro.listar(incluirInactivas);
    }

    /**
     * @param soloActivas {@code true} para el odontólogo: una plantilla inactiva no existe para él (design D2)
     */
    @Transactional(readOnly = true)
    public Plantilla ver(long plantillaId, boolean soloActivas) {
        return registro.buscar(plantillaId)
                .filter(plantilla -> plantilla.activa() || !soloActivas)
                .orElseThrow(ServicioPlantillas::inexistente);
    }

    @Transactional(readOnly = true)
    public List<Version> versiones(long plantillaId) {
        if (registro.buscar(plantillaId).isEmpty()) {
            throw inexistente();
        }
        return registro.versiones(plantillaId);
    }

    @Transactional(readOnly = true)
    public Version version(long plantillaId, int numero) {
        return registro.version(plantillaId, numero).orElseThrow(ServicioPlantillas::inexistente);
    }

    /**
     * El título es único sin distinguir mayúsculas entre las últimas versiones (design D5). Se compara en Java y no con
     * {@code lower()} de SQL, que con letras acentuadas depende de la configuración regional de la base.
     */
    private void exigirTituloLibre(String titulo, Long excepto) {
        String buscado = titulo.toLowerCase(Locale.ROOT);
        boolean repetido = registro.listar(true).stream()
                .filter(plantilla -> excepto == null || plantilla.id() != excepto)
                .anyMatch(plantilla -> plantilla.ultima().titulo().toLowerCase(Locale.ROOT).equals(buscado));
        if (repetido) {
            throw new ClinicaException(CodigoClinica.PLANTILLA_DUPLICADA, "Ya hay una plantilla con ese título");
        }
    }

    private static ClinicaException inexistente() {
        return new ClinicaException(CodigoClinica.RECURSO_NO_ENCONTRADO, "No existe la plantilla");
    }

    /** @param creada {@code false} si la edición no tenía cambios y se devuelve la versión actual */
    public record Edicion(Plantilla plantilla, boolean creada) {}
}
