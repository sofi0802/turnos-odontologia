package ar.turnos.odontologia.clinica.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnos.odontologia.clinica.application.RegistroConsentimientos.Consentimiento;
import ar.turnos.odontologia.clinica.application.RegistroConsentimientos.PlantillaParaFirmar;
import ar.turnos.odontologia.clinica.application.RegistroConsentimientos.Revocacion;
import ar.turnos.odontologia.clinica.application.ServicioConsentimientos.Verificacion;
import ar.turnos.odontologia.clinica.domain.ClinicaException;
import ar.turnos.odontologia.clinica.domain.CodigoClinica;
import ar.turnos.odontologia.clinica.domain.ConsentimientoFirmado;
import ar.turnos.odontologia.clinica.domain.EvidenciaFirma;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/**
 * Casos de uso de los consentimientos firmados sobre un puerto en memoria (C-27, tarea 4.2). Datos ficticios: "Paciente
 * Uno", DNI 99000001, IP 10.0.0.15.
 */
class ServicioConsentimientosTest {

    private static final long PACIENTE_UNO = 1L;
    private static final long PACIENTE_DOS = 2L;
    private static final long DRA_A = 10L;
    private static final long DR_B = 11L;
    private static final long EXTRACCION = 5L;
    private static final long BLANQUEAMIENTO = 6L;
    private static final Instant AHORA = Instant.parse("2026-11-24T13:30:00Z");
    private static final EvidenciaFirma EVIDENCIA = EvidenciaFirma.de("10.0.0.15", "Navegador de prueba 1.0");

    private final ConsentimientosEnMemoria registro = new ConsentimientosEnMemoria();
    private final ServicioConsentimientos servicio =
            new ServicioConsentimientos(registro, Clock.fixed(AHORA, ZoneId.of("America/Argentina/Buenos_Aires")));

    @Test
    @DisplayName("Firmar un consentimiento")
    void firmarUnConsentimiento() {
        Consentimiento firmado = firmar(PACIENTE_UNO, DRA_A, EXTRACCION, 1);

        assertThat(firmado.titulo()).isEqualTo("Extracción dental");
        assertThat(firmado.texto()).isEqualTo("Texto ficticio de la versión 1.");
        assertThat(firmado.firmanteDni()).isEqualTo("99000001");
        assertThat(firmado.profesionalId()).isEqualTo(DRA_A);
        assertThat(firmado.firmadoEn()).isEqualTo(AHORA);
        assertThat(firmado.tipoFirma()).isEqualTo("ELECTRONICA");
        assertThat(firmado.hash()).isEqualTo(firmado.contenidoCanonico().sha256());
        assertThat(firmado.revocado()).isFalse();
    }

    @Test
    @DisplayName("Paciente inexistente")
    void pacienteInexistente() {
        assertCodigo(() -> firmar(999_999L, DRA_A, EXTRACCION, 1), CodigoClinica.RECURSO_NO_ENCONTRADO);
        assertCodigo(() -> servicio.listar(999_999L), CodigoClinica.RECURSO_NO_ENCONTRADO);
        assertThat(registro.firmados).isEmpty();
    }

    @Test
    @DisplayName("Consentimiento inexistente o de otro paciente")
    void consentimientoInexistente() {
        Consentimiento firmado = firmar(PACIENTE_UNO, DRA_A, EXTRACCION, 1);

        assertCodigo(() -> servicio.ver(PACIENTE_UNO, 999_999L), CodigoClinica.RECURSO_NO_ENCONTRADO);
        assertCodigo(() -> servicio.ver(PACIENTE_DOS, firmado.id()), CodigoClinica.RECURSO_NO_ENCONTRADO);
        assertCodigo(() -> servicio.verificar(PACIENTE_DOS, firmado.id()), CodigoClinica.RECURSO_NO_ENCONTRADO);
        assertCodigo(() -> servicio.revocar(PACIENTE_DOS, firmado.id(), DRA_A, "Motivo"),
                CodigoClinica.RECURSO_NO_ENCONTRADO);
    }

    @Test
    @DisplayName("Plantilla inexistente o sin indicar")
    void plantillaInexistente() {
        assertCodigo(() -> firmar(PACIENTE_UNO, DRA_A, 999_999L, 1), CodigoClinica.RECURSO_NO_ENCONTRADO);
        assertCodigo(() -> servicio.firmar(PACIENTE_UNO, DRA_A, null, 1, "Paciente Uno", "99000001", true, EVIDENCIA),
                CodigoClinica.DATOS_INVALIDOS);
    }

    @Test
    @DisplayName("Odontólogo sin profesional vinculado")
    void odontologoSinProfesional() {
        assertThatThrownBy(() -> firmar(PACIENTE_UNO, null, EXTRACCION, 1)).isInstanceOf(AccessDeniedException.class);
        Consentimiento firmado = firmar(PACIENTE_UNO, DRA_A, EXTRACCION, 1);
        assertThatThrownBy(() -> servicio.revocar(PACIENTE_UNO, firmado.id(), null, "Motivo"))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(registro.firmados).hasSize(1);
        assertThat(registro.revocaciones).isEmpty();
    }

