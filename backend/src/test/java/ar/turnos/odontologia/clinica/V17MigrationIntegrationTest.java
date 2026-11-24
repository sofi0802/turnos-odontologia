package ar.turnos.odontologia.clinica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Esquema de V17 contra PostgreSQL real (C-26, tarea 3.1): la base repite las reglas de RN-CL-07 y garantiza RN-CL-08
 * con un trigger que solo deja registrar la anulación, una vez (design D7).
 */
@Tag("integration")
@ActiveProfiles("test")
@SpringBootTest
class V17MigrationIntegrationTest {

    private static final Timestamp AHORA = Timestamp.from(Instant.parse("2026-12-01T13:00:00Z"));
    private static final Date TOMADA = Date.valueOf(LocalDate.of(2026, 11, 30));
    private static final String HASH = "a".repeat(64);

    @Autowired
    private JdbcTemplate jdbc;

    private long paciente;
    private long profesional;
    private long otroProfesional;

    @BeforeEach
    void datosFicticios() {
        jdbc.execute("TRUNCATE usuario, turno, paciente, prestacion, sillon, profesional RESTART IDENTITY CASCADE");
        profesional = id("INSERT INTO profesional(nombre, apellido, matricula) VALUES ('Ana','Ejemplo','MP-0001') RETURNING id");
        otroProfesional = id("INSERT INTO profesional(nombre, apellido, matricula) VALUES ('Bruno','Prueba','MP-0002') RETURNING id");
        paciente = id("INSERT INTO paciente(nombre, apellido, dni) VALUES ('Paciente','Uno','99000001') RETURNING id");
    }

    @Test
    @DisplayName("La base acepta una imagen válida, con o sin pieza")
    void aceptaUnaImagenValida() {
        imagen("RADIOGRAFIA", 36, "image/jpeg", 300_000);
        imagen("ESTUDIO", null, "application/pdf", 10_485_760);
        imagen("FOTOGRAFIA", 55, "image/png", 1);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM imagen_clinica", Integer.class)).isEqualTo(3);
    }

    @Test
    @DisplayName("Los CHECK rechazan categoría, tipo, pieza y tamaño inválidos")
    void losCheckRechazanValoresInvalidos() {
        assertRechaza(() -> imagen("TOMOGRAFIA", null, "image/jpeg", 10));
        assertRechaza(() -> imagen("RADIOGRAFIA", null, "image/gif", 10));
        assertRechaza(() -> imagen("RADIOGRAFIA", 19, "image/jpeg", 10));
        assertRechaza(() -> imagen("RADIOGRAFIA", 56, "image/jpeg", 10));
        assertRechaza(() -> imagen("RADIOGRAFIA", null, "image/jpeg", 0));
        assertRechaza(() -> imagen("RADIOGRAFIA", null, "image/jpeg", 10_485_761));
    }

    @Test
    @DisplayName("La anulación se guarda completa o no se guarda")
    void anulacionIncompleta() {
        assertRechaza(() -> jdbc.update("INSERT INTO imagen_clinica(paciente_id, profesional_id, categoria, tomada_el,"
                + " tipo_contenido, tamano_bytes, sha256, clave_almacen, registrada_en, anulada_en)"
                + " VALUES (?,?,'RADIOGRAFIA',?,'image/jpeg',10,?,?,?,?)",
                paciente, profesional, TOMADA, HASH, clave(), AHORA, AHORA));
        long imagen = imagen("RADIOGRAFIA", null, "image/jpeg", 10);
        assertRechaza(() -> jdbc.update("UPDATE imagen_clinica SET anulada_en = ?, anulada_por = ?, motivo_anulacion = ' '"
                + " WHERE id = ?", AHORA, profesional, imagen));
    }

    @Test
    @DisplayName("La clave del almacén es única")
    void claveUnica() {
        String clave = clave();
        imagen("RADIOGRAFIA", null, "image/jpeg", 10, clave);
        assertThatThrownBy(() -> imagen("FOTOGRAFIA", null, "image/png", 10, clave))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("La base rechaza borrar una imagen")
    void laBaseRechazaBorrarUnaImagen() {
        long imagen = imagen("RADIOGRAFIA", 36, "image/jpeg", 10);

        assertRechaza(() -> jdbc.update("DELETE FROM imagen_clinica WHERE id = ?", imagen));
        assertRechaza(() -> jdbc.update("UPDATE imagen_clinica SET categoria = 'FOTOGRAFIA' WHERE id = ?", imagen));
        assertRechaza(() -> jdbc.update("UPDATE imagen_clinica SET tomada_el = tomada_el - 1 WHERE id = ?", imagen));
        assertRechaza(() -> jdbc.update("UPDATE imagen_clinica SET clave_almacen = ? WHERE id = ?", clave(), imagen));
        assertRechaza(() -> jdbc.update("UPDATE imagen_clinica SET descripcion = 'Otra' WHERE id = ?", imagen));

        assertThat(jdbc.queryForObject("SELECT categoria FROM imagen_clinica WHERE id = ?", String.class, imagen))
                .isEqualTo("RADIOGRAFIA");
    }

    @Test
    @DisplayName("La base permite la primera anulación y rechaza la segunda")
    void unaSolaAnulacion() {
        long imagen = imagen("RADIOGRAFIA", 36, "image/jpeg", 10);

        int filas = jdbc.update("UPDATE imagen_clinica SET anulada_en = ?, anulada_por = ?, motivo_anulacion = ?"
                + " WHERE id = ?", AHORA, profesional, "Paciente equivocado", imagen);
        assertThat(filas).isEqualTo(1);

        assertRechaza(() -> jdbc.update("UPDATE imagen_clinica SET anulada_en = ?, anulada_por = ?,"
                + " motivo_anulacion = ? WHERE id = ?", AHORA, otroProfesional, "Otro motivo", imagen));
        assertThat(jdbc.queryForObject("SELECT anulada_por FROM imagen_clinica WHERE id = ?", Long.class, imagen))
                .isEqualTo(profesional);
    }

    private long imagen(String categoria, Integer pieza, String tipo, int tamano) {
        return imagen(categoria, pieza, tipo, tamano, clave());
    }

    private long imagen(String categoria, Integer pieza, String tipo, int tamano, String clave) {
        return jdbc.queryForObject("INSERT INTO imagen_clinica(paciente_id, profesional_id, categoria, pieza, tomada_el,"
                        + " tipo_contenido, tamano_bytes, sha256, clave_almacen, registrada_en)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?) RETURNING id", Long.class,
                paciente, profesional, categoria, pieza, TOMADA, tipo, tamano, HASH, clave, AHORA);
    }

    private static String clave() {
        String uuid = UUID.randomUUID().toString();
        return uuid.substring(0, 2) + "/" + uuid.substring(2, 4) + "/" + uuid;
    }

    private void assertRechaza(Runnable accion) {
        assertThatThrownBy(accion::run).isInstanceOf(DataAccessException.class);
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
