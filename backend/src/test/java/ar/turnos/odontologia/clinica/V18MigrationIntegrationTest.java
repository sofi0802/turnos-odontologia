package ar.turnos.odontologia.clinica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Esquema de V18 contra PostgreSQL real (C-27, tarea 3.1, design D5): los CHECK repiten las reglas del dominio, los
 * UNIQUE frenan versiones y revocaciones duplicadas y los triggers dejan las versiones, los firmados y las revocaciones
 * de solo agregado. De la plantilla solo cambia {@code activa}. Datos ficticios.
 */
@Tag("integration")
@ActiveProfiles("test")
@SpringBootTest
class V18MigrationIntegrationTest {

    private static final Timestamp AHORA = Timestamp.from(Instant.parse("2026-11-24T13:30:00.123456Z"));
    private static final String HASH = "c".repeat(64);

    @Autowired
    private JdbcTemplate jdbc;

    private long paciente;
    private long profesional;
    private long otroProfesional;
    private long admin;

    @BeforeEach
    void datosFicticios() {
        // El CASCADE alcanza a las cuatro tablas de V18 por sus FK a usuario, paciente y profesional.
        jdbc.execute("TRUNCATE usuario, turno, paciente, prestacion, sillon, profesional RESTART IDENTITY CASCADE");
        profesional = id("INSERT INTO profesional(nombre, apellido, matricula) VALUES ('Ana','Ejemplo','MP-0001') RETURNING id");
        otroProfesional = id("INSERT INTO profesional(nombre, apellido, matricula) VALUES ('Bruno','Prueba','MP-0002') RETURNING id");
        paciente = id("INSERT INTO paciente(nombre, apellido, dni) VALUES ('Paciente','Uno','99000001') RETURNING id");
        admin = id("INSERT INTO usuario(username, password_hash, rol) VALUES ('admin','x','ADMIN') RETURNING id");
    }

    @Test
    @DisplayName("La base acepta una plantilla, sus versiones, un firmado y su revocación válidos")
    void aceptaDatosValidos() {
        long plantilla = plantilla();
        long v1 = version(plantilla, 1, "Extracción dental", "Texto ficticio");
        version(plantilla, 2, "t".repeat(120), "x".repeat(20_000));
        long firmado = firmado(v1, "99000001", HASH, "ELECTRONICA", "SHA-256", "");
        revocacion(firmado, "El paciente decidió no hacer el tratamiento");

        assertThat(jdbc.queryForObject("SELECT activa FROM plantilla_consentimiento WHERE id = ?", Boolean.class,
                plantilla)).isTrue();
        assertThat(cuenta("consentimiento_revocacion")).isEqualTo(1);
    }

    @Nested
    class LosCheckRechazanValoresInvalidos {

        @Test
        @DisplayName("Título de 121 caracteres y texto vacío o de más de 20 000")
        void tituloYTexto() {
            long plantilla = plantilla();

            assertRechaza(() -> version(plantilla, 1, "t".repeat(121), "Texto ficticio"));
            assertRechaza(() -> version(plantilla, 1, "", "Texto ficticio"));
            assertRechaza(() -> version(plantilla, 1, "Extracción dental", ""));
            assertRechaza(() -> version(plantilla, 1, "Extracción dental", "x".repeat(20_001)));
            assertRechaza(() -> version(plantilla, 0, "Extracción dental", "Texto ficticio"));
        }

        @Test
        @DisplayName("DNI con letras, hash no hexadecimal, otro tipo de firma y otro algoritmo")
        void firmadoInvalido() {
            long v1 = version(plantilla(), 1, "Extracción dental", "Texto ficticio");

            assertRechaza(() -> firmado(v1, "99A00001", HASH, "ELECTRONICA", "SHA-256", ""));
            assertRechaza(() -> firmado(v1, "123456", HASH, "ELECTRONICA", "SHA-256", ""));
            assertRechaza(() -> firmado(v1, "99000001", "G".repeat(64), "ELECTRONICA", "SHA-256", ""));
            assertRechaza(() -> firmado(v1, "99000001", "C".repeat(64), "ELECTRONICA", "SHA-256", ""));
            assertRechaza(() -> firmado(v1, "99000001", HASH, "DIGITAL", "SHA-256", ""));
            assertRechaza(() -> firmado(v1, "99000001", HASH, "ELECTRONICA", "SHA-1", ""));
            assertRechaza(() -> firmado(v1, "99000001", HASH, "ELECTRONICA", "SHA-256", "u".repeat(513)));
            assertThat(cuenta("consentimiento_firmado")).isZero();
        }

