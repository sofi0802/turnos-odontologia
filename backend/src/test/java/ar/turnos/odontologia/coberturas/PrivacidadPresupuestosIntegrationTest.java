package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.notificaciones.CapturaDeEnvios;
import ar.turnos.odontologia.notificaciones.application.EnviarRecordatorios.Resumen;
import ar.turnos.odontologia.notificaciones.infrastructure.ServicioNotificaciones;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Privacidad del presupuesto (C-19, spec presupuestos, tarea 7.1, RN-PR-07 y RN-NO-01): las piezas, las caras, los
 * importes y la cobertura de un presupuesto no aparecen en los recordatorios, ni en los logs, ni en los errores.
 * Reloj: 2026-11-24 a las 12:00 en Buenos Aires.
 */
@ExtendWith(OutputCaptureExtension.class)
@Import(CapturaDeEnvios.class)
@TestPropertySource(properties = {
        "app.notificaciones.activo=false",
        "app.notificaciones.base-url=https://turnos.example.test",
        "app.consultorio.nombre=Clínica Ejemplo",
        "app.consultorio.direccion=Calle Ficticia 123"})
class PrivacidadPresupuestosIntegrationTest extends IntegracionPresupuestosBase {

    @Autowired
    private ServicioNotificaciones notificaciones;

    @Autowired
    private CapturaDeEnvios.Registro envios;

    @Test
    @DisplayName("El recordatorio no lleva el presupuesto")
    void elRecordatorioNoLlevaElPresupuesto() throws Exception {
        // GIVEN "Paciente Uno" tiene un presupuesto con "Consulta o control" en la pieza 16 y un turno mañana
        semilla();
        envios.reiniciar();
        emitirComo("ana.ejemplo", pacienteUno, item(consulta, null, 16, "O", 1));
        jdbc.update("UPDATE paciente SET consentimiento_comunicaciones = true, tiene_whatsapp = true, "
                + "email = 'paciente.uno@example.com', telefono = '+54 11 5555-0001', consentimiento_fecha = now() "
                + "WHERE id = ?", pacienteUno);
        long sillon = jdbc.queryForObject("INSERT INTO sillon(nombre) VALUES ('Sillón 1') RETURNING id", Long.class);
        Instant inicio = Instant.parse("2026-11-25T10:30:00Z");
        Instant fin = inicio.plusSeconds(30 * 60);
        long turno = jdbc.queryForObject("INSERT INTO turno(profesional_id, sillon_id, prestacion_id, paciente_id, "
                + "inicio, fin, fin_con_margen, estado) VALUES (?,?,?,?,?,?,?,'RESERVADO') RETURNING id", Long.class,
                profesionalAna, sillon, consulta, pacienteUno, Timestamp.from(inicio), Timestamp.from(fin),
                Timestamp.from(fin.plusSeconds(10 * 60)));
        jdbc.update("INSERT INTO turno_auditoria(turno_id, accion, actor, fecha) VALUES (?, 'CREAR', 'recepcion', ?)",
                turno, Timestamp.from(Instant.parse("2026-11-20T15:00:00Z")));

        // WHEN se genera el recordatorio del turno
        Resumen resumen = notificaciones.enviarRecordatorios();

        // THEN el mensaje no contiene "presupuesto", la pieza 16 ni los importes
        assertThat(resumen.nuevas()).isEqualTo(1);
        assertThat(envios.totalEnviados()).isPositive();
        List<String> cuerpos = new ArrayList<>();
        envios.whatsapp.enviados.forEach(e -> cuerpos.add(e.cuerpo()));
        envios.email.enviados.forEach(e -> cuerpos.add(e.cuerpo()));
        assertThat(cuerpos).isNotEmpty().allSatisfy(cuerpo -> assertThat(cuerpo.toLowerCase())
                .doesNotContain("presupuesto", "pieza", "caries", "15000", "27500", "12500", "obra social ejemplo",
                        "plan base"));
        assertThat(cuerpos).allSatisfy(cuerpo -> assertThat(cuerpo).doesNotContainPattern("(?i)pieza\\D{0,4}16"));
    }

    @Test
    @DisplayName("Los logs no llevan el presupuesto")
    void losLogsNoLlevanElPresupuesto(CapturedOutput salida) throws Exception {
        // GIVEN el catálogo de la KB, sin precio para "Tratamiento de conducto"
        semilla();
        jdbc.update("DELETE FROM arancel WHERE prestacion_id = ?", conducto);

        // WHEN ana.ejemplo emite un presupuesto para "Paciente Tres" con "Extracción simple" en la pieza 36
        MvcResult emitido = post(como("ana.ejemplo"), presupuestosUrl(pacienteTres),
                presupuestoSolicitud(item(extraccion, null, 36, null, 1)));
        // AND después un pedido rechazado por ARANCEL_INEXISTENTE
        MvcResult sinPrecio = post(como("ana.ejemplo"), presupuestosUrl(pacienteTres),
                presupuestoSolicitud(item(conducto, null, 36, "O", 1)));
        // AND uno con una pieza inválida
        MvcResult invalido = post(como("ana.ejemplo"), presupuestosUrl(pacienteTres),
                presupuestoSolicitud(item(extraccion, null, 19, null, 1)));

        // THEN la salida de logs de los pedidos no contiene la pieza 36, 30000.00, 24000.00 ni 6000.00
        assertThat(status(emitido)).isEqualTo(201);
        assertThat(status(sinPrecio)).isEqualTo(409);
        assertThat(codigoDeError(sinPrecio)).isEqualTo("ARANCEL_INEXISTENTE");
        assertThat(status(invalido)).isEqualTo(422);
        String logs = salida.getAll();
        assertThat(logs).doesNotContain("30000", "24000", "6000.00", "60000");
        assertThat(logs).doesNotContainPattern("(?i)pieza\\D{0,6}(36|19)");
        assertThat(logs).doesNotContainPattern("(?i)cara\\D{0,6}O\\b");
        // AND los errores tampoco repiten piezas, caras ni importes recibidos
        assertThat(texto(sinPrecio)).doesNotContain("36", "30000", "24000");
        assertThat(texto(invalido)).doesNotContainPattern("\\b19\\b");
    }

    @Test
    @DisplayName("El error de un presupuesto vencido o cerrado no lleva datos del paciente")
    void losErroresDeEstadoNoLlevanDatos(CapturedOutput salida) throws Exception {
        semilla();
        long presupuesto = emitirComo("ana.ejemplo", pacienteUno, item(consulta, null, 16, "O", 1));
        post(como("ana.ejemplo"), presupuestoUrl(pacienteUno, presupuesto) + "/aceptar", null);
        MvcResult cerrado = post(como("ana.ejemplo"), presupuestoUrl(pacienteUno, presupuesto) + "/rechazar", null);
        assertThat(status(cerrado)).isEqualTo(409);
        assertThat(texto(cerrado)).doesNotContain("15000", "OSE-000001", "Plan Base", "Paciente Uno", "99000001");
        assertThat(salida.getAll()).doesNotContain("15000", "OSE-000001", "99000001");
    }
}
