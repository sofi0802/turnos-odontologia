package ar.turnos.odontologia.clinica.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnos.odontologia.clinica.application.RegistroPeriodontal.ResumenVersion;
import ar.turnos.odontologia.clinica.application.RegistroPeriodontal.Version;
import ar.turnos.odontologia.clinica.application.ServicioPeriodontograma.PiezaPedido;
import ar.turnos.odontologia.clinica.application.ServicioPeriodontograma.SitioPedido;
import ar.turnos.odontologia.clinica.domain.ClinicaException;
import ar.turnos.odontologia.clinica.domain.CodigoClinica;
import ar.turnos.odontologia.clinica.domain.EstadoPeriodontal;
import ar.turnos.odontologia.clinica.domain.PiezaPeriodontal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/** Casos de uso del periodontograma sobre puertos en memoria (C-25, tarea 4.1). */
class ServicioPeriodontogramaTest {

    private static final long PACIENTE_UNO = 1L;
    private static final long PACIENTE_DOS = 2L;
    private static final long DRA_A = 10L;
    private static final Instant NUEVE = Instant.parse("2026-12-01T12:00:00Z");

    private final RegistroEnMemoria registro = new RegistroEnMemoria();
    private final ServicioPeriodontograma servicio = new ServicioPeriodontograma(
            new PacientesEnMemoria(List.of(PACIENTE_UNO, PACIENTE_DOS)), registro, Clock.fixed(NUEVE, ZoneOffset.UTC));

    @Test
    @DisplayName("Registrar la primera versión")
    void registrarLaPrimeraVersion() {
        Version version = servicio.registrar(PACIENTE_UNO, List.of(presente(16, 0)), DRA_A);

        assertThat(version.resumen().profesionalId()).isEqualTo(DRA_A);
        assertThat(version.resumen().registradoEn()).isEqualTo(NUEVE);
        assertThat(version.piezas()).extracting(p -> p.pieza().numero()).containsExactly(16);
        assertThat(version.piezas().get(0).estado()).isEqualTo(EstadoPeriodontal.SANO);
        assertThat(servicio.versiones(PACIENTE_UNO)).hasSize(1);
    }

    @Test
    @DisplayName("Registro sin piezas")
    void registroSinPiezas() {
        assertCodigo(() -> servicio.registrar(PACIENTE_UNO, List.of(), DRA_A), CodigoClinica.DATOS_INVALIDOS);
        assertThat(registro.versiones).isEmpty();
    }

    @Test
    @DisplayName("Pieza repetida en el mismo registro")
    void piezaRepetidaEnElMismoRegistro() {
        assertCodigo(() -> servicio.registrar(PACIENTE_UNO, List.of(presente(21, 0), presente(21, 1)), DRA_A),
                CodigoClinica.DATOS_INVALIDOS);
        assertThat(registro.versiones).isEmpty();
    }

    @Test
    @DisplayName("Paciente inexistente")
    void pacienteInexistente() {
        assertCodigo(() -> servicio.registrar(999_999L, List.of(presente(16, 0)), DRA_A),
                CodigoClinica.RECURSO_NO_ENCONTRADO);
        assertCodigo(() -> servicio.versiones(999_999L), CodigoClinica.RECURSO_NO_ENCONTRADO);
    }

