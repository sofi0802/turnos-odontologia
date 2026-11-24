package ar.turnos.odontologia.clinica.application;

import ar.turnos.odontologia.clinica.application.RegistroImagenes.Imagen;
import ar.turnos.odontologia.clinica.domain.ClinicaException;
import ar.turnos.odontologia.clinica.domain.CodigoClinica;
import ar.turnos.odontologia.clinica.domain.ImagenClinica;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Imágenes y radiografías del paciente (C-26, RN-CL-07 y RN-CL-08). Valida todo antes de guardar, guarda el archivo en
 * el almacén y después la fila; si la fila no llega a la base, borra el archivo (design D3). Nunca escribe en los logs
 * la descripción, el motivo, la categoría ni la pieza (design D9).
 */
@Service
@Transactional
public class ServicioImagenes {

    private final RegistroClinico pacientes;
    private final RegistroImagenes registro;
    private final AlmacenArchivos almacen;
    private final Clock reloj;
    private final ZoneId zona;

    public ServicioImagenes(RegistroClinico pacientes, RegistroImagenes registro, AlmacenArchivos almacen,
                            Clock reloj, ZoneId zona) {
        this.pacientes = pacientes;
        this.registro = registro;
        this.almacen = almacen;
        this.reloj = reloj;
        this.zona = zona;
    }

    /**
     * @param profesionalId el profesional vinculado al usuario de la sesión
     * @throws AccessDeniedException si el usuario no está vinculado a un profesional (403)
     * @throws ClinicaException {@code RECURSO_NO_ENCONTRADO} y los errores de validación de {@link ImagenClinica}
     */
    public Imagen cargar(Long pacienteId, Long profesionalId, String categoria, Integer pieza, LocalDate tomadaEl,
                         String descripcion, byte[] contenido) {
        exigirProfesional(profesionalId);
        exigirPaciente(pacienteId);
        Instant ahora = reloj.instant();
        ImagenClinica imagen = ImagenClinica.nueva(categoria, pieza, tomadaEl, descripcion, contenido,
                LocalDate.ofInstant(ahora, zona));
        String hash = sha256(contenido);
        String clave = almacen.guardar(contenido);
        compensarSiNoSeConfirma(clave);
        long imagenId;
        try {
            imagenId = registro.registrar(pacienteId, profesionalId, ahora, imagen, hash, clave);
        } catch (RuntimeException e) {
            almacen.borrar(clave);
            throw e;
        }
        return registro.buscar(pacienteId, imagenId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<Imagen> listar(Long pacienteId, Integer pieza, boolean incluirAnuladas) {
        exigirPaciente(pacienteId);
        return registro.listar(pacienteId, pieza, incluirAnuladas);
    }

    /**
     * El archivo de una imagen, también si está anulada. Antes de entregarlo verifica que el contenido coincida con el
     * hash guardado.
     *
     * @throws IllegalStateException si el contenido no coincide (termina en un {@code 500} sin datos)
     */
    @Transactional(readOnly = true)
    public ArchivoImagen archivo(Long pacienteId, Long imagenId) {
        Imagen imagen = imagen(pacienteId, imagenId);
        byte[] contenido = almacen.leer(imagen.claveAlmacen());
        if (!sha256(contenido).equals(imagen.sha256())) {
            throw new IllegalStateException("El archivo de la imagen " + imagenId + " no coincide con su hash");
        }
        return new ArchivoImagen(imagen, contenido);
    }

    /**
     * @throws ClinicaException {@code RECURSO_NO_ENCONTRADO}, {@code DATOS_INVALIDOS} o {@code IMAGEN_YA_ANULADA}
     */
    public Imagen anular(Long pacienteId, Long imagenId, Long profesionalId, String motivo) {
        exigirProfesional(profesionalId);
        String elMotivo = ImagenClinica.motivoDeAnulacion(motivo);
        Imagen imagen = imagen(pacienteId, imagenId);
        if (imagen.anulada() || !registro.anular(pacienteId, imagenId, profesionalId, reloj.instant(), elMotivo)) {
            throw new ClinicaException(CodigoClinica.IMAGEN_YA_ANULADA, "La imagen ya está anulada");
        }
        return registro.buscar(pacienteId, imagenId).orElseThrow();
    }

    private Imagen imagen(Long pacienteId, Long imagenId) {
        exigirPaciente(pacienteId);
        return registro.buscar(pacienteId, imagenId)
                .orElseThrow(() -> new ClinicaException(CodigoClinica.RECURSO_NO_ENCONTRADO,
                        "No existe esa imagen para el paciente"));
    }

    private void exigirPaciente(Long pacienteId) {
        if (!pacientes.existePaciente(pacienteId)) {
            throw ClinicaException.pacienteInexistente();
        }
    }

    private static void exigirProfesional(Long profesionalId) {
        if (profesionalId == null) {
            throw new AccessDeniedException("El usuario no está vinculado a un profesional");
        }
    }

    /** Si la transacción termina en rollback (por ejemplo, falla el commit), el archivo no queda huérfano. */
    private void compensarSiNoSeConfirma(String clave) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int estado) {
                    if (estado == STATUS_ROLLED_BACK) {
                        almacen.borrar(clave);
                    }
                }
            });
        }
    }

    static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record ArchivoImagen(Imagen imagen, byte[] contenido) {}
}