    @Test
    @DisplayName("Plantilla inactiva")
    void plantillaInactiva() {
        assertCodigo(() -> firmar(PACIENTE_UNO, DRA_A, BLANQUEAMIENTO, 1), CodigoClinica.PLANTILLA_INACTIVA);
        assertThat(registro.firmados).isEmpty();
    }

    @Test
    @DisplayName("Versión desactualizada")
    void versionDesactualizada() {
        registro.plantillas.put(EXTRACCION, new PlantillaParaFirmar(EXTRACCION, true, 51L, 2, "Extracción dental",
                "Texto ficticio de la versión 2."));

        assertCodigo(() -> firmar(PACIENTE_UNO, DRA_A, EXTRACCION, 1), CodigoClinica.VERSION_DESACTUALIZADA);
        assertThat(firmar(PACIENTE_UNO, DRA_A, EXTRACCION, 2).texto()).isEqualTo("Texto ficticio de la versión 2.");
    }

    @Test
    @DisplayName("DNI distinto del paciente")
    void dniDistintoDelPaciente() {
        assertCodigo(() -> servicio.firmar(PACIENTE_UNO, DRA_A, EXTRACCION, 1, "Paciente Uno", "99000002", true,
                EVIDENCIA), CodigoClinica.FIRMANTE_NO_COINCIDE);
        assertThat(registro.firmados).isEmpty();
    }

