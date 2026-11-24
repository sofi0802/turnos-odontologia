package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.shared.security.Navegador;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Cobertura vigente o particular (C-18, spec afiliaciones-pacientes, tarea 5.1). Salvo que el escenario diga otra
 * cosa, hoy es 2026-11-24 en {@code America/Argentina/Buenos_Aires}.
 */
@Tag("integration")
class CoberturaIntegrationTest extends IntegracionCoberturasBase {

    private long planBase;
    private long planOro;

    @BeforeEach
    void catalogo() {
        long ose = obraSocial("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL");
        planBase = plan(ose, "Plan Base");
        long prepaga = obraSocial("Prepaga Prueba", null, "PREPAGA");
        planOro = plan(prepaga, "Plan Oro");
    }

    @Test
    @DisplayName("Paciente con afiliación vigente hoy")
    void pacienteConAfiliacionVigenteHoy() throws Exception {
        // GIVEN "Paciente Uno" tiene "Plan Base" con el número OSE-000001 desde 2026-01-01 sin fecha de fin
        afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);

        // WHEN recepcion consulta la cobertura de "Paciente Uno"
        MvcResult r = get(como("recepcion"), cobertura(pacienteUno));

        // THEN la respuesta es 200 con "Obra Social Ejemplo", "Plan Base", el número OSE-000001 y no particular
        assertThat(status(r)).isEqualTo(200);
        JsonNode c = cuerpo(r);
        assertThat(c.path("particular").asBoolean()).isFalse();
        assertThat(c.path("afiliacion").path("obraSocial").path("nombre").asText()).isEqualTo("Obra Social Ejemplo");
        assertThat(c.path("afiliacion").path("obraSocial").path("sigla").asText()).isEqualTo("OSE");
        assertThat(c.path("afiliacion").path("plan").path("nombre").asText()).isEqualTo("Plan Base");
        assertThat(c.path("afiliacion").path("numeroAfiliado").asText()).isEqualTo("OSE-000001");
        assertThat(c.path("afiliacion").path("vigenteDesde").asText()).isEqualTo("2026-01-01");
        assertThat(c.path("afiliacion").path("vigente").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Paciente sin cobertura")
    void pacienteSinCobertura() throws Exception {
        // WHEN recepcion consulta la cobertura de "Paciente Dos", que no tiene afiliaciones
        MvcResult r = get(como("recepcion"), cobertura(pacienteDos));

        // THEN la respuesta es 200 e indica que se atiende como particular
        assertThat(status(r)).isEqualTo(200);
        assertThat(cuerpo(r).path("particular").asBoolean()).isTrue();
        assertThat(cuerpo(r).path("afiliacion").isNull()).isTrue();
    }

    @Test
    @DisplayName("Afiliación vencida")
    void afiliacionVencida() throws Exception {
        // GIVEN "Paciente Dos" tuvo "Plan Oro" desde 2026-01-01 hasta 2026-10-31
        afiliacion(pacienteDos, planOro, "PP-77", "2026-01-01", "2026-10-31");

        // WHEN recepcion consulta la cobertura de "Paciente Dos"
        MvcResult r = get(como("recepcion"), cobertura(pacienteDos));

        // THEN la respuesta es 200 e indica que se atiende como particular
        assertThat(status(r)).isEqualTo(200);
        assertThat(cuerpo(r).path("particular").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Cobertura en una fecha pasada")
    void coberturaEnUnaFechaPasada() throws Exception {
        // GIVEN "Paciente Dos" tuvo "Plan Oro" desde 2026-01-01 hasta 2026-10-31
        afiliacion(pacienteDos, planOro, "PP-77", "2026-01-01", "2026-10-31");
        Navegador recepcion = como("recepcion");

        // WHEN recepcion consulta la cobertura de "Paciente Dos" en la fecha 2026-10-31
        MvcResult r = get(recepcion, cobertura(pacienteDos) + "?fecha=2026-10-31");

        // THEN la respuesta es 200 con "Plan Oro"
        assertThat(status(r)).isEqualTo(200);
        assertThat(cuerpo(r).path("particular").asBoolean()).isFalse();
        assertThat(cuerpo(r).path("afiliacion").path("plan").path("nombre").asText()).isEqualTo("Plan Oro");
        // AND un día después ya es particular, y un día antes del inicio también
        assertThat(cuerpo(get(recepcion, cobertura(pacienteDos) + "?fecha=2026-11-01")).path("particular").asBoolean())
                .isTrue();
        assertThat(cuerpo(get(recepcion, cobertura(pacienteDos) + "?fecha=2025-12-31")).path("particular").asBoolean())
                .isTrue();
        assertThat(cuerpo(get(recepcion, cobertura(pacienteDos) + "?fecha=2026-01-01")).path("particular").asBoolean())
                .isFalse();
    }

    @Test
    @DisplayName("Hoy se calcula en la zona de Buenos Aires")
    void hoySeCalculaEnLaZonaDeBuenosAires() throws Exception {
        // GIVEN "Paciente Dos" tiene "Plan Oro" desde 2026-11-25 y el reloj marca 2026-11-25T02:00Z (23:00 del 24 en Buenos Aires)
        afiliacion(pacienteDos, planOro, "PP-77", "2026-11-25", null);
        reloj.ahoraEs(Instant.parse("2026-11-25T02:00:00Z"));
        Navegador recepcion = como("recepcion");

        // WHEN recepcion consulta la cobertura de "Paciente Dos" sin fecha
        MvcResult r = get(recepcion, cobertura(pacienteDos));

        // THEN la respuesta indica que se atiende como particular
        assertThat(status(r)).isEqualTo(200);
        assertThat(cuerpo(r).path("particular").asBoolean()).isTrue();
        // AND pasada la medianoche de Buenos Aires ya es "Plan Oro"
        reloj.ahoraEs(Instant.parse("2026-11-25T03:00:00Z"));
        assertThat(cuerpo(get(recepcion, cobertura(pacienteDos))).path("afiliacion").path("plan").path("nombre").asText())
                .isEqualTo("Plan Oro");
    }

    @Test
    @DisplayName("El odontólogo consulta la cobertura")
    void elOdontologoConsultaLaCobertura() throws Exception {
        // GIVEN "Paciente Uno" tiene "Plan Base" vigente
        afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);

        // WHEN ana.ejemplo consulta la cobertura de "Paciente Uno"
        MvcResult r = get(como("ana.ejemplo"), cobertura(pacienteUno));

        // THEN la respuesta es 200 con "Plan Base"
        assertThat(status(r)).isEqualTo(200);
        assertThat(cuerpo(r).path("afiliacion").path("plan").path("nombre").asText()).isEqualTo("Plan Base");
    }

    @Test
    @DisplayName("Desactivar el plan no cambia la cobertura vigente")
    void desactivarElPlanNoCambiaLaCoberturaVigente() throws Exception {
        // GIVEN "Paciente Uno" tiene "Plan Base" vigente desde 2026-01-01
        afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);

        // WHEN admin desactiva "Plan Base" y su obra social y recepcion consulta la cobertura de "Paciente Uno"
        jdbc.update("UPDATE plan SET activo = FALSE");
        jdbc.update("UPDATE obra_social SET activa = FALSE");
        MvcResult r = get(como("recepcion"), cobertura(pacienteUno));

        // THEN la cobertura sigue siendo "Plan Base" con el número OSE-000001
        assertThat(cuerpo(r).path("afiliacion").path("plan").path("nombre").asText()).isEqualTo("Plan Base");
        assertThat(cuerpo(r).path("afiliacion").path("numeroAfiliado").asText()).isEqualTo("OSE-000001");
    }

    @Test
    @DisplayName("Una afiliación futura todavía no es la cobertura")
    void unaAfiliacionFuturaTodaviaNoEsLaCobertura() throws Exception {
        afiliacion(pacienteUno, planBase, "OSE-000001", "2027-01-01", null);
        MvcResult r = get(como("recepcion"), cobertura(pacienteUno));
        assertThat(cuerpo(r).path("particular").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Paciente inexistente y fecha inválida")
    void pacienteInexistenteYFechaInvalida() throws Exception {
        Navegador recepcion = como("recepcion");
        MvcResult inexistente = get(recepcion, cobertura(999999));
        MvcResult fechaMala = get(recepcion, cobertura(pacienteUno) + "?fecha=ayer");

        assertThat(status(inexistente)).isEqualTo(404);
        assertThat(codigoDeError(inexistente)).isEqualTo("RECURSO_NO_ENCONTRADO");
        assertThat(status(fechaMala)).isEqualTo(422);
        assertThat(codigoDeError(fechaMala)).isEqualTo("DATOS_INVALIDOS");
    }
}
