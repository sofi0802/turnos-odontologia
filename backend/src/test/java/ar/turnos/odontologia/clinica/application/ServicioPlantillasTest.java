package ar.turnos.odontologia.clinica.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnos.odontologia.clinica.application.RegistroPlantillas.Plantilla;
import ar.turnos.odontologia.clinica.application.RegistroPlantillas.Version;
import ar.turnos.odontologia.clinica.application.ServicioPlantillas.Edicion;
import ar.turnos.odontologia.clinica.domain.ClinicaException;
import ar.turnos.odontologia.clinica.domain.CodigoClinica;
import ar.turnos.odontologia.clinica.domain.VersionPlantilla;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Casos de uso de las plantillas de consentimiento sobre un puerto en memoria (C-27, tarea 4.1). Textos ficticios. */
class ServicioPlantillasTest {

    private static final long ADMIN = 1L;
    private static final Instant AHORA = Instant.parse("2026-11-24T13:30:00Z");
    private static final String TEXTO = "Texto ficticio de la plantilla.";

    private final PlantillasEnMemoria registro = new PlantillasEnMemoria();
    private final ServicioPlantillas servicio =
            new ServicioPlantillas(registro, Clock.fixed(AHORA, ZoneId.of("America/Argentina/Buenos_Aires")));

    @Test
    @DisplayName("Crear una plantilla")
    void crearUnaPlantilla() {
        Plantilla plantilla = servicio.crear(ADMIN, " Extracción dental ", TEXTO);

        assertThat(plantilla.activa()).isTrue();
        assertThat(plantilla.ultima().numero()).isEqualTo(1);
        assertThat(plantilla.ultima().titulo()).isEqualTo("Extracción dental");
        assertThat(plantilla.ultima().creadaEn()).isEqualTo(AHORA);
        assertThat(plantilla.ultima().creadaPor()).isEqualTo("admin");
    }

    @Test
    @DisplayName("Título repetido")
    void tituloRepetido() {
        servicio.crear(ADMIN, "Extracción dental", TEXTO);

        assertCodigo(() -> servicio.crear(ADMIN, "EXTRACCIÓN DENTAL", TEXTO), CodigoClinica.PLANTILLA_DUPLICADA);
        assertCodigo(() -> servicio.crear(ADMIN, "  extracción dental", "Otro"), CodigoClinica.PLANTILLA_DUPLICADA);
        assertThat(registro.plantillas).hasSize(1);
    }

    @Test
    @DisplayName("Una plantilla inactiva también reserva su título")
    void tituloDeUnaInactiva() {
        Plantilla blanqueamiento = servicio.crear(ADMIN, "Blanqueamiento", TEXTO);
        servicio.cambiarEstado(blanqueamiento.id(), false);

        assertCodigo(() -> servicio.crear(ADMIN, "blanqueamiento", TEXTO), CodigoClinica.PLANTILLA_DUPLICADA);
    }

    @Test
    @DisplayName("Texto demasiado largo")
    void textoDemasiadoLargo() {
        assertCodigo(() -> servicio.crear(ADMIN, "Extracción dental", "x".repeat(20_001)), CodigoClinica.DATOS_INVALIDOS);
        assertThat(registro.plantillas).isEmpty();
    }

    @Test
    @DisplayName("Editar una plantilla")
    void editarUnaPlantilla() {
        Plantilla plantilla = servicio.crear(ADMIN, "Extracción dental", TEXTO);

        Edicion edicion = servicio.editar(ADMIN, plantilla.id(), 1, "Extracción dental", "Otro texto ficticio");

        assertThat(edicion.creada()).isTrue();
        assertThat(edicion.plantilla().ultima().numero()).isEqualTo(2);
        assertThat(edicion.plantilla().ultima().texto()).isEqualTo("Otro texto ficticio");
        assertThat(servicio.versiones(plantilla.id())).extracting(Version::numero).containsExactly(2, 1);
        assertThat(servicio.version(plantilla.id(), 1).texto()).isEqualTo(TEXTO);
    }

    @Test
    @DisplayName("Edición sin cambios")
    void edicionSinCambios() {
        Plantilla plantilla = servicio.crear(ADMIN, "Extracción dental", TEXTO);

        Edicion edicion = servicio.editar(ADMIN, plantilla.id(), 1, "Extracción dental ", TEXTO + "\r\n");

        assertThat(edicion.creada()).isFalse();
        assertThat(edicion.plantilla().ultima().numero()).isEqualTo(1);
        assertThat(servicio.versiones(plantilla.id())).hasSize(1);
    }

    @Test
    @DisplayName("Edición sobre una versión vieja")
    void edicionSobreUnaVersionVieja() {
        Plantilla plantilla = servicio.crear(ADMIN, "Extracción dental", TEXTO);
        servicio.editar(ADMIN, plantilla.id(), 1, "Extracción dental", "Versión 2");

        assertCodigo(() -> servicio.editar(ADMIN, plantilla.id(), 1, "Extracción dental", "Versión 3"),
                CodigoClinica.VERSION_DESACTUALIZADA);
        assertThat(servicio.versiones(plantilla.id())).hasSize(2);
    }

    @Test
    @DisplayName("Si otra edición ganó el número, la respuesta es VERSION_DESACTUALIZADA")
    void numeroOcupado() {
        Plantilla plantilla = servicio.crear(ADMIN, "Extracción dental", TEXTO);
        registro.ocuparSiguienteNumero = true;

        assertCodigo(() -> servicio.editar(ADMIN, plantilla.id(), 1, "Extracción dental", "Otra"),
                CodigoClinica.VERSION_DESACTUALIZADA);
    }

