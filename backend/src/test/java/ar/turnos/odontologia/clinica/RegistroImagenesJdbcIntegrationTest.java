package ar.turnos.odontologia.clinica;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.clinica.application.RegistroImagenes;
import ar.turnos.odontologia.clinica.application.RegistroImagenes.Imagen;
import ar.turnos.odontologia.clinica.domain.CategoriaImagen;
import ar.turnos.odontologia.clinica.domain.ImagenClinica;
import ar.turnos.odontologia.clinica.domain.TipoArchivoClinico;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Adaptador JDBC de las imágenes contra PostgreSQL real (C-26, tarea 5.2, design D7 y D8). Datos ficticios. */
@Tag("integration")
@ActiveProfiles("test")
@SpringBootTest
class RegistroImagenesJdbcIntegrationTest {

    private static final Instant MOMENTO = Instant.parse("2026-12-01T12:00:00Z");
    private static final String HASH = "b".repeat(64);

    @Autowired
    private RegistroImagenes registro;

    @Autowired
    private JdbcTemplate jdbc;

    private long pacienteUno;
    private long pacienteDos;
    private long draA;
    private long drB;

    @BeforeEach
    void datosFicticios() {
        jdbc.execute("TRUNCATE usuario, turno, paciente, prestacion, sillon, profesional RESTART IDENTITY CASCADE");
        draA = id("INSERT INTO profesional(nombre, apellido, matricula) VALUES ('Ana','Ejemplo','MP-0001') RETURNING id");
        drB = id("INSERT INTO profesional(nombre, apellido, matricula) VALUES ('Bruno','Prueba','MP-0002') RETURNING id");
        pacienteUno = id("INSERT INTO paciente(nombre, apellido, dni) VALUES ('Paciente','Uno','99000001') RETURNING id");
        pacienteDos = id("INSERT INTO paciente(nombre, apellido, dni) VALUES ('Paciente','Dos','99000002') RETURNING id");
    }

    @Test
    @DisplayName("Una imagen guardada se lee igual")
    void unaImagenGuardadaSeLeeIgual() {
        ImagenClinica imagen = new ImagenClinica(CategoriaImagen.RADIOGRAFIA, 36, LocalDate.of(2026, 11, 30),
                "Control", TipoArchivoClinico.JPEG, 300);
        String clave = clave();

        long id = registro.registrar(pacienteUno, draA, MOMENTO, imagen, HASH, clave);
        Imagen leida = registro.buscar(pacienteUno, id).orElseThrow();

        assertThat(leida.categoria()).isEqualTo(CategoriaImagen.RADIOGRAFIA);
        assertThat(leida.pieza()).isEqualTo(36);
        assertThat(leida.tomadaEl()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(leida.descripcion()).isEqualTo("Control");
        assertThat(leida.tipo()).isEqualTo(TipoArchivoClinico.JPEG);
        assertThat(leida.tamanoBytes()).isEqualTo(300);
        assertThat(leida.sha256()).isEqualTo(HASH);
        assertThat(leida.claveAlmacen()).isEqualTo(clave);
        assertThat(leida.profesionalId()).isEqualTo(draA);
        assertThat(leida.profesional()).isEqualTo("Ana Ejemplo");
        assertThat(leida.registradaEn()).isEqualTo(MOMENTO);
        assertThat(leida.anulada()).isFalse();
    }

    @Test
    @DisplayName("Una imagen de otro paciente no se encuentra")
    void imagenDeOtroPaciente() {
        long id = guardar(pacienteUno, null, LocalDate.of(2026, 11, 30));

        assertThat(registro.buscar(pacienteDos, id)).isEmpty();
        assertThat(registro.anular(pacienteDos, id, draA, MOMENTO, "x")).isFalse();
        assertThat(registro.listar(pacienteDos, null, true)).isEmpty();
    }

    @Test
    @DisplayName("Dos anulaciones seguidas dejan la primera")
    void dosAnulacionesDejanLaPrimera() {
        long id = guardar(pacienteUno, null, LocalDate.of(2026, 11, 30));

        assertThat(registro.anular(pacienteUno, id, draA, MOMENTO, "Primera")).isTrue();
        assertThat(registro.anular(pacienteUno, id, drB, MOMENTO.plusSeconds(60), "Segunda")).isFalse();

        Imagen anulada = registro.buscar(pacienteUno, id).orElseThrow();
        assertThat(anulada.anulacion().profesionalId()).isEqualTo(draA);
        assertThat(anulada.anulacion().profesional()).isEqualTo("Ana Ejemplo");
        assertThat(anulada.anulacion().motivo()).isEqualTo("Primera");
        assertThat(anulada.anulacion().anuladaEn()).isEqualTo(MOMENTO);
    }

    @Test
    @DisplayName("El listado ordena por fecha de la toma y carga, filtra por pieza y por anuladas")
    void ordenYFiltros() {
        long vieja = guardar(pacienteUno, 36, LocalDate.of(2026, 10, 1));
        long nueva = guardar(pacienteUno, 11, LocalDate.of(2026, 11, 1));
        long mismaFecha = guardar(pacienteUno, 36, LocalDate.of(2026, 11, 1));
        registro.anular(pacienteUno, vieja, draA, MOMENTO, "Duplicada");

        assertThat(registro.listar(pacienteUno, null, false)).extracting(Imagen::id).containsExactly(mismaFecha, nueva);
        assertThat(registro.listar(pacienteUno, null, true)).extracting(Imagen::id)
                .containsExactly(mismaFecha, nueva, vieja);
        assertThat(registro.listar(pacienteUno, 36, true)).extracting(Imagen::id).containsExactly(mismaFecha, vieja);
    }

    private long guardar(long paciente, Integer pieza, LocalDate tomadaEl) {
        ImagenClinica imagen = new ImagenClinica(CategoriaImagen.FOTOGRAFIA, pieza, tomadaEl, null,
                TipoArchivoClinico.PNG, 10);
        return registro.registrar(paciente, draA, MOMENTO, imagen, HASH, clave());
    }

    private static String clave() {
        String uuid = UUID.randomUUID().toString();
        return uuid.substring(0, 2) + "/" + uuid.substring(2, 4) + "/" + uuid;
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
