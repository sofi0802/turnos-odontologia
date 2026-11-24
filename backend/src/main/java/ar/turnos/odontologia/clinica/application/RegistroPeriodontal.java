package ar.turnos.odontologia.clinica.application;

import ar.turnos.odontologia.clinica.domain.PiezaPeriodontal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida: almacenamiento del periodontograma (C-25, design D6). Cada versión es de solo agregado
 * (RN-CL-04): el puerto no tiene operaciones para modificar ni borrar.
 */
public interface RegistroPeriodontal {

    /** Inserta la versión con sus piezas y sitios en una sola transacción y devuelve su id. */
    long registrar(Long pacienteId, Long profesionalId, Instant momento, List<PiezaPeriodontal> piezas);

    /** Las versiones del paciente, de la más nueva a la más vieja, sin las piezas. */
    List<ResumenVersion> versiones(Long pacienteId);

    /** La versión con sus piezas, solo si es de ese paciente. */
    Optional<Version> version(Long pacienteId, Long versionId);

    record ResumenVersion(Long id, Long profesionalId, String profesional, Instant registradoEn) {}

    record Version(ResumenVersion resumen, List<PiezaPeriodontal> piezas) {

        public Version {
            piezas = List.copyOf(piezas);
        }
    }
}
