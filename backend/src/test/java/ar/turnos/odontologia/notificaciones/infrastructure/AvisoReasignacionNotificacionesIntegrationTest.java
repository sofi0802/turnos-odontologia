package ar.turnos.odontologia.notificaciones.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.agenda.disponibilidad.application.AvisoReasignacion;
import ar.turnos.odontologia.notificaciones.IntegracionNotificacionesBase;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * C-07 tarea 6.4: con C-11 en master, la reasignación de un turno afectado por una ausencia pide el aviso real de
 * {@code notificaciones} por medio del puerto {@link AvisoReasignacion}.
 */
class AvisoReasignacionNotificacionesIntegrationTest extends IntegracionNotificacionesBase {

    @Autowired
    private AvisoReasignacion aviso;

    @Test
    @DisplayName("La reasignación deja un aviso REASIGNACION para el turno nuevo")
    void reasignacionDejaUnAvisoDeReasignacion() throws Exception {
        // el puerto de la agenda lo implementa el adaptador de notificaciones
        assertThat(aviso).isInstanceOf(AvisoReasignacionNotificaciones.class);

        // GIVEN "Paciente Uno" con consentimiento y un turno con la Dra. Ana Ejemplo el 2026-12-09 a las 10:00,
        // afectado por una licencia suya, y el Dr. Bruno Prueba habilitado para "Consulta"
        contacto(pacienteUno, true, false, "paciente.uno@example.com", null);
        // La base común ya habilita a todos para "Consulta" (change validar-habilitacion-profesional).
        jdbc.update("INSERT INTO profesional_prestacion(profesional_id, prestacion_id) VALUES (?, ?) "
                + "ON CONFLICT DO NOTHING", drB, consulta);
        long original = turnoCreado(pacienteUno, draA, sillon1, diciembre(9, 10, 0)).path("id").asLong();
        MvcResult licencia = como("recepcion").post("/api/v1/profesionales/" + draA + "/ausencias",
                json.writeValueAsString(Map.of("tipo", "LICENCIA", "inicio", diciembre(7, 0, 0),
                        "fin", diciembre(12, 0, 0)))).andReturn();
        long licenciaId = cuerpo(licencia).path("periodo").path("id").asLong();

        // WHEN la recepción lo reasigna al Dr. Bruno Prueba en el mismo horario
        MvcResult resultado = como("recepcion").post("/api/v1/profesionales/" + draA + "/ausencias/" + licenciaId
                + "/turnos/" + original + "/reasignar", json.writeValueAsString(Map.of("profesionalId", drB)))
                .andReturn();

        // THEN queda una notificación REASIGNACION para el turno nuevo, y ninguna para el original
        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        long nuevo = cuerpo(resultado).path("turnoId").asLong();
        List<String> tipos = jdbc.queryForList("SELECT tipo FROM notificacion WHERE turno_id = ?", String.class, nuevo);
        assertThat(tipos).containsExactly("REASIGNACION");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE turno_id = ?", Integer.class,
                original)).isZero();
    }

    private static String diciembre(int dia, int hora, int minuto) {
        return LocalDateTime.of(2026, 12, dia, hora, minuto).atZone(BUENOS_AIRES)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
    }
}