    @Test
    @DisplayName("Consentimiento íntegro")
    void consentimientoIntegro() {
        Consentimiento firmado = firmar(PACIENTE_UNO, DRA_A, EXTRACCION, 1);

        Verificacion verificacion = servicio.verificar(PACIENTE_UNO, firmado.id());

        assertThat(verificacion.integro()).isTrue();
        assertThat(verificacion.algoritmo()).isEqualTo("SHA-256");
        assertThat(verificacion.hashCalculado()).isEqualTo(verificacion.hashGuardado()).isEqualTo(firmado.hash());
        assertThat(verificacion.verificadoEn()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("Consentimiento alterado")
    void consentimientoAlterado() {
        Consentimiento firmado = firmar(PACIENTE_UNO, DRA_A, EXTRACCION, 1);
        registro.alterarTexto(firmado.id(), "Texto ficticio de la versión 1!");

        Verificacion verificacion = servicio.verificar(PACIENTE_UNO, firmado.id());

        assertThat(verificacion.integro()).isFalse();
        assertThat(verificacion.hashGuardado()).isEqualTo(firmado.hash());
        assertThat(verificacion.hashCalculado()).isNotEqualTo(firmado.hash()).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("Revocar un consentimiento")
    void revocarUnConsentimiento() {
        Consentimiento firmado = firmar(PACIENTE_UNO, DRA_A, EXTRACCION, 1);

        Consentimiento revocado = servicio.revocar(PACIENTE_UNO, firmado.id(), DR_B, " Motivo ficticio ");

        assertThat(revocado.revocacion().profesionalId()).isEqualTo(DR_B);
        assertThat(revocado.revocacion().motivo()).isEqualTo("Motivo ficticio");
        assertThat(revocado.revocacion().revocadoEn()).isEqualTo(AHORA);
        assertThat(servicio.verificar(PACIENTE_UNO, firmado.id()).integro()).isTrue();
    }

    @Test
    @DisplayName("Revocar dos veces")
    void revocarDosVeces() {
        Consentimiento firmado = firmar(PACIENTE_UNO, DRA_A, EXTRACCION, 1);
        servicio.revocar(PACIENTE_UNO, firmado.id(), DRA_A, "Primera");

        assertCodigo(() -> servicio.revocar(PACIENTE_UNO, firmado.id(), DR_B, "Segunda"),
                CodigoClinica.CONSENTIMIENTO_YA_REVOCADO);
        assertThat(registro.revocaciones.get(firmado.id()).profesionalId()).isEqualTo(DRA_A);
    }

    @Test
    @DisplayName("Si otra revocación ganó la carrera, la respuesta es CONSENTIMIENTO_YA_REVOCADO")
    void carreraDeRevocaciones() {
        Consentimiento firmado = firmar(PACIENTE_UNO, DRA_A, EXTRACCION, 1);
        registro.revocacionYaGuardada = true;

        assertCodigo(() -> servicio.revocar(PACIENTE_UNO, firmado.id(), DR_B, "Motivo"),
                CodigoClinica.CONSENTIMIENTO_YA_REVOCADO);
    }

    @Test
    @DisplayName("Revocar sin motivo")
    void revocarSinMotivo() {
        Consentimiento firmado = firmar(PACIENTE_UNO, DRA_A, EXTRACCION, 1);

        assertCodigo(() -> servicio.revocar(PACIENTE_UNO, firmado.id(), DRA_A, " "), CodigoClinica.DATOS_INVALIDOS);
        assertThat(registro.revocaciones).isEmpty();
    }

    @Test
    @DisplayName("Listar del más nuevo al más viejo")
    void listar() {
        Consentimiento primero = firmar(PACIENTE_UNO, DRA_A, EXTRACCION, 1);
        Consentimiento segundo = firmar(PACIENTE_UNO, DR_B, EXTRACCION, 1);

        assertThat(servicio.listar(PACIENTE_UNO)).extracting(Consentimiento::id)
                .containsExactly(segundo.id(), primero.id());
        assertThat(servicio.listar(PACIENTE_DOS)).isEmpty();
    }

    private Consentimiento firmar(long paciente, Long profesional, long plantilla, int version) {
        return servicio.firmar(paciente, profesional, plantilla, version, "Paciente Uno", "99.000.001", true, EVIDENCIA);
    }

    private static void assertCodigo(ThrowingCallable accion, CodigoClinica codigo) {
        assertThatThrownBy(accion).isInstanceOf(ClinicaException.class)
                .extracting(e -> ((ClinicaException) e).codigo()).isEqualTo(codigo);
    }

    /** Puerto en memoria con la misma semántica que el adaptador JDBC. */
    private static final class ConsentimientosEnMemoria implements RegistroConsentimientos {

        final Map<Long, String> dnis = Map.of(PACIENTE_UNO, "99000001", PACIENTE_DOS, "99000002");
        final Map<Long, PlantillaParaFirmar> plantillas = new HashMap<>(Map.of(
                EXTRACCION, new PlantillaParaFirmar(EXTRACCION, true, 50L, 1, "Extracción dental",
                        "Texto ficticio de la versión 1."),
                BLANQUEAMIENTO, new PlantillaParaFirmar(BLANQUEAMIENTO, false, 60L, 1, "Blanqueamiento",
                        "Texto ficticio.")));
        final Map<Long, Consentimiento> firmados = new LinkedHashMap<>();
        final Map<Long, Revocacion> revocaciones = new HashMap<>();
        boolean revocacionYaGuardada;
        private long siguienteId = 1;

        @Override
        public Optional<String> dniDelPaciente(Long pacienteId) {
            return Optional.ofNullable(dnis.get(pacienteId));
        }

        @Override
        public Optional<PlantillaParaFirmar> plantillaParaFirmar(long plantillaId) {
            return Optional.ofNullable(plantillas.get(plantillaId));
        }

        @Override
        public long registrar(ConsentimientoFirmado c, String hash) {
            long id = siguienteId++;
            long plantillaId = plantillas.values().stream().filter(p -> p.versionId() == c.plantillaVersionId())
                    .findFirst().orElseThrow().id();
            firmados.put(id, new Consentimiento(id, c.pacienteId(), plantillaId, c.plantillaVersionId(), 1,
                    c.titulo(), c.texto(), c.firmanteNombre(), c.firmanteDni(), c.profesionalId(), "Profesional",
                    c.firmadoEn(), c.ipOrigen(), c.dispositivo(), ConsentimientoFirmado.TIPO_FIRMA, 1, "SHA-256", hash,
                    null));
            return id;
        }

        void alterarTexto(long id, String texto) {
            Consentimiento c = firmados.get(id);
            firmados.put(id, new Consentimiento(c.id(), c.pacienteId(), c.plantillaId(), c.plantillaVersionId(),
                    c.versionNumero(), c.titulo(), texto, c.firmanteNombre(), c.firmanteDni(), c.profesionalId(),
                    c.profesional(), c.firmadoEn(), c.ipOrigen(), c.dispositivo(), c.tipoFirma(), c.formatoCanonico(),
                    c.hashAlgoritmo(), c.hash(), null));
        }

        @Override
        public List<Consentimiento> listar(Long pacienteId) {
            List<Consentimiento> lista = new ArrayList<>();
            firmados.values().stream().filter(c -> c.pacienteId().equals(pacienteId))
                    .forEach(c -> lista.add(conRevocacion(c)));
            lista.sort(Comparator.comparing(Consentimiento::firmadoEn).thenComparing(Consentimiento::id).reversed());
            return lista;
        }

        @Override
        public Optional<Consentimiento> buscar(Long pacienteId, Long consentimientoId) {
            return Optional.ofNullable(firmados.get(consentimientoId))
                    .filter(c -> c.pacienteId().equals(pacienteId))
                    .map(this::conRevocacion);
        }

        @Override
        public boolean revocar(Long consentimientoId, Long profesionalId, Instant momento, String motivo) {
            if (revocacionYaGuardada || revocaciones.containsKey(consentimientoId)) {
                return false;
            }
            revocaciones.put(consentimientoId, new Revocacion(profesionalId, "Profesional", momento, motivo));
            return true;
        }

        private Consentimiento conRevocacion(Consentimiento c) {
            return new Consentimiento(c.id(), c.pacienteId(), c.plantillaId(), c.plantillaVersionId(),
                    c.versionNumero(), c.titulo(), c.texto(), c.firmanteNombre(), c.firmanteDni(), c.profesionalId(),
                    c.profesional(), c.firmadoEn(), c.ipOrigen(), c.dispositivo(), c.tipoFirma(), c.formatoCanonico(),
                    c.hashAlgoritmo(), c.hash(), revocaciones.get(c.id()));
        }
    }
}
