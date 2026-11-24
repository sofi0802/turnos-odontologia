package ar.turnos.odontologia.clinica.api;

import ar.turnos.odontologia.clinica.api.ImagenClinicaDtos.AnulacionSolicitud;
import ar.turnos.odontologia.clinica.api.ImagenClinicaDtos.ImagenVista;
import ar.turnos.odontologia.clinica.application.ServicioImagenes;
import ar.turnos.odontologia.clinica.application.ServicioImagenes.ArchivoImagen;
import ar.turnos.odontologia.clinica.domain.ClinicaException;
import ar.turnos.odontologia.clinica.domain.CodigoClinica;
import ar.turnos.odontologia.shared.security.UsuarioAutenticado;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Imágenes y radiografías del paciente (C-26, design D1). Los mismos permisos que la ficha clínica (RN-CL-01): ADMIN y
 * el odontólogo leen, solo el odontólogo carga y anula. La recepción y el paciente quedan afuera por la anotación,
 * antes de leer ningún dato. Las partes del multipart son opcionales para que la falta de una dé {@code 422} con
 * {@code DATOS_INVALIDOS} desde el dominio, y no un {@code 400} genérico.
 */
@RestController
@RequestMapping("/api/v1/pacientes/{id}/imagenes")
class ImagenClinicaController {

    private static final String LECTURA = "hasAnyRole('ADMIN', 'ODONTOLOGO')";
    private static final String ESCRITURA = "hasRole('ODONTOLOGO')";

    private final ServicioImagenes imagenes;
    private final ZoneId zona;

    ImagenClinicaController(ServicioImagenes imagenes, ZoneId zona) {
        this.imagenes = imagenes;
        this.zona = zona;
    }

    @PreAuthorize(ESCRITURA)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    ImagenVista cargar(@PathVariable Long id,
                       @RequestPart(name = "archivo", required = false) MultipartFile archivo,
                       @RequestParam(required = false) String categoria,
                       @RequestParam(required = false) String tomadaEl,
                       @RequestParam(required = false) String pieza,
                       @RequestParam(required = false) String descripcion,
                       @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ImagenVista.de(imagenes.cargar(id, usuario.profesionalId(), categoria, entero(pieza), fecha(tomadaEl),
                descripcion, bytes(archivo)), zona);
    }

    @PreAuthorize(LECTURA)
    @GetMapping
    List<ImagenVista> listar(@PathVariable Long id, @RequestParam(required = false) Integer pieza,
                             @RequestParam(defaultValue = "false") boolean incluirAnuladas) {
        return ImagenVista.de(imagenes.listar(id, pieza, incluirAnuladas), zona);
    }

    /** El archivo con su tipo guardado, sin caché, sin que el navegador adivine el tipo y con un nombre genérico. */
    @PreAuthorize(LECTURA)
    @GetMapping("/{imagenId}/archivo")
    ResponseEntity<byte[]> archivo(@PathVariable Long id, @PathVariable Long imagenId,
                                   @RequestParam(defaultValue = "false") boolean descarga) {
        ArchivoImagen archivo = imagenes.archivo(id, imagenId);
        String nombre = "imagen-" + imagenId + "." + archivo.imagen().tipo().extension();
        ContentDisposition disposicion = (descarga ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(nombre)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.imagen().tipo().tipoContenido()))
                .contentLength(archivo.contenido().length)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .body(archivo.contenido());
    }

    @PreAuthorize(ESCRITURA)
    @PostMapping("/{imagenId}/anulacion")
    ImagenVista anular(@PathVariable Long id, @PathVariable Long imagenId,
                       @RequestBody(required = false) AnulacionSolicitud solicitud,
                       @AuthenticationPrincipal UsuarioAutenticado usuario) {
        String motivo = solicitud == null ? null : solicitud.motivo();
        return ImagenVista.de(imagenes.anular(id, imagenId, usuario.profesionalId(), motivo), zona);
    }

    private static byte[] bytes(MultipartFile archivo) {
        if (archivo == null) {
            return null;
        }
        try {
            return archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo subido", e);
        }
    }

    private static LocalDate fecha(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valor.strip());
        } catch (DateTimeParseException e) {
            throw invalido();
        }
    }

    private static Integer entero(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(valor.strip());
        } catch (NumberFormatException e) {
            throw invalido();
        }
    }

    private static ClinicaException invalido() {
        return new ClinicaException(CodigoClinica.DATOS_INVALIDOS, "Hay datos de entrada inválidos");
    }
}