        @Test
        @DisplayName("Motivo de revocación en blanco o de más de 200 caracteres")
        void motivo() {
            long firmado = firmado(version(plantilla(), 1, "Extracción dental", "Texto ficticio"), "99000001", HASH,
                    "ELECTRONICA", "SHA-256", "");

            assertRechaza(() -> revocacion(firmado, "   "));
            assertRechaza(() -> revocacion(firmado, "m".repeat(201)));
            assertThat(cuenta("consentimiento_revocacion")).isZero();
        }
    }

    @Nested
    class LosUniqueRechazanDuplicados {

        @Test
        @DisplayName("Dos versiones con el mismo número")
        void versionDuplicada() {
            long plantilla = plantilla();
            version(plantilla, 1, "Extracción dental", "Texto ficticio");

            assertThatThrownBy(() -> version(plantilla, 1, "Extracción dental", "Otro texto"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Dos revocaciones del mismo consentimiento")
        void revocacionDuplicada() {
            long firmado = firmado(version(plantilla(), 1, "Extracción dental", "Texto ficticio"), "99000001", HASH,
                    "ELECTRONICA", "SHA-256", "");
            revocacion(firmado, "Primera");

            assertThatThrownBy(() -> revocacion(firmado, "Segunda")).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    class EditarUnaPlantillaCreaUnaVersionNueva {

        @Test
        @DisplayName("La base rechaza modificar una versión")
        void laBaseRechazaModificarUnaVersion() {
            long version = version(plantilla(), 1, "Extracción dental", "Texto ficticio");

            assertRechaza(() -> jdbc.update("UPDATE plantilla_consentimiento_version SET texto = 'Otro' WHERE id = ?", version));
            assertRechaza(() -> jdbc.update("UPDATE plantilla_consentimiento_version SET numero = 5 WHERE id = ?", version));
            assertRechaza(() -> jdbc.update("DELETE FROM plantilla_consentimiento_version WHERE id = ?", version));

            assertThat(jdbc.queryForObject("SELECT texto FROM plantilla_consentimiento_version WHERE id = ?",
                    String.class, version)).isEqualTo("Texto ficticio");
        }
    }

    @Nested
    class ActivarYDesactivarUnaPlantilla {

        @Test
        @DisplayName("De la plantilla solo cambia activa")
        void soloCambiaActiva() {
            long plantilla = plantilla();

            assertThat(jdbc.update("UPDATE plantilla_consentimiento SET activa = false WHERE id = ?", plantilla))
                    .isEqualTo(1);
            assertThat(jdbc.update("UPDATE plantilla_consentimiento SET activa = true WHERE id = ?", plantilla))
                    .isEqualTo(1);
            assertRechaza(() -> jdbc.update("UPDATE plantilla_consentimiento SET creada_en = now() WHERE id = ?", plantilla));
            assertRechaza(() -> jdbc.update("UPDATE plantilla_consentimiento SET creada_por = NULL WHERE id = ?", plantilla));
        }

        @Test
        @DisplayName("La base rechaza borrar una plantilla")
        void laBaseRechazaBorrarUnaPlantilla() {
            long plantilla = plantilla();

            assertRechaza(() -> jdbc.update("DELETE FROM plantilla_consentimiento WHERE id = ?", plantilla));
            assertThat(cuenta("plantilla_consentimiento")).isEqualTo(1);
        }
    }

    @Nested
    class DocumentoFirmadoBloqueado {

        @Test
        @DisplayName("La base rechaza modificar un consentimiento")
        void laBaseRechazaModificarUnConsentimiento() {
            long firmado = firmado(version(plantilla(), 1, "Extracción dental", "Texto ficticio"), "99000001", HASH,
                    "ELECTRONICA", "SHA-256", "Navegador de prueba 1.0");

            assertRechaza(() -> jdbc.update("UPDATE consentimiento_firmado SET texto = 'Otro' WHERE id = ?", firmado));
            assertRechaza(() -> jdbc.update("UPDATE consentimiento_firmado SET firmante_nombre = 'Otro Nombre' WHERE id = ?", firmado));
            assertRechaza(() -> jdbc.update("UPDATE consentimiento_firmado SET firmante_dni = '99000002' WHERE id = ?", firmado));
            assertRechaza(() -> jdbc.update("UPDATE consentimiento_firmado SET firmado_en = now() WHERE id = ?", firmado));
            assertRechaza(() -> jdbc.update("UPDATE consentimiento_firmado SET hash = ? WHERE id = ?", "d".repeat(64), firmado));
            // Ni siquiera un UPDATE que deja todo igual.
            assertRechaza(() -> jdbc.update("UPDATE consentimiento_firmado SET texto = texto WHERE id = ?", firmado));
            assertRechaza(() -> jdbc.update("DELETE FROM consentimiento_firmado WHERE id = ?", firmado));

            assertThat(jdbc.queryForObject("SELECT texto || '|' || hash FROM consentimiento_firmado WHERE id = ?",
                    String.class, firmado)).isEqualTo("Texto ficticio|" + HASH);
        }
    }

    @Nested
    class RevocarUnConsentimientoConRegistro {

        @Test
        @DisplayName("La base rechaza modificar una revocación")
        void laBaseRechazaModificarUnaRevocacion() {
            long firmado = firmado(version(plantilla(), 1, "Extracción dental", "Texto ficticio"), "99000001", HASH,
                    "ELECTRONICA", "SHA-256", "");
            long revocacion = revocacion(firmado, "Motivo ficticio");

            assertRechaza(() -> jdbc.update("UPDATE consentimiento_revocacion SET motivo = 'Otro' WHERE id = ?", revocacion));
            assertRechaza(() -> jdbc.update("UPDATE consentimiento_revocacion SET revocado_por = ? WHERE id = ?",
                    otroProfesional, revocacion));
            assertRechaza(() -> jdbc.update("DELETE FROM consentimiento_revocacion WHERE id = ?", revocacion));

            assertThat(jdbc.queryForObject("SELECT motivo FROM consentimiento_revocacion WHERE id = ?", String.class,
                    revocacion)).isEqualTo("Motivo ficticio");
        }
    }

    private long plantilla() {
        return jdbc.queryForObject("INSERT INTO plantilla_consentimiento(creada_en, creada_por) VALUES (?,?) RETURNING id",
                Long.class, AHORA, admin);
    }

    private long version(long plantilla, int numero, String titulo, String texto) {
        return jdbc.queryForObject("INSERT INTO plantilla_consentimiento_version(plantilla_id, numero, titulo, texto,"
                + " creada_en, creada_por) VALUES (?,?,?,?,?,?) RETURNING id", Long.class,
                plantilla, numero, titulo, texto, AHORA, admin);
    }

    private long firmado(long version, String dni, String hash, String tipoFirma, String algoritmo, String dispositivo) {
        return jdbc.queryForObject("INSERT INTO consentimiento_firmado(paciente_id, plantilla_version_id, profesional_id,"
                        + " titulo, texto, firmante_nombre, firmante_dni, firmado_en, ip_origen, dispositivo, tipo_firma,"
                        + " formato_canonico, hash_algoritmo, hash) VALUES (?,?,?,?,?,?,?,?,?,?,?,1,?,?) RETURNING id",
                Long.class, paciente, version, profesional, "Extracción dental", "Texto ficticio", "Paciente Uno", dni,
                AHORA, "10.0.0.15", dispositivo, tipoFirma, algoritmo, hash);
    }

    private long revocacion(long firmado, String motivo) {
        return jdbc.queryForObject("INSERT INTO consentimiento_revocacion(consentimiento_id, revocado_en, revocado_por,"
                + " motivo) VALUES (?,?,?,?) RETURNING id", Long.class, firmado, AHORA, profesional, motivo);
    }

    private int cuenta(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class);
    }

    private void assertRechaza(Runnable accion) {
        assertThatThrownBy(accion::run).isInstanceOf(DataAccessException.class);
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
