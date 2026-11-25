package ar.turnos.odontologia.clinica;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.clinica.application.RegistroPlantillas;
import ar.turnos.odontologia.clinica.application.RegistroPlantillas.Plantilla;
import ar.turnos.odontologia.clinica.application.RegistroPlantillas.Version;
import ar.turnos.odontologia.clinica.application.ServicioPlantillas;
import ar.turnos.odontologia.clinica.domain.ClinicaException;
import ar.turnos.odontologia.clinica.domain.CodigoClinica;
import ar.turnos.odontologia.clinica.domain.VersionPlantilla;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Adaptador JDBC de las plantillas contra PostgreSQL real (C-27, tarea 4.1, design D6 y D10). Textos ficticios. */
@Tag("integration")
@ActiveProfiles("test")
@SpringBootTest
class RegistroPlantillasJdbcIntegrationTest {

    private static final Instant MOMENTO = Instant.parse("2026-11-24T13:30:00.123456Z");

    @Autowired
    private RegistroPlantillas registro;

    @Autowired
    private ServicioPlantillas servicio;

    @Autowired
    private JdbcTemplate jdbc;

    private long admin;

    @BeforeEach
    void datosFicticios() {
        jdbc.execute("TRUNCATE usuario, turno, paciente, prestacion, sillon, profesional RESTART IDENTITY CASCADE");
        admin = jdbc.queryForObject("INSERT INTO usuario(username, password_hash, rol) VALUES ('admin','x','ADMIN')"
                + " RETURNING id", Long.class);
    }

    @Test
    @DisplayName("Una plantilla guardada se lee igual, con su última versión y quién la creó")
    void unaPlantillaGuardadaSeLeeIgual() {
        long id = registro.crear(admin, MOMENTO, VersionPlantilla.nueva("Extracción dental", "Línea uno\nLínea dos"));

        Plantilla leida = registro.buscar(id).orElseThrow();

        assertThat(leida.activa()).isTrue();
        assertThat(leida.ultima().numero()).isEqualTo(1);
        assertThat(leida.ultima().titulo()).isEqualTo("Extracción dental");
        assertThat(leida.ultima().texto()).isEqualTo("Línea uno\nLínea dos");
        assertThat(leida.ultima().creadaEn()).isEqualTo(MOMENTO);
        assertThat(leida.ultima().creadaPor()).isEqualTo("admin");
    }

    @Test
    @DisplayName("Las versiones se listan de la más nueva a la más vieja y un número repetido se rechaza")
    void versiones() {
        long id = registro.crear(admin, MOMENTO, VersionPlantilla.nueva("Extracción dental", "Versión 1"));

        assertThat(registro.agregarVersion(id, 2, VersionPlantilla.nueva("Extracción dental", "Versión 2"), admin,
                MOMENTO.plusSeconds(60))).isTrue();
        assertThat(registro.agregarVersion(id, 2, VersionPlantilla.nueva("Extracción dental", "Otra"), admin,
                MOMENTO.plusSeconds(120))).isFalse();

        assertThat(registro.versiones(id)).extracting(Version::numero).containsExactly(2, 1);
        assertThat(registro.version(id, 1).orElseThrow().texto()).isEqualTo("Versión 1");
        assertThat(registro.buscar(id).orElseThrow().ultima().texto()).isEqualTo("Versión 2");
        assertThat(registro.version(id, 3)).isEmpty();
    }

    @Test
    @DisplayName("Activar, desactivar y listar")
    void estadoYListado() {
        long extraccion = registro.crear(admin, MOMENTO, VersionPlantilla.nueva("Extracción dental", "Texto"));
        long blanqueamiento = registro.crear(admin, MOMENTO, VersionPlantilla.nueva("Blanqueamiento", "Texto"));

        assertThat(registro.cambiarEstado(blanqueamiento, false)).isTrue();
        assertThat(registro.cambiarEstado(999_999L, false)).isFalse();

        assertThat(registro.listar(false)).extracting(Plantilla::id).containsExactly(extraccion);
        assertThat(registro.listar(true)).extracting(Plantilla::id).containsExactly(blanqueamiento, extraccion);
        assertThat(registro.buscar(999_999L)).isEmpty();
    }

    @Test
    @DisplayName("Dos ediciones simultáneas sobre la misma versión dejan una sola versión nueva")
    void dosEdicionesSimultaneas() throws Exception {
        long id = registro.crear(admin, MOMENTO, VersionPlantilla.nueva("Extracción dental", "Versión 1"));
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        List<Future<Object>> resultados = new ArrayList<>();
        try {
            for (String texto : List.of("Texto de A", "Texto de B")) {
                Callable<Object> edicion = () -> {
                    largada.await();
                    try {
                        return servicio.editar(admin, id, 1, "Extracción dental", texto);
                    } catch (ClinicaException e) {
                        return e.codigo();
                    }
                };
                resultados.add(hilos.submit(edicion));
            }
            largada.countDown();
            List<Object> valores = new ArrayList<>();
            for (Future<Object> resultado : resultados) {
                valores.add(resultado.get(30, TimeUnit.SECONDS));
            }

            assertThat(valores).filteredOn(v -> v instanceof ServicioPlantillas.Edicion).hasSize(1);
            assertThat(valores).filteredOn(v -> v == CodigoClinica.VERSION_DESACTUALIZADA).hasSize(1);
            assertThat(registro.versiones(id)).extracting(Version::numero).containsExactly(2, 1);
        } finally {
            hilos.shutdownNow();
        }
    }
}
