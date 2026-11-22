package ar.turnos.odontologia.clinica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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

/** Esquema de V8 contra PostgreSQL real (tarea 1.2): la base repite las reglas del odontograma (design D3, D8). */
@Tag("integration")
@ActiveProfiles("test")
@SpringBootTest
class V8MigrationIntegrationTest {

    private static final Timestamp AHORA = Timestamp.from(Instant.parse("2026-12-01T13:00:00Z"));
    private static final Timestamp FIN = Timestamp.from(Instant.parse("2026-12-01T13:30:00Z"));
    private static final Timestamp FIN_CON_MARGEN = Timestamp.from(Instant.parse("2026-12-01T13:40:00Z"));

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
    @DisplayName("El odontograma rechaza modificar o borrar un registro")
    void soloAgregado() {
        long registro = registrar(16, "O", "CARIES");

        assertThatThrownBy(() -> jdbc.update("UPDATE odontograma_registro SET estado = 'SANO' WHERE id = ?", registro))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("solo agregado");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM odontograma_registro WHERE id = ?", registro))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("solo agregado");
        assertThat(jdbc.queryForObject("SELECT estado FROM odontograma_registro WHERE id = ?", String.class, registro))
                .isEqualTo("CARIES");
    }

    @Test
    @DisplayName("La base rechaza una pieza fuera de la notación FDI")
    void rechazaPiezaFueraDeFdi() {
        assertThatThrownBy(() -> registrar(19, "O", "CARIES")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> registrar(56, "O", "CARIES")).isInstanceOf(DataIntegrityViolationException.class);
        registrar(54, "O", "CARIES");
    }

    @Test
    @DisplayName("La base rechaza la cara palatina en una pieza inferior y la lingual en una superior")
    void rechazaCaraSegunCuadrante() {
        assertThatThrownBy(() -> registrar(31, "P", "OBTURADO")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> registrar(21, "L", "OBTURADO")).isInstanceOf(DataIntegrityViolationException.class);
        registrar(21, "P", "OBTURADO");
        registrar(31, "L", "OBTURADO");
        registrar(36, null, "AUSENTE");
    }

    @Test
    @DisplayName("La base rechaza un estado fuera del catálogo")
    void rechazaEstadoDesconocido() {
        assertThatThrownBy(() -> registrar(16, "O", "ROTO")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("La auditoría acepta las siete acciones y rechaza una desconocida")
    void auditoriaConLasSieteAcciones() {
        long sillon = id("INSERT INTO sillon(nombre) VALUES ('Sillón 1') RETURNING id");
        long prestacion = id("INSERT INTO prestacion(nombre, duracion_minutos) VALUES ('Consulta', 30) RETURNING id");
        long turno = jdbc.queryForObject("INSERT INTO turno(profesional_id, sillon_id, prestacion_id, paciente_id, inicio,"
                        + " fin, fin_con_margen, estado) VALUES (?,?,?,?,?,?,?,'RESERVADO') RETURNING id", Long.class,
                profesional, sillon, prestacion, paciente, AHORA, FIN, FIN_CON_MARGEN);

        for (String accion : List.of("CREAR", "CANCELAR", "REPROGRAMAR", "SOBRETURNO", "EXTENDER", "CONFIRMAR", "AUSENTE")) {
            auditar(turno, accion);
        }
        assertThatThrownBy(() -> auditar(turno, "BORRAR")).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno_auditoria WHERE turno_id = ?", Integer.class, turno))
                .isEqualTo(7);
    }

    private long registrar(int pieza, String cara, String estado) {
        return jdbc.queryForObject("INSERT INTO odontograma_registro(paciente_id, pieza, cara, estado, profesional_id,"
                + " registrado_en) VALUES (?,?,?,?,?,?) RETURNING id", Long.class,
                paciente, pieza, cara, estado, profesional, AHORA);
    }

    private void auditar(long turno, String accion) {
        jdbc.update("INSERT INTO turno_auditoria(turno_id, accion, actor, fecha) VALUES (?, ?, 'ana.ejemplo', ?)",
                turno, accion, AHORA);
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
