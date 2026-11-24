package ar.turnos.odontologia.clinica.application;

import ar.turnos.odontologia.clinica.domain.CategoriaImagen;
import ar.turnos.odontologia.clinica.domain.ImagenClinica;
import ar.turnos.odontologia.clinica.domain.TipoArchivoClinico;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida: metadatos de las imágenes clínicas (C-26, design D7 y D8). No tiene operaciones para modificar ni
 * borrar: lo único que se registra después de la carga es la anulación, una vez (RN-CL-08).
 */
public interface RegistroImagenes {

    /** Inserta la imagen y devuelve su id. */
    long registrar(Long pacienteId, Long profesionalId, Instant momento, ImagenClinica imagen, String sha256,
                   String claveAlmacen);

    /**
     * Las imágenes del paciente, de la toma más nueva a la más vieja y, con la misma fecha, de la carga más nueva.
     *
     * @param pieza si no es {@code null}, solo las de esa pieza
     */
    List<Imagen> listar(Long pacienteId, Integer pieza, boolean incluirAnuladas);

    /** La imagen, solo si es de ese paciente. */
    Optional<Imagen> buscar(Long pacienteId, Long imagenId);

    /**
     * Registra la anulación solo si la imagen sigue vigente.
     *
     * @return {@code false} si no había una imagen vigente con ese id para ese paciente
     */
    boolean anular(Long pacienteId, Long imagenId, Long profesionalId, Instant momento, String motivo);

    record Imagen(Long id, Long pacienteId, CategoriaImagen categoria, Integer pieza, LocalDate tomadaEl,
                  String descripcion, TipoArchivoClinico tipo, int tamanoBytes, String sha256, String claveAlmacen,
                  Long profesionalId, String profesional, Instant registradaEn, Anulacion anulacion) {

        public boolean anulada() {
            return anulacion != null;
        }
    }

    record Anulacion(Long profesionalId, String profesional, Instant anuladaEn, String motivo) {}
}
