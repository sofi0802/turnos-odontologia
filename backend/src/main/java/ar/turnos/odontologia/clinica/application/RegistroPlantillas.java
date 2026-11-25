package ar.turnos.odontologia.clinica.application;

import ar.turnos.odontologia.clinica.domain.PlantillaConsentimiento;
import ar.turnos.odontologia.clinica.domain.VersionPlantilla;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida: plantillas de consentimiento y sus versiones (C-27, design D5, D6 y D10). No tiene operaciones para
 * modificar ni borrar una versión: cada edición agrega la siguiente. De la plantilla solo cambia si está activa.
 */
public interface RegistroPlantillas {

    /** Inserta la plantilla activa con su versión 1 y devuelve su id. */
    long crear(Long usuarioId, Instant momento, VersionPlantilla contenido);

    /** La plantilla con su última versión. */
    Optional<Plantilla> buscar(long plantillaId);

    /**
     * La plantilla con su última versión, bloqueada con {@code FOR UPDATE} hasta el fin de la transacción: dos
     * ediciones sobre la misma plantilla se ejecutan una después de la otra (design D6).
     */
    Optional<Plantilla> bloquearParaEditar(long plantillaId);

    /**
     * Inserta la versión {@code numero}.
     *
     * @return {@code false} si ya existía ese número para la plantilla ({@code UNIQUE (plantilla_id, numero)})
     */
    boolean agregarVersion(long plantillaId, int numero, VersionPlantilla contenido, Long usuarioId, Instant momento);

    /** @return {@code false} si la plantilla no existe */
    boolean cambiarEstado(long plantillaId, boolean activa);

    /** Las plantillas con su última versión, ordenadas por título. */
    List<Plantilla> listar(boolean incluirInactivas);

    /** Las versiones de la plantilla, de la más nueva a la más vieja. */
    List<Version> versiones(long plantillaId);

    Optional<Version> version(long plantillaId, int numero);

    record Plantilla(long id, boolean activa, Version ultima) {

        public PlantillaConsentimiento dominio() {
            return new PlantillaConsentimiento(id, activa, ultima.numero(),
                    new VersionPlantilla(ultima.titulo(), ultima.texto()));
        }
    }

    /** @param creadaPor el username del {@code ADMIN} que la creó */
    record Version(long id, int numero, String titulo, String texto, Instant creadaEn, String creadaPor) {}
}
