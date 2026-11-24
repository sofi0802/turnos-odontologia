package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.notificaciones.IntegracionNotificacionesBase;
import ar.turnos.odontologia.notificaciones.application.EnviarRecordatorios.Resumen;
import ar.turnos.odontologia.notificaciones.infrastructure.ServicioNotificaciones;
import ar.turnos.odontologia.shared.security.Navegador;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Privacidad de la cobertura (C-18, spec afiliaciones-pacientes, tarea 6.1, RN-OS-04 y RN-OS-07): ni los recordatorios
 * ni los errores ni los logs llevan la obra social, el plan o el número de afiliado, y dar un turno no exige cobertura.
 * Parte de la base de notificaciones: reloj fijo del 2026-12-01 a las 08:00 en Buenos Aires.
 */
@Tag("integration")
@ExtendWith(OutputCaptureExtension.class)
class PrivacidadCoberturasIntegrationTest extends IntegracionNotificacionesBase {

    private static final String NUMERO = "OSE-000001";

    @Autowired
    private ServicioNotificaciones servicio;

    private long planBase;
    private long planIntegral;

    @BeforeEach
    void catalogo() {
        jdbc.execute("TRUNCATE afiliacion_paciente, nomenclador, plan, obra_social RESTART IDENTITY CASCADE");
        long ose = jdbc.queryForObject(
                "INSERT INTO obra_social(nombre, sigla, tipo) VALUES ('Obra Social Ejemplo','OSE','OBRA_SOCIAL') RETURNING id",
                Long.class);
        planBase = jdbc.queryForObject("INSERT INTO plan(obra_social_id, nombre) VALUES (?, 'Plan Base') RETURNING id",
                Long.class, ose);
        planIntegral = jdbc.queryForObject(
                "INSERT INTO plan(obra_social_id, nombre) VALUES (?, 'Plan Integral') RETURNING id", Long.class, ose);
    }

    private void afiliado(long paciente, long plan, String numero) {
        jdbc.update("INSERT INTO afiliacion_paciente(paciente_id, plan_id, obra_social_id, numero_afiliado, "
                + "vigente_desde) SELECT ?, p.id, p.obra_social_id, ?, DATE '2026-01-01' FROM plan p WHERE p.id = ?",
                paciente, numero, plan);
    }

    private MvcResult afiliar(Navegador navegador, long paciente, long plan, String numero) throws Exception {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("planId", plan);
        pedido.put("numeroAfiliado", numero);
        pedido.put("vigenteDesde", "2026-01-01");
        return navegador.post("/api/v1/pacientes/" + paciente + "/afiliaciones", json.writeValueAsString(pedido))
                .andReturn();
    }

    @Test
    @DisplayName("El recordatorio no lleva la cobertura")
    void elRecordatorioNoLlevaLaCobertura(CapturedOutput salida) {
        // GIVEN "Paciente Uno" tiene "Plan Base" vigente con el número OSE-000001 y un turno mañana
        afiliado(pacienteUno, planBase, NUMERO);
        contacto(pacienteUno, true, true, "paciente.uno@example.com", "+54 11 5555-0001");
        long turno = turno(pacienteUno, draA, sillon1, turnoDeManana(), "RESERVADO");
        reservadoEn(turno, en(2026, 11, 28, 12, 0));

        // WHEN se genera el recordatorio del turno
        Resumen resumen = servicio.enviarRecordatorios();

        // THEN el mensaje no contiene "Obra Social Ejemplo", "Plan Base" ni OSE-000001
        assertThat(resumen.nuevas()).isEqualTo(1);
        assertThat(envios.totalEnviados()).isPositive();
        java.util.List<String> cuerpos = new java.util.ArrayList<>();
        envios.whatsapp.enviados.forEach(e -> cuerpos.add(e.cuerpo()));
        envios.email.enviados.forEach(e -> cuerpos.add(e.cuerpo()));
        assertThat(cuerpos).isNotEmpty().allSatisfy(cuerpo -> assertThat(cuerpo)
                .doesNotContain("Obra Social Ejemplo", "Plan Base", NUMERO, "OSE"));
        // AND el envío no dejó la cobertura en los logs
        assertThat(salida.getAll()).doesNotContain("Obra Social Ejemplo", "Plan Base", NUMERO);
    }

    @Test
    @DisplayName("Turno de un paciente particular")
    void turnoDeUnPacienteParticular() throws Exception {
        // GIVEN "Paciente Dos" no tiene afiliaciones
        assertThat(jdbc.queryForObject("SELECT count(*) FROM afiliacion_paciente WHERE paciente_id = ?",
                Integer.class, pacienteDos)).isZero();

        // WHEN recepcion le da un turno válido
        MvcResult r = crearTurno(como("recepcion"), pacienteDos, draA, sillon1, hora(10, 0));

        // THEN el turno se crea igual que antes de C-18
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        assertThat(cuerpo(r).path("estado").asText()).isEqualTo("RESERVADO");
    }

    @Test
    @DisplayName("Un paciente afiliado también recibe su turno, sin que la cobertura cambie nada")
    void unPacienteAfiliadoTambienRecibeSuTurno() throws Exception {
        afiliado(pacienteUno, planBase, NUMERO);
        MvcResult r = crearTurno(como("recepcion"), pacienteUno, draA, sillon1, hora(11, 0));
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        assertThat(r.getResponse().getContentAsString()).doesNotContain(NUMERO, "Obra Social Ejemplo", "Plan Base");
    }

    @Test
    @DisplayName("El error de duplicado no repite el número")
    void elErrorDeDuplicadoNoRepiteElNumero(CapturedOutput salida) throws Exception {
        // GIVEN "Paciente Uno" tiene "Plan Base" con el número OSE-000001
        afiliado(pacienteUno, planBase, NUMERO);
        Navegador recepcion = como("recepcion");

        // WHEN recepcion afilia a "Paciente Dos" a "Plan Base" con el número OSE-000001 desde 2026-01-01
        MvcResult duplicado = afiliar(recepcion, pacienteDos, planBase, NUMERO);
        // AND con un número inválido
        MvcResult invalido = afiliar(recepcion, pacienteDos, planIntegral, "OSE 0001#");

        // THEN la respuesta es 409 con el código AFILIADO_DUPLICADO y su cuerpo no contiene OSE-000001
        assertThat(duplicado.getResponse().getStatus()).isEqualTo(409);
        assertThat(cuerpo(duplicado).path("codigo").asText()).isEqualTo("AFILIADO_DUPLICADO");
        assertThat(duplicado.getResponse().getContentAsString()).doesNotContain(NUMERO, "Paciente Uno", "99000001");
        // AND el 422 del número tampoco lo repite
        assertThat(invalido.getResponse().getStatus()).isEqualTo(422);
        assertThat(invalido.getResponse().getContentAsString()).doesNotContain("OSE 0001#");
        // AND ni los logs
        assertThat(salida.getAll()).doesNotContain(NUMERO, "OSE 0001#");
    }
}
