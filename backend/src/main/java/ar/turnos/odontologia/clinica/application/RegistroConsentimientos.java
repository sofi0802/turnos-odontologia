package ar.turnos.odontologia.clinica.application;

import ar.turnos.odontologia.clinica.domain.ConsentimientoFirmado;
import ar.turnos.odontologia.clinica.domain.ContenidoCanonico;
import ar.turnos.odontologia.clinica.domain.PlantillaConsentimiento;
import ar.turnos.odontologia.clinica.domain.VersionPlantilla;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida: consentimientos firmados y sus revocaciones (C-27, design D4, D5, D6 y D10). No tiene operaciones
 * para modificar ni borrar un consentimiento ni una revocación: el documento firmado queda bloqueado.
 */
public interface RegistroConsentimientos {

    /** El DNI registrado del paciente, o vacío si el paciente no existe. */
    Optional<String> dniDelPaciente(Long pacienteId);

    /**
     * La plantilla con su última versión, bloqueada con {@code FOR SHARE} hasta el fin de la transacción: una edición o
     * una desactivación no se cuela entre la comprobación y la firma (design D6).
     */
    Optional<PlantillaParaFirmar> plantillaParaFirmar(long plantillaId);

    /** Inserta el consentimiento con su hash y devuelve su id. */
    long registrar(ConsentimientoFirmado consentimiento, String hash);

    /** Los consentimientos del paciente, del más nuevo al más viejo. */
    List<Consentimiento> listar(Long pacienteId);

    /** El consentimiento, solo si es de ese paciente. */
    Optional<Consentimiento> buscar(Long pacienteId, Long consentimientoId);

    /**
     * Inserta la revocación.
     *
     * @return {@code false} si el consentimiento ya tenía una ({@code UNIQUE (consentimiento_id)})
     */
    boolean revocar(Long consentimientoId, Long profesionalId, Instant momento, String motivo);

    /** La plantilla y el id, el título y el texto de su última versión. */
    record PlantillaParaFirmar(long id, boolean activa, long versionId, int versionNumero, String titulo,
                               String texto) {

        public PlantillaConsentimiento dominio() {
            return new PlantillaConsentimiento(id, activa, versionNumero, contenido());
        }

        public VersionPlantilla contenido() {
            return new VersionPlantilla(titulo, texto);
        }
    }

    record Consentimiento(Long id, Long pacienteId, Long plantillaId, Long plantillaVersionId, int versionNumero,
                          String titulo, String texto, String firmanteNombre, String firmanteDni, Long profesionalId,
                          String profesional, Instant firmadoEn, String ipOrigen, String dispositivo,
                          String tipoFirma, int formatoCanonico, String hashAlgoritmo, String hash,
                          Revocacion revocacion) {

        public boolean revocado() {
            return revocacion != null;
        }

        /** El contenido canónico rearmado con los datos guardados (design D4). */
        public ContenidoCanonico contenidoCanonico() {
            return new ContenidoCanonico(pacienteId, plantillaVersionId, titulo, texto, firmanteNombre, firmanteDni,
                    profesionalId, firmadoEn, ipOrigen, dispositivo, tipoFirma);
        }
    }

    record Revocacion(Long profesionalId, String profesional, Instant revocadoEn, String motivo) {}
}