    @Test
    @DisplayName("Editar con el título de otra plantilla")
    void editarConTituloDeOtra() {
        servicio.crear(ADMIN, "Blanqueamiento", TEXTO);
        Plantilla extraccion = servicio.crear(ADMIN, "Extracción dental", TEXTO);

        assertCodigo(() -> servicio.editar(ADMIN, extraccion.id(), 1, "BLANQUEAMIENTO", TEXTO),
                CodigoClinica.PLANTILLA_DUPLICADA);
        // Cambiar solo mayúsculas del propio título no es un duplicado.
        assertThat(servicio.editar(ADMIN, extraccion.id(), 1, "Extracción Dental", TEXTO).creada()).isTrue();
    }

    @Test
    @DisplayName("Desactivar y reactivar una plantilla")
    void desactivarYReactivar() {
        Plantilla plantilla = servicio.crear(ADMIN, "Extracción dental", TEXTO);

        assertThat(servicio.cambiarEstado(plantilla.id(), false).activa()).isFalse();
        assertThat(servicio.listar(false)).isEmpty();
        assertThat(servicio.listar(true)).extracting(Plantilla::id).containsExactly(plantilla.id());
        assertCodigo(() -> servicio.ver(plantilla.id(), true), CodigoClinica.RECURSO_NO_ENCONTRADO);
        assertThat(servicio.ver(plantilla.id(), false).activa()).isFalse();
        assertThat(servicio.cambiarEstado(plantilla.id(), true).activa()).isTrue();
        assertCodigo(() -> servicio.cambiarEstado(plantilla.id(), null), CodigoClinica.DATOS_INVALIDOS);
    }

    @Test
    @DisplayName("Plantilla inexistente")
    void plantillaInexistente() {
        assertCodigo(() -> servicio.ver(999_999L, false), CodigoClinica.RECURSO_NO_ENCONTRADO);
        assertCodigo(() -> servicio.editar(ADMIN, 999_999L, 1, "Extracción dental", TEXTO),
                CodigoClinica.RECURSO_NO_ENCONTRADO);
        assertCodigo(() -> servicio.cambiarEstado(999_999L, false), CodigoClinica.RECURSO_NO_ENCONTRADO);
        assertCodigo(() -> servicio.versiones(999_999L), CodigoClinica.RECURSO_NO_ENCONTRADO);
        assertCodigo(() -> servicio.version(999_999L, 1), CodigoClinica.RECURSO_NO_ENCONTRADO);
    }

    private static void assertCodigo(ThrowingCallable accion, CodigoClinica codigo) {
        assertThatThrownBy(accion).isInstanceOf(ClinicaException.class)
                .extracting(e -> ((ClinicaException) e).codigo()).isEqualTo(codigo);
    }

    /** Puerto en memoria con la misma semántica que el adaptador JDBC. */
    static final class PlantillasEnMemoria implements RegistroPlantillas {

        final Map<Long, Boolean> plantillas = new LinkedHashMap<>();
        final Map<Long, List<Version>> versiones = new LinkedHashMap<>();
        boolean ocuparSiguienteNumero;
        private long siguienteId = 1;

        @Override
        public long crear(Long usuarioId, Instant momento, VersionPlantilla contenido) {
            long id = siguienteId++;
            plantillas.put(id, true);
            versiones.put(id, new ArrayList<>());
            agregarVersion(id, 1, contenido, usuarioId, momento);
            return id;
        }

        @Override
        public Optional<Plantilla> buscar(long plantillaId) {
            if (!plantillas.containsKey(plantillaId)) {
                return Optional.empty();
            }
            Version ultima = versiones.get(plantillaId).stream().max(Comparator.comparingInt(Version::numero))
                    .orElseThrow();
            return Optional.of(new Plantilla(plantillaId, plantillas.get(plantillaId), ultima));
        }

        @Override
        public Optional<Plantilla> bloquearParaEditar(long plantillaId) {
            return buscar(plantillaId);
        }

        @Override
        public boolean agregarVersion(long plantillaId, int numero, VersionPlantilla contenido, Long usuarioId,
                                      Instant momento) {
            if (ocuparSiguienteNumero) {
                return false;
            }
            List<Version> lista = versiones.get(plantillaId);
            if (lista.stream().anyMatch(v -> v.numero() == numero)) {
                return false;
            }
            lista.add(new Version(siguienteId++ * 100, numero, contenido.titulo(), contenido.texto(), momento, "admin"));
            return true;
        }

        @Override
        public boolean cambiarEstado(long plantillaId, boolean activa) {
            return plantillas.computeIfPresent(plantillaId, (id, estado) -> activa) != null;
        }

        @Override
        public List<Plantilla> listar(boolean incluirInactivas) {
            return plantillas.keySet().stream().map(id -> buscar(id).orElseThrow())
                    .filter(p -> incluirInactivas || p.activa()).toList();
        }

        @Override
        public List<Version> versiones(long plantillaId) {
            return versiones.getOrDefault(plantillaId, List.of()).stream()
                    .sorted(Comparator.comparingInt(Version::numero).reversed()).toList();
        }

        @Override
        public Optional<Version> version(long plantillaId, int numero) {
            return versiones.getOrDefault(plantillaId, List.of()).stream().filter(v -> v.numero() == numero)
                    .findFirst();
        }
    }
}
