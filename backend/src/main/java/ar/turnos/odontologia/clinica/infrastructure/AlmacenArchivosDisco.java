package ar.turnos.odontologia.clinica.infrastructure;

import ar.turnos.odontologia.clinica.application.AlmacenArchivos;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adaptador en disco de {@link AlmacenArchivos} (C-26, DD-17, design D2). La carpeta base sale de
 * {@code app.clinica.imagenes.directorio} ({@code IMAGENES_DIRECTORIO}). Cada archivo se guarda en {@code ab/cd/<uuid>}:
 * dos niveles para no juntar miles de archivos en una carpeta, sin extensión y sin datos del paciente. Se escribe a un
 * temporal y se mueve con {@code ATOMIC_MOVE}, así una caída no deja un archivo a medias con el nombre final.
 */
@Component
public class AlmacenArchivosDisco implements AlmacenArchivos {

    private static final Pattern FORMATO_CLAVE =
            Pattern.compile("^[0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final Path base;

    public AlmacenArchivosDisco(@Value("${app.clinica.imagenes.directorio}") String directorio) {
        this.base = Path.of(directorio).toAbsolutePath().normalize();
    }

    @Override
    public String guardar(byte[] contenido) {
        String uuid = UUID.randomUUID().toString();
        String clave = uuid.substring(0, 2) + "/" + uuid.substring(2, 4) + "/" + uuid;
        Path destino = ruta(clave);
        try {
            Files.createDirectories(destino.getParent());
            Path temporal = Files.createTempFile(destino.getParent(), "subida-", ".tmp");
            try {
                Files.write(temporal, contenido);
                Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporal);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo en el almacén", e);
        }
        return clave;
    }

    @Override
    public byte[] leer(String clave) {
        try {
            return Files.readAllBytes(ruta(clave));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo del almacén", e);
        }
    }

    @Override
    public void borrar(String clave) {
        try {
            Files.deleteIfExists(ruta(clave));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo borrar el archivo del almacén", e);
        }
    }

    /** La ruta de una clave, solo si tiene el formato esperado y queda dentro de la carpeta base. */
    Path ruta(String clave) {
        if (clave == null || !FORMATO_CLAVE.matcher(clave).matches()) {
            throw new IllegalArgumentException("Clave de almacén inválida");
        }
        Path ruta = base.resolve(clave).normalize();
        if (!ruta.startsWith(base)) {
            throw new IllegalArgumentException("Clave de almacén inválida");
        }
        return ruta;
    }
}
