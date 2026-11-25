package ar.turnos.odontologia.notificaciones.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.notificaciones.CapturaDeEnvios;
import ar.turnos.odontologia.notificaciones.domain.Consultorio;
import ar.turnos.odontologia.notificaciones.domain.Enlaces;
import ar.turnos.odontologia.reserva.IntegracionReservaBase;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * C-15, delta de {@code notificaciones-recordatorios}: el aviso de la oferta con el adaptador real sobre los canales
 * capturadores de {@link CapturaDeEnvios}. PostgreSQL real para leer el contacto del paciente.
 */
@Import(CapturaDeEnvios.class)
@TestPropertySource(properties = {
        "app.notificaciones.activo=false",
        "app.notificaciones.base-url=https://turnos.example.test",
        "app.consultorio.nombre=Clínica Ejemplo",
        "app.consultorio.direccion=Calle Ficticia 123"})
class AvisoOfertaNotificacionesIntegrationTest extends IntegracionReservaBase {

    private static final String TOKEN = "tokenFicticioDeOferta_123";

    @Autowired CapturaDeEnvios.Registro envios;
    @Autowired Consultorio consultorio;
    @Autowired Enlaces enlaces;

    private AvisoOfertaNotificaciones aviso;

    @BeforeEach
    void contactosFicticios() {
        envios.reiniciar();
        aviso = new AvisoOfertaNotificaciones(jdbc, List.of(envios.whatsapp, envios.email), consultorio, enlaces);
        jdbc.update("UPDATE paciente SET consentimiento_comunicaciones = TRUE, consentimiento_fecha = now()");
        jdbc.update("UPDATE paciente SET telefono = '+5491100000001', tiene_whatsapp = TRUE WHERE id = ?", pacienteUno);
        jdbc.update("UPDATE paciente SET telefono = NULL, tiene_whatsapp = FALSE WHERE id = ?", pacienteDos);
    }

    private static Instant instante(int dia, int hora) {
        return LocalDateTime.of(2026, 12, dia, hora, 0).atZone(BUENOS_AIRES).toInstant();
    }

    @Test
    void ofertaConConsentimientoYWhatsapp() {
        boolean enviado = aviso.avisar(pacienteUno, instante(3, 10), instante(2, 10), TOKEN);

        assertThat(enviado).isTrue();
        assertThat(envios.email.enviados).isEmpty();
        assertThat(envios.whatsapp.enviados).singleElement().satisfies(envio -> {
            assertThat(envio.destino()).isEqualTo("+5491100000001");
            assertThat(envio.cuerpo())
                    .contains("03/12/2026", "10:00", "Clínica Ejemplo", "Calle Ficticia 123", "02/12/2026")
                    .contains("https://turnos.example.test/oferta/" + TOKEN)
                    .doesNotContain("Consulta", "Paciente", "Uno", "Ana", DNI_UNO);
        });
    }

    @Test
    void ofertaPorEmail() {
        boolean enviado = aviso.avisar(pacienteDos, instante(3, 10), instante(2, 10), TOKEN);

        assertThat(enviado).isTrue();
        assertThat(envios.whatsapp.enviados).isEmpty();
        assertThat(envios.email.enviados).singleElement()
                .satisfies(envio -> assertThat(envio.destino()).isEqualTo(EMAIL_DOS));
    }

    @Test
    void siWhatsappFallaSaleporEmail() {
        envios.whatsapp.falla = true;

        assertThat(aviso.avisar(pacienteUno, instante(3, 10), instante(2, 10), TOKEN)).isTrue();

        assertThat(envios.email.enviados).singleElement()
                .satisfies(envio -> assertThat(envio.destino()).isEqualTo(EMAIL_UNO));
    }

    @Test
    void sinConsentimientoNoSeEnvia() {
        jdbc.update("UPDATE paciente SET consentimiento_comunicaciones = FALSE WHERE id = ?", pacienteUno);

        assertThat(aviso.avisar(pacienteUno, instante(3, 10), instante(2, 10), TOKEN)).isFalse();

        assertThat(envios.totalEnviados()).isZero();
    }

    @Test
    void laNotificacionNoGuardaElToken() {
        aviso.avisar(pacienteUno, instante(3, 10), instante(2, 10), TOKEN);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion", Long.class)).isZero();
    }
}
