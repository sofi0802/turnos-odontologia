package ar.turnos.odontologia.clinica;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.clinica.application.RegistroConsentimientos;
import ar.turnos.odontologia.clinica.application.RegistroConsentimientos.Consentimiento;
import ar.turnos.odontologia.clinica.application.RegistroConsentimientos.PlantillaParaFirmar;
import ar.turnos.odontologia.clinica.application.RegistroPlantillas;
import ar.turnos.odontologia.clinica.domain.ConsentimientoFirmado;
import ar.turnos.odontologia.clinica.domain.ConsentimientoFirmado.Firmante;
import ar.turnos.odontologia.clinica.domain.EvidenciaFirma;
import ar.turnos.odontologia.clinica.domain.VersionPlantilla;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Adaptador JDBC de los consentimientos contra PostgreSQL real (C-27, tarea 4.2, design D4, D5 y D10). Datos ficticios.
 */
@Tag("integration")
@ActiveProfiles("test")
@SpringBootTest
class RegistroConsentimientosJdbcIntegrationTest {

    /** Con microsegundos: lo leído de la base tiene que volver a dar el mismo hash. */
    private static final Instant MOMENTO = Instant.parse("2026-11-24T13:30:00.123456Z");

    @Autowired
    private RegistroConsentimientos registro;

    @Autowired
    private RegistroPlantillas plantillas;

    @Autowired
    private JdbcTemplate jdbc;

    private long pacienteUno;
    private long pacienteDos;
    private long draA;
    private long drB;
    private long plantilla;

    @BeforeEach
    void datosFicticios() {
        jdbc.execute("TRUNCATE usuario, turno, paciente, prestacion, sillon, profesional RESTART IDENTITY CASCADE");
        draA = id("INSERT INTO profesional(nombre, apellido, matricula) VALUES ('Ana','Ejemplo','MP-0001') RETURNING id");
        drB = id("INSERT INTO profesional(nombre, apellido, matricula) VALUES ('Bruno','Prueba','MP-0002') RETURNING id");
        pacienteUno = id("INSERT INTO paciente(nombre, apellido, dni) VALUES ('Paciente','Uno','99000001') RETURNING id");
        pacienteDos = id("INSERT INTO paciente(nombre, apellido, dni) VALUES ('Paciente','Dos','99000002') RETURNING id");
        long admin = id("INSERT INTO usuario(username, password_hash, rol) VALUES ('admin','x','ADMIN') RETURNING id");
        plantilla = plantillas.crear(admin, MOMENTO,
                VersionPlantilla.nueva("Extracción dental", "Línea uno del texto ficticio.\nLínea dos."));
    }

    @Test
    @DisplayName("Lo firmado se lee igual y vuelve a dar el mismo hash")
    void loFirmadoSeLeeIgual() {
        PlantillaParaFirmar paraFirmar = registro.plantillaParaFirmar(plantilla).orElseThrow();
        ConsentimientoFirmado firmado = firmado(paraFirmar, "Navegador de prueba 1.0");

        long id = registro.registrar(firmado, firmado.hash());
        Consentimiento leido = registro.buscar(pacienteUno, id).orElseThrow();

        assertThat(leido.plantillaId()).isEqualTo(plantilla);
        assertThat(leido.plantillaVersionId()).isEqualTo(paraFirmar.versionId());
        assertThat(leido.versionNumero()).isEqualTo(1);
        assertThat(leido.titulo()).isEqualTo("Extracción dental");
        assertThat(leido.texto()).isEqualTo("Línea uno del texto ficticio.\nLínea dos.");
        assertThat(leido.firmanteNombre()).isEqualTo("Paciente Uno");
        assertThat(leido.firmanteDni()).isEqualTo("99000001");
        assertThat(leido.profesionalId()).isEqualTo(draA);
        assertThat(leido.profesional()).isEqualTo("Ana Ejemplo");
        assertThat(leido.firmadoEn()).isEqualTo(MOMENTO);
        assertThat(leido.ipOrigen()).isEqualTo("10.0.0.15");
        assertThat(leido.dispositivo()).isEqualTo("Navegador de prueba 1.0");
        assertThat(leido.tipoFirma()).isEqualTo("ELECTRONICA");
        assertThat(leido.formatoCanonico()).isEqualTo(1);
        assertThat(leido.hashAlgoritmo()).isEqualTo("SHA-256");
        assertThat(leido.hash()).isEqualTo(firmado.hash());
        assertThat(leido.contenidoCanonico().sha256()).isEqualTo(firmado.hash());
        assertThat(leido.revocado()).isFalse();
    }

