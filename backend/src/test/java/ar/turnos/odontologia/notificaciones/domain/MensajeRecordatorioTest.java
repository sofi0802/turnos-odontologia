package ar.turnos.odontologia.notificaciones.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Escenarios de "Mensaje sin datos de salud" del spec {@code notificaciones-recordatorios} (C-11, RN-NO-01, regla dura
 * 6) y plantillas de los avisos de la reserva online. Los mensajes llevan solo fecha, hora, consultorio y links:
 * ni nombres, ni prestación, ni motivo, ni número de reserva, ni DNI. El turno es el 2026-12-02 a las 07:30 en Buenos
 * Aires (10:30 UTC).
 */
class MensajeRecordatorioTest {

    private static final Instant SIETE_Y_MEDIA = Instant.parse("2026-12-02T10:30:00Z");
    private static final Consultorio CONSULTORIO = new Consultorio("Clínica Ejemplo", "Calle Ficticia 123");
    private static final Enlaces ENLACES = Enlaces.de("https://turnos.example.test");
    private static final String CONFIRMAR = "https://turnos.example.test/confirmar/abc123";
    private static final String CANCELAR = "https://turnos.example.test/mis-turnos/cancelar";
    private static final String REPROGRAMAR = "https://turnos.example.test/mis-turnos/reprogramar";

    @Test
    @DisplayName("Contenido del recordatorio")
    void contenidoDelRecordatorio() {
        // GIVEN un turno de "Paciente Uno" de "Consulta" con la Dra. A el 2026-12-02 a las 07:30
        // (el dato de entrada no tiene dónde poner el paciente, el profesional ni la prestación)
        DatosDelTurno turno = new DatosDelTurno(SIETE_Y_MEDIA);

        // WHEN se arma el recordatorio
        String texto = MensajeRecordatorio.recordatorio(turno, CONSULTORIO, ENLACES, "abc123").texto();

        // THEN el mensaje contiene la fecha, la hora, el consultorio, su dirección y los links de confirmar,
        // cancelar y reprogramar
        assertThat(texto).contains("02/12/2026", "07:30", "Clínica Ejemplo", "Calle Ficticia 123", CONFIRMAR,
                CANCELAR, REPROGRAMAR);
        // AND no contiene "Consulta" ni ningún otro nombre de prestación
        assertThat(texto).doesNotContain("Consulta ");
        // AND no contiene el nombre de "Paciente Uno" ni el de la Dra. A
        assertThat(texto).doesNotContain("Paciente Uno", "Dra. A");
    }

    @Test
    @DisplayName("Un sobreturno no filtra su motivo")
    void unSobreturnoNoFiltraSuMotivo() {
        // GIVEN un sobreturno de "Paciente Uno" con un motivo administrativo (el read model no lo trae)
        String motivo = "Paciente derivado por la obra social";
        TurnoARecordar sobreturno = new TurnoARecordar(7L, SIETE_Y_MEDIA, Instant.parse("2026-11-28T12:00:00Z"), true,
                new ContactoPaciente("paciente.uno@example.com", null, false, true));

        // WHEN se arma el recordatorio
        String texto = MensajeRecordatorio.recordatorio(sobreturno.datosDelTurno(), CONSULTORIO, ENLACES, "abc123")
                .texto();

        // THEN el mensaje no contiene el motivo
        assertThat(texto).doesNotContain(motivo);
    }

    @Test
    void ningunRegistroDeLecturaNiPlantillaTieneCamposDeNombreNiDeSaludNiDeCredenciales() throws Exception {
        for (Class<? extends Record> tipo : List.of(DatosDelTurno.class, TurnoARecordar.class,
                ContactoPaciente.class)) {
            assertThat(Arrays.stream(tipo.getRecordComponents()).map(RecordComponent::getName))
                    .as("campos de %s", tipo.getSimpleName())
                    .noneMatch(c -> {
                        String minuscula = c.toLowerCase();
                        return minuscula.contains("nombre") || minuscula.contains("apellido")
                                || minuscula.contains("prestacion") || minuscula.contains("motivo")
                                || minuscula.contains("clinic") || minuscula.contains("profesional");
                    });
        }
        // los links se arman solo con la dirección base y las rutas: nada del turno ni del paciente
        assertThat(Arrays.stream(Enlaces.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("baseUrl", "rutaCancelar", "rutaReprogramar");
        // las plantillas solo reciben datos del consultorio, fecha, hora y links
        assertThat(placeholders("PLANTILLA_RECORDATORIO")).isEqualTo(7);
        assertThat(placeholders("PLANTILLA_CONFIRMACION")).isEqualTo(6);
        assertThat(placeholders("PLANTILLA_CANCELACION")).isEqualTo(4);
        assertThat(placeholders("PLANTILLA_REASIGNACION")).isEqualTo(4);
    }

    @Test
    void fechaYHoraEnLaZonaDeBuenosAires() {
        // 2026-12-03T01:30Z es el 2 de diciembre a las 22:30 en Buenos Aires (UTC-3), no el 3
        DatosDelTurno nocturno = new DatosDelTurno(Instant.parse("2026-12-03T01:30:00Z"));

        assertThat(MensajeRecordatorio.recordatorio(nocturno, CONSULTORIO, ENLACES, "abc123").texto())
                .contains("02/12/2026", "22:30");
    }

    @Test
    void elAvisoDeReasignacionTraeLaNuevaFechaYHoraSinNombresNiLink() {
        DatosDelTurno reasignado = new DatosDelTurno(Instant.parse("2026-12-03T13:00:00Z"));

        String texto = MensajeRecordatorio.reasignacion(reasignado, CONSULTORIO).texto();

        assertThat(texto).contains("03/12/2026", "10:00", "Clínica Ejemplo", "Calle Ficticia 123")
                .doesNotContain("http", "Paciente Uno", "Dr. B");
    }

    @Test
    void elAvisoDeReservaTraeFechaHoraConsultorioYLosLinksDeCancelarYReprogramarSinConfirmar() {
        String texto = MensajeRecordatorio.confirmacion(new DatosDelTurno(SIETE_Y_MEDIA), CONSULTORIO, ENLACES).texto();

        assertThat(texto).contains("02/12/2026", "07:30", "Clínica Ejemplo", "Calle Ficticia 123", CANCELAR,
                REPROGRAMAR);
        assertThat(texto).doesNotContain("/confirmar/", "Paciente", "Dr", "Consulta ", "reserva N", "DNI");
    }

    @Test
    void elAvisoDeCancelacionInformaFechaHoraYConsultorioSinLinks() {
        String texto = MensajeRecordatorio.cancelacion(new DatosDelTurno(SIETE_Y_MEDIA), CONSULTORIO).texto();

        assertThat(texto).contains("cancelado", "02/12/2026", "07:30", "Clínica Ejemplo", "Calle Ficticia 123")
                .doesNotContain("http", "Paciente", "Dr", "Consulta ");
    }

    private static int placeholders(String nombre) throws ReflectiveOperationException {
        Field plantilla = MensajeRecordatorio.class.getDeclaredField(nombre);
        assertThat(Modifier.isStatic(plantilla.getModifiers())).isTrue();
        plantilla.setAccessible(true);
        return ((String) plantilla.get(null)).split("%s", -1).length - 1;
    }
}
