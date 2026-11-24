package ar.turnos.odontologia.clinica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
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

/** Esquema de V16 contra PostgreSQL real (C-25, tarea 3.1): la base repite las reglas de RN-CL-05 (design D3). */
@Tag("integration")
@ActiveProfiles("test")
@SpringBootTest
class V16MigrationIntegrationTest {

    private static final Timestamp AHORA = Timestamp.from(Instant.parse("2026-12-01T13:00:00Z"));

    @Autowired
    private JdbcTemplate jdbc;

    private long paciente;
    private long profesional;

    @BeforeEach
    void datosFicticios() {
        jdbc.execute("TRUNCATE usuario, turno, paciente, prestacion, sillon, profesional RESTART IDENTITY CASCADE");
        profesional = id("INSERT INTO profesional(nombre, apellido, matricula) VALUES ('Ana','Ejemplo','MP-0001') RETURNING id");
        paciente = id("INSERT INTO paciente(nombre, apellido, dni) VALUES ('Paciente','Uno','99000001') RETURNING id");
    }

    @Test
    @DisplayName("La base rechaza modificar o borrar una versión")
    void laBaseRechazaModificarOBorrarUnaVersion() {
        long version = version();
        long pieza = pieza(version, 16, false, 0, 0);
        long sitio = sitio(pieza, "MV", 3, 0);

        assertSoloAgregado("UPDATE periodontograma_version SET registrado_en = now() WHERE id = ?", version);
        assertSoloAgregado("DELETE FROM periodontograma_version WHERE id = ?", version);
        assertSoloAgregado("UPDATE periodontograma_pieza SET movilidad = 2 WHERE id = ?", pieza);
        assertSoloAgregado("DELETE FROM periodontograma_pieza WHERE id = ?", pieza);
        assertSoloAgregado("UPDATE periodontograma_sitio SET profundidad_sondaje = 5 WHERE id = ?", sitio);
        assertSoloAgregado("DELETE FROM periodontograma_sitio WHERE id = ?", sitio);

        assertThat(jdbc.queryForObject("SELECT movilidad FROM periodontograma_pieza WHERE id = ?", Integer.class, pieza))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT profundidad_sondaje FROM periodontograma_sitio WHERE id = ?",
                Integer.class, sitio)).isEqualTo(3);
    }

    @Test
    @DisplayName("La base acepta solo piezas permanentes")
    void soloPiezasPermanentes() {
        long version = version();
        assertThatThrownBy(() -> pieza(version, 55, false, 0, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> pieza(version, 19, false, 0, null)).isInstanceOf(DataIntegrityViolationException.class);
        pieza(version, 11, false, 0, null);
        pieza(version, 48, false, 0, null);
    }

    @Test
    @DisplayName("La base rechaza movilidad y furcación fuera de rango, y furcación en una pieza no multirradicular")
    void rangosDeLaPieza() {
        long version = version();
        assertThatThrownBy(() -> pieza(version, 11, false, 4, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> pieza(version, 46, false, 0, 4)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> pieza(version, 11, false, 0, 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> pieza(version, 15, false, 0, 1)).isInstanceOf(DataIntegrityViolationException.class);
        pieza(version, 14, false, 0, 1);
        pieza(version, 24, false, 0, 1);
        pieza(version, 37, false, 0, 3);
    }

    @Test
    @DisplayName("La base rechaza una pieza ausente con movilidad o furcación, y una presente sin movilidad")
    void piezaAusente() {
        long version = version();
        assertThatThrownBy(() -> pieza(version, 18, true, 1, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> pieza(version, 18, true, null, 0)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> pieza(version, 17, false, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        pieza(version, 18, true, null, null);
    }

    @Test
    @DisplayName("La base rechaza profundidad, recesión o sitio fuera de rango")
    void rangosDelSitio() {
        long pieza = pieza(version(), 11, false, 0, null);
        assertThatThrownBy(() -> sitio(pieza, "MV", 16, 0)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> sitio(pieza, "MV", -1, 0)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> sitio(pieza, "V", 2, -6)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> sitio(pieza, "V", 2, 16)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> sitio(pieza, "XX", 2, 0)).isInstanceOf(DataIntegrityViolationException.class);
        sitio(pieza, "MV", 15, -5);
        sitio(pieza, "V", 0, 15);
    }

    @Test
    @DisplayName("La base rechaza una pieza repetida en la versión y un sitio repetido en la pieza")
    void unicidad() {
        long version = version();
        long pieza = pieza(version, 21, false, 0, null);
        assertThatThrownBy(() -> pieza(version, 21, false, 0, null)).isInstanceOf(DataIntegrityViolationException.class);
        sitio(pieza, "V", 2, 0);
        assertThatThrownBy(() -> sitio(pieza, "V", 3, 0)).isInstanceOf(DataIntegrityViolationException.class);
        pieza(version(), 21, false, 0, null);
    }

    private void assertSoloAgregado(String sql, long id) {
        assertThatThrownBy(() -> jdbc.update(sql, id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("solo agregado");
    }

    private long version() {
        return jdbc.queryForObject("INSERT INTO periodontograma_version(paciente_id, profesional_id, registrado_en)"
                + " VALUES (?,?,?) RETURNING id", Long.class, paciente, profesional, AHORA);
    }

    private long pieza(long version, int pieza, boolean ausente, Integer movilidad, Integer furcacion) {
        return jdbc.queryForObject("INSERT INTO periodontograma_pieza(version_id, pieza, ausente, movilidad, furcacion)"
                + " VALUES (?,?,?,?,?) RETURNING id", Long.class, version, pieza, ausente, movilidad, furcacion);
    }

    private long sitio(long pieza, String sitio, int profundidad, int recesion) {
        return jdbc.queryForObject("INSERT INTO periodontograma_sitio(pieza_id, sitio, profundidad_sondaje, recesion,"
                + " sangrado, placa) VALUES (?,?,?,?,false,false) RETURNING id", Long.class,
                pieza, sitio, profundidad, recesion);
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
