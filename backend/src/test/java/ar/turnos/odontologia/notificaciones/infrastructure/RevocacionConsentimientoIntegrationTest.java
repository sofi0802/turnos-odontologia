package ar.turnos.odontologia.notificaciones.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.notificaciones.IntegracionNotificacionesBase;
import ar.turnos.odontologia.notificaciones.domain.CambioConsentimiento;
import ar.turnos.odontologia.notificaciones.domain.MedioConsentimiento;
import java.sql.Timestamp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * C-17: "Revocar el consentimiento corta los avisos" del spec {@code notificaciones-recordatorios}, de punta a punta
 * con PostgreSQL real y el reloj fijo del 2026-12-01 a las 08:00. El consentimiento se cambia con el mismo servicio
 * que usan el paciente y la recepción.
 */
class RevocacionConsentimientoIntegrationTest extends IntegracionNotificacionesBase {

    @Autowired ServicioNotificaciones notificaciones;
    @Autowired ServicioConsentimiento consentimiento;

    /** Turno de mañana a las 07:30, reservado el 28/11, del paciente indicado. */
    private long turnoDeMananaDe(long paciente) {
        long turno = turno(paciente, draA, sillon1, turnoDeManana(), "RESERVADO");
        reservadoEn(turno, en(2026, 11, 28, 12, 0));
        return turno;
    }

    private int notificacionesDe(long turno) {
        return jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE turno_id = ?", Integer.class, turno);
    }

    @Test
    @DisplayName("Revocar corta los recordatorios")
    void revocarCortaLosRecordatorios() {
        contacto(pacienteUno, false, true, "paciente.uno@example.com", "+54 11 5555-0001");
        consentimiento.cambiar(pacienteUno, CambioConsentimiento.delPaciente(true));
        long turno = turnoDeMananaDe(pacienteUno);

        consentimiento.cambiar(pacienteUno, CambioConsentimiento.delPaciente(false));
        notificaciones.enviarRecordatorios();

        assertThat(envios.totalEnviados()).isZero();
        assertThat(notificacionesDe(turno)).isZero();
    }

    @Test
    @DisplayName("Revocar corta los reintentos")
    void revocarCortaLosReintentos() {
        contacto(pacienteUno, false, true, "paciente.uno@example.com", "+54 11 5555-0001");
        consentimiento.cambiar(pacienteUno, CambioConsentimiento.delPaciente(true));
        long turno = turnoDeMananaDe(pacienteUno);
        envios.whatsapp.falla = true;
        notificaciones.enviarRecordatorios();
        envios.whatsapp.falla = false;
        envios.reiniciar();
        jdbc.update("UPDATE notificacion SET proximo_intento = ? WHERE turno_id = ?",
                Timestamp.from(en(2026, 12, 1, 7, 59)), turno);
        int intentos = jdbc.queryForObject("SELECT intentos FROM notificacion WHERE turno_id = ?", Integer.class, turno);

        consentimiento.cambiar(pacienteUno, CambioConsentimiento.delPaciente(false));
        notificaciones.enviarRecordatorios();

        assertThat(envios.totalEnviados()).isZero();
        assertThat(jdbc.queryForMap("SELECT estado, intentos FROM notificacion WHERE turno_id = ?", turno))
                .containsEntry("estado", "FALLIDA").containsEntry("intentos", intentos);
    }

    @Test
    @DisplayName("El consentimiento registrado por la recepción habilita los avisos")
    void elConsentimientoRegistradoPorLaRecepcionHabilitaLosAvisos() {
        contacto(pacienteDos, false, false, "paciente.dos@example.com", null);
        long recepcion = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'recepcion'", Long.class);
        long turno = turnoDeMananaDe(pacienteDos);

        consentimiento.cambiar(pacienteDos,
                CambioConsentimiento.deLaRecepcion(true, MedioConsentimiento.TELEFONO, recepcion));
        notificaciones.enviarRecordatorios();

        assertThat(envios.email.enviados).hasSize(1);
        assertThat(envios.whatsapp.enviados).isEmpty();
        assertThat(jdbc.queryForObject("SELECT canal FROM notificacion WHERE turno_id = ?", String.class, turno))
                .isEqualTo("EMAIL");
    }
}
