package ar.turnos.odontologia.clinica.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Adaptador en disco del almacén de imágenes (C-26, tarea 4.1, design D2). Contenido ficticio. */
class AlmacenArchivosDiscoTest {

    @TempDir
    Path carpeta;

    private AlmacenArchivosDisco almacen;

    @BeforeEach
    void crearAlmacen() {
        almacen = new AlmacenArchivosDisco(carpeta.toString());
    }

    @Test
    @DisplayName("Guardar y leer devuelve los mismos bytes")
    void guardarYLeer() {
        byte[] contenido = "contenido ficticio".getBytes(StandardCharsets.UTF_8);

        String clave = almacen.guardar(contenido);

        assertThat(almacen.leer(clave)).isEqualTo(contenido);
        assertThat(Files.exists(carpeta.resolve(clave))).isTrue();
    }

    @Test
    @DisplayName("La clave tiene el formato ab/cd/<uuid>, sin extensión ni datos del paciente")
    void formatoDeLaClave() {
        String clave = almacen.guardar(new byte[] {1, 2, 3});

        assertThat(clave).matches("[0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f-]{36}");
        String uuid = clave.substring(6);
        assertThat(clave).startsWith(uuid.substring(0, 2) + "/" + uuid.substring(2, 4) + "/");
        assertThat(almacen.guardar(new byte[] {1})).isNotEqualTo(clave);
    }

    @Test
    @DisplayName("No quedan archivos temporales después de guardar")
    void sinTemporales() throws Exception {
        String clave = almacen.guardar(new byte[] {1, 2, 3});

        try (var archivos = Files.list(carpeta.resolve(clave).getParent())) {
            assertThat(archivos.map(p -> p.getFileName().toString())).containsExactly(clave.substring(6));
        }
    }

    @Test
    @DisplayName("Una clave fuera del formato o que sale de la carpeta se rechaza")
    void claveInvalida() {
        assertThatThrownBy(() -> almacen.leer("../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> almacen.leer("ab/cd/../../../x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> almacen.borrar("archivo.jpg")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> almacen.leer(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Borrar elimina el archivo y es idempotente")
    void borrar() {
        String clave = almacen.guardar(new byte[] {1, 2, 3});

        almacen.borrar(clave);
        almacen.borrar(clave);

        assertThat(Files.exists(carpeta.resolve(clave))).isFalse();
        assertThatThrownBy(() -> almacen.leer(clave)).isInstanceOf(UncheckedIOException.class);
    }
}