    @Test
    @DisplayName("Una IPv6 y un dispositivo vacío se guardan tal cual")
    void ipv6YDispositivoVacio() {
        PlantillaParaFirmar paraFirmar = registro.plantillaParaFirmar(plantilla).orElseThrow();
        ConsentimientoFirmado firmado = ConsentimientoFirmado.firmar(pacienteUno,
                Firmante.de("Paciente Uno", "99000001", true, "99000001"), paraFirmar.versionId(),
                paraFirmar.contenido(), draA, EvidenciaFirma.de("0:0:0:0:0:0:0:1", null), Clock.fixed(MOMENTO, ZoneOffset.UTC));

        Consentimiento leido = registro.buscar(pacienteUno, registro.registrar(firmado, firmado.hash())).orElseThrow();

        assertThat(leido.ipOrigen()).isEqualTo("0:0:0:0:0:0:0:1");
        assertThat(leido.dispositivo()).isEmpty();
        assertThat(leido.contenidoCanonico().sha256()).isEqualTo(firmado.hash());
    }

    @Test
    @DisplayName("Un consentimiento de otro paciente no se encuentra")
    void consentimientoDeOtroPaciente() {
        PlantillaParaFirmar paraFirmar = registro.plantillaParaFirmar(plantilla).orElseThrow();
        ConsentimientoFirmado firmado = firmado(paraFirmar, "");
        long id = registro.registrar(firmado, firmado.hash());

        assertThat(registro.buscar(pacienteDos, id)).isEmpty();
        assertThat(registro.listar(pacienteDos)).isEmpty();
        assertThat(registro.buscar(pacienteUno, 999_999L)).isEmpty();
    }

    @Test
    @DisplayName("La revocación se registra una sola vez y no cambia el documento")
    void revocacionUnica() {
        PlantillaParaFirmar paraFirmar = registro.plantillaParaFirmar(plantilla).orElseThrow();
        ConsentimientoFirmado firmado = firmado(paraFirmar, "");
        long id = registro.registrar(firmado, firmado.hash());

        assertThat(registro.revocar(id, drB, MOMENTO.plusSeconds(60), "Motivo ficticio")).isTrue();
        assertThat(registro.revocar(id, draA, MOMENTO.plusSeconds(120), "Otro motivo")).isFalse();

        Consentimiento leido = registro.buscar(pacienteUno, id).orElseThrow();
        assertThat(leido.revocacion().profesionalId()).isEqualTo(drB);
        assertThat(leido.revocacion().profesional()).isEqualTo("Bruno Prueba");
        assertThat(leido.revocacion().revocadoEn()).isEqualTo(MOMENTO.plusSeconds(60));
        assertThat(leido.revocacion().motivo()).isEqualTo("Motivo ficticio");
        assertThat(leido.contenidoCanonico().sha256()).isEqualTo(firmado.hash());
    }

    @Test
    @DisplayName("El DNI del paciente y la plantilla para firmar")
    void dniYPlantilla() {
        assertThat(registro.dniDelPaciente(pacienteUno)).contains("99000001");
        assertThat(registro.dniDelPaciente(999_999L)).isEmpty();
        PlantillaParaFirmar paraFirmar = registro.plantillaParaFirmar(plantilla).orElseThrow();
        assertThat(paraFirmar.activa()).isTrue();
        assertThat(paraFirmar.versionNumero()).isEqualTo(1);
        assertThat(paraFirmar.titulo()).isEqualTo("Extracción dental");
        assertThat(registro.plantillaParaFirmar(999_999L)).isEmpty();
    }

    private ConsentimientoFirmado firmado(PlantillaParaFirmar paraFirmar, String userAgent) {
        return ConsentimientoFirmado.firmar(pacienteUno, Firmante.de("Paciente Uno", "99000001", true, "99000001"),
                paraFirmar.versionId(), paraFirmar.contenido(), draA, EvidenciaFirma.de("10.0.0.15", userAgent),
                Clock.fixed(MOMENTO, ZoneOffset.UTC));
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