    @Test
    @DisplayName("Odontólogo sin profesional vinculado")
    void odontologoSinProfesionalVinculado() {
        assertThatThrownBy(() -> servicio.registrar(PACIENTE_UNO, List.of(presente(16, 0)), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(registro.versiones).isEmpty();
    }

    @Test
    @DisplayName("Una pieza inválida no guarda ninguna de las del pedido")
    void pedidoAtomico() {
        PiezaPedido furcacionEnIncisivo = new PiezaPedido(11, false, 0, 1, seisSitios());
        assertCodigo(() -> servicio.registrar(PACIENTE_UNO, List.of(presente(16, 0), furcacionEnIncisivo), DRA_A),
                CodigoClinica.FURCACION_NO_APLICA);
        assertThat(registro.versiones).isEmpty();
    }

    @Test
    @DisplayName("Un sitio desconocido es un dato inválido")
    void sitioDesconocido() {
        List<SitioPedido> sitios = new ArrayList<>(seisSitios());
        sitios.set(0, new SitioPedido("XX", 2, -2, false, false));
        assertCodigo(() -> servicio.registrar(PACIENTE_UNO, List.of(new PiezaPedido(11, false, 0, null, sitios)),
                DRA_A), CodigoClinica.DATOS_INVALIDOS);
    }

    @Test
    @DisplayName("Las versiones se listan de la más nueva a la más vieja")
    void listarLasVersiones() {
        Version primera = servicio.registrar(PACIENTE_UNO, List.of(presente(16, 0)), DRA_A);
        ServicioPeriodontograma masTarde = new ServicioPeriodontograma(new PacientesEnMemoria(List.of(PACIENTE_UNO)),
                registro, Clock.fixed(NUEVE.plusSeconds(3600), ZoneOffset.UTC));
        Version segunda = masTarde.registrar(PACIENTE_UNO, List.of(presente(16, 1)), DRA_A);

        assertThat(servicio.versiones(PACIENTE_UNO)).extracting(ResumenVersion::id)
                .containsExactly(segunda.resumen().id(), primera.resumen().id());
    }

    @Test
    @DisplayName("Versión de otro paciente")
    void versionDeOtroPaciente() {
        Version deDos = servicio.registrar(PACIENTE_DOS, List.of(presente(16, 0)), DRA_A);
        assertCodigo(() -> servicio.version(PACIENTE_UNO, deDos.resumen().id()), CodigoClinica.RECURSO_NO_ENCONTRADO);
        assertThat(servicio.version(PACIENTE_DOS, deDos.resumen().id()).piezas()).hasSize(1);
    }

    // ---- helpers ----

    private static PiezaPedido presente(int pieza, int movilidad) {
        return new PiezaPedido(pieza, false, movilidad, null, seisSitios());
    }

    private static List<SitioPedido> seisSitios() {
        return List.of("MV", "V", "DV", "ML", "L", "DL").stream()
                .map(s -> new SitioPedido(s, 2, -2, false, false)).toList();
    }

    private static void assertCodigo(Runnable accion, CodigoClinica codigo) {
        assertThatThrownBy(accion::run)
                .isInstanceOf(ClinicaException.class)
                .extracting(e -> ((ClinicaException) e).codigo())
                .isEqualTo(codigo);
    }

    private static final class RegistroEnMemoria implements RegistroPeriodontal {

        private record Guardada(long pacienteId, Version version) {}

        private final List<Guardada> versiones = new ArrayList<>();

        @Override
        public long registrar(Long pacienteId, Long profesionalId, Instant momento, List<PiezaPeriodontal> piezas) {
            long id = versiones.size() + 1L;
            versiones.add(new Guardada(pacienteId,
                    new Version(new ResumenVersion(id, profesionalId, "Ana Ejemplo", momento), piezas)));
            return id;
        }

        @Override
        public List<ResumenVersion> versiones(Long pacienteId) {
            return versiones.stream().filter(g -> g.pacienteId() == pacienteId)
                    .map(g -> g.version().resumen())
                    .sorted(Comparator.comparing(ResumenVersion::registradoEn).reversed())
                    .toList();
        }

        @Override
        public Optional<Version> version(Long pacienteId, Long versionId) {
            return versiones.stream()
                    .filter(g -> g.pacienteId() == pacienteId && g.version().resumen().id().equals(versionId))
                    .map(Guardada::version).findFirst();
        }
    }

    /** Solo responde si el paciente existe; el resto del puerto no lo usa el periodontograma. */
    private record PacientesEnMemoria(List<Long> ids) implements RegistroClinico {

        @Override
        public boolean existePaciente(Long pacienteId) {
            return ids.contains(pacienteId);
        }

        @Override
        public Optional<Ficha> ficha(Long pacienteId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Ficha guardarFicha(Ficha ficha) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registrarCambios(Long pacienteId, Long profesionalId, Instant momento, List<Cambio> cambios) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RegistroOdontograma> vigente(Long pacienteId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RegistroOdontograma> historial(Long pacienteId) {
            throw new UnsupportedOperationException();
        }
    }
}
