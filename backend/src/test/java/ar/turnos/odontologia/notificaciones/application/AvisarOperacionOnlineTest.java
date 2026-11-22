package ar.turnos.odontologia.notificaciones.application;

import static ar.turnos.odontologia.notificaciones.application.NotificacionesEnMemoria.ahora;
import static ar.turnos.odontologia.notificaciones.application.NotificacionesEnMemoria.en;
import static ar.turnos.odontologia.notificaciones.application.NotificacionesEnMemoria.soloEmail;
import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.notificaciones.application.NotificacionesEnMemoria.CanalEnMemoria;
import ar.turnos.odontologia.notificaciones.application.NotificacionesEnMemoria.RelojMutable;
import ar.turnos.odontologia.notificaciones.application.NotificacionesEnMemoria.RepositorioEnMemoria;
import ar.turnos.odontologia.notificaciones.application.NotificacionesEnMemoria.TokensSecuenciales;
import ar.turnos.odontologia.notificaciones.application.NotificacionesEnMemoria.TurnosEnMemoria;
import ar.turnos.odontologia.notificaciones.domain.Canal;
import ar.turnos.odontologia.notificaciones.domain.Consultorio;
import ar.turnos.odontologia.notificaciones.domain.ContactoPaciente;
import ar.turnos.odontologia.notificaciones.domain.Enlaces;
import ar.turnos.odontologia.notificaciones.domain.EstadoNotificacion;
import ar.turnos.odontologia.notificaciones.domain.Notificacion;
import ar.turnos.odontologia.notificaciones.domain.TipoNotificacion;
import ar.turnos.odontologia.notificaciones.domain.TurnoARecordar;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Escenarios de los avisos de la reserva online del spec {@code notificaciones-recordatorios} (C-11, acordado con C-10),
 * con dobles en memoria. "Ahora" es el 2026-12-01 a las 08:00; el turno de "Paciente Uno" es el 2026-12-02 a las 10:00.
 */
class AvisarOperacionOnlineTest {

    private static final Instant TURNO = en(2026, 12, 2, 10, 0);
    private static final Consultorio CONSULTORIO = new Consultorio("Clínica Ejemplo", "Calle Ficticia 123");
    private static final Enlaces ENLACES = Enlaces.de("https://turnos.example.test");
    private static final String CANCELAR = "https://turnos.example.test/mis-turnos/cancelar";
    private static final String REPROGRAMAR = "https://turnos.example.test/mis-turnos/reprogramar";

    private final RelojMutable reloj = new RelojMutable(ahora());
    private final TurnosEnMemoria turnos = new TurnosEnMemoria();
    private final RepositorioEnMemoria repositorio = new RepositorioEnMemoria(turnos);
    private final CanalEnMemoria whatsapp = new CanalEnMemoria(Canal.WHATSAPP);
    private final CanalEnMemoria email = new CanalEnMemoria(Canal.EMAIL);
    private final AvisarOperacionOnline casoDeUso = new AvisarOperacionOnline(turnos, repositorio,
            List.of(whatsapp, email), CONSULTORIO, ENLACES, reloj);

    private void turno(long id, Instant inicio, boolean activo, ContactoPaciente contacto) {
        turnos.guardar(new TurnoARecordar(id, inicio, en(2026, 11, 28, 12, 0), activo, contacto));
    }

    private static ContactoPaciente sinConsentimiento() {
        return new ContactoPaciente("paciente.uno@example.com", "+54 11 5555-0001", true, false);
    }

    private static ContactoPaciente soloWhatsappConsentido() {
        return new ContactoPaciente(null, "+54 11 5555-0001", true, true);
    }

    // ---------- Requirement: Aviso de la reserva online ----------

    @Test
    @DisplayName("Reserva online con consentimiento")
    void reservaOnlineConConsentimiento() {
        // GIVEN "Paciente Uno" con consentimiento y email que reservó online un turno el 2026-12-02 a las 10:00
        turno(1, TURNO, true, soloEmail());

        // WHEN la reserva online pide el aviso
        casoDeUso.reservado(1L);

        // THEN se envía un mensaje por email con la fecha, la hora, el consultorio y los links de cancelar y reprogramar
        assertThat(email.enviados).hasSize(1);
        assertThat(email.enviados.get(0).destino()).isEqualTo("paciente.uno@example.com");
        String cuerpo = email.enviados.get(0).cuerpo();
        assertThat(cuerpo).contains("02/12/2026", "10:00", "Clínica Ejemplo", "Calle Ficticia 123", CANCELAR,
                REPROGRAMAR);
        // AND el mensaje no contiene el número de reserva, el DNI, el nombre del paciente ni el del profesional
        assertThat(cuerpo).doesNotContain("ABCD-2345", "99000001", "Paciente", "Dra. A", "/confirmar/");
        Notificacion aviso = repositorio.unica();
        assertThat(aviso.tipo()).isEqualTo(TipoNotificacion.CONFIRMACION);
        assertThat(aviso.estado()).isEqualTo(EstadoNotificacion.ENVIADA);
        assertThat(aviso.tokenConfirmacion()).isNull();
    }

    @Test
    @DisplayName("Reserva online sin consentimiento")
    void reservaOnlineSinConsentimiento() {
        // GIVEN "Paciente Uno" sin consentimiento de comunicaciones que reservó online un turno
        turno(1, TURNO, true, sinConsentimiento());

        // WHEN la reserva online pide el aviso
        casoDeUso.reservado(1L);

        // THEN no se envía ningún mensaje
        assertThat(email.enviados).isEmpty();
        assertThat(whatsapp.enviados).isEmpty();
        assertThat(repositorio.notificaciones).isEmpty();
    }

    // ---------- Requirement: Aviso de la cancelación online ----------

    @Test
    @DisplayName("Cancelación online con consentimiento")
    void cancelacionOnlineConConsentimiento() {
        // GIVEN "Paciente Uno" con consentimiento y WhatsApp que canceló online su turno del 2026-12-02 a las 10:00
        // (el turno ya está cancelado cuando se avisa)
        turno(1, TURNO, false, soloWhatsappConsentido());

        // WHEN la cancelación online pide el aviso
        casoDeUso.cancelado(1L);

        // THEN se envía un mensaje por WhatsApp que informa la cancelación con la fecha, la hora y el consultorio
        assertThat(whatsapp.enviados).hasSize(1);
        String cuerpo = whatsapp.enviados.get(0).cuerpo();
        assertThat(cuerpo).contains("cancelado", "02/12/2026", "10:00", "Clínica Ejemplo", "Calle Ficticia 123");
        // AND el mensaje no contiene links ni datos de salud
        assertThat(cuerpo).doesNotContain("http", "Consulta ", "Paciente");
        Notificacion aviso = repositorio.unica();
        assertThat(aviso.tipo()).isEqualTo(TipoNotificacion.CANCELACION);
        assertThat(aviso.estado()).isEqualTo(EstadoNotificacion.ENVIADA);
        assertThat(aviso.tokenConfirmacion()).isNull();
    }

    @Test
    @DisplayName("Cancelación online sin consentimiento")
    void cancelacionOnlineSinConsentimiento() {
        // GIVEN "Paciente Uno" sin consentimiento que canceló online su turno
        turno(1, TURNO, false, sinConsentimiento());

        // WHEN la cancelación online pide el aviso
        casoDeUso.cancelado(1L);

        // THEN no se envía ningún mensaje
        assertThat(whatsapp.enviados).isEmpty();
        assertThat(email.enviados).isEmpty();
        assertThat(repositorio.notificaciones).isEmpty();
    }

    // ---------- Requirement: Aviso de la reprogramación online ----------

    @Test
    @DisplayName("Reprogramación online con consentimiento")
    void reprogramacionOnlineConConsentimiento() {
        // GIVEN "Paciente Uno" con consentimiento y email que reprogramó online su turno al 2026-12-03 a las 11:00
        // (el turno anterior, del 2026-12-02, quedó cancelado)
        turno(1, TURNO, false, soloEmail());
        turno(2, en(2026, 12, 3, 11, 0), true, soloEmail());

        // WHEN la reprogramación online pide el aviso para el turno nuevo
        casoDeUso.reprogramado(2L);

        // THEN se envía un mensaje con la fecha y la hora nuevas y los links de cancelar y reprogramar
        assertThat(email.enviados).hasSize(1);
        String cuerpo = email.enviados.get(0).cuerpo();
        assertThat(cuerpo).contains("03/12/2026", "11:00", CANCELAR, REPROGRAMAR);
        // AND el mensaje no menciona la fecha del turno anterior ni datos de salud
        assertThat(cuerpo).doesNotContain("02/12/2026", "10:00", "Consulta ", "Paciente");
        // y solo se avisa del turno nuevo
        assertThat(repositorio.unica().turnoId()).isEqualTo(2L);
    }

    // ---------- Requirement: Un aviso online fallido queda para reintento ----------

    @Test
    @DisplayName("Falla el envío del aviso de una reserva")
    void fallaElEnvioDelAvisoDeUnaReserva() {
        // GIVEN "Paciente Uno" con consentimiento y email, y un proveedor que falla
        turno(1, TURNO, true, soloEmail());
        email.falla = true;

        // WHEN la reserva online pide el aviso (no lanza: la reserva ya está confirmada)
        casoDeUso.reservado(1L);

        // THEN la notificación queda FALLIDA con un reintento programado
        Notificacion aviso = repositorio.unica();
        assertThat(aviso.estado()).isEqualTo(EstadoNotificacion.FALLIDA);
        assertThat(aviso.proximoIntento()).isEqualTo(ahora().plus(Duration.ofMinutes(5)));
        assertThat(aviso.tokenConfirmacion()).isNull();
    }

    @Test
    void sinNingunContactoNoSeEnvia() {
        turno(1, TURNO, true, new ContactoPaciente(null, null, false, true));

        casoDeUso.reservado(1L);
        casoDeUso.cancelado(1L);

        assertThat(repositorio.notificaciones).isEmpty();
    }

    @Test
    void unTurnoInexistenteNoGeneraNada() {
        casoDeUso.reservado(99L);

        assertThat(repositorio.notificaciones).isEmpty();
    }

    @Test
    void unAvisoDeReservaFallidoSeReintentaConLaMismaPoliticaYSinToken() {
        turno(1, TURNO, true, soloEmail());
        email.falla = true;
        casoDeUso.reservado(1L);
        email.falla = false;

        reloj.adelantarMinutos(5);
        new EnviarRecordatorios(turnos, repositorio, List.of(whatsapp, email), new TokensSecuenciales(), CONSULTORIO,
                ENLACES, reloj).ejecutar();

        Notificacion aviso = repositorio.deTipo(TipoNotificacion.CONFIRMACION).get(0);
        assertThat(aviso.estado()).isEqualTo(EstadoNotificacion.ENVIADA);
        assertThat(aviso.tokenConfirmacion()).isNull();
        assertThat(email.enviados).hasSize(1);
        assertThat(email.enviados.get(0).cuerpo()).contains(CANCELAR, REPROGRAMAR).doesNotContain("/confirmar/");
    }

    @Test
    void unAvisoDeCancelacionFallidoSeReintentaAunqueElTurnoYaEstaCancelado() {
        turno(1, TURNO, false, soloWhatsappConsentido());
        whatsapp.falla = true;
        casoDeUso.cancelado(1L);
        whatsapp.falla = false;

        reloj.adelantarMinutos(5);
        new EnviarRecordatorios(turnos, repositorio, List.of(whatsapp, email), new TokensSecuenciales(), CONSULTORIO,
                ENLACES, reloj).ejecutar();

        assertThat(whatsapp.enviados).hasSize(1);
        assertThat(repositorio.unica().estado()).isEqualTo(EstadoNotificacion.ENVIADA);
        assertThat(repositorio.unica().tokenConfirmacion()).isNull();
    }

    @Test
    void unAvisoDeReservaFallidoNoSeReintentaSiElTurnoSeCancelo() {
        turno(1, TURNO, true, soloEmail());
        email.falla = true;
        casoDeUso.reservado(1L);
        email.falla = false;
        turno(1, TURNO, false, soloEmail());

        reloj.adelantarMinutos(5);
        new EnviarRecordatorios(turnos, repositorio, List.of(whatsapp, email), new TokensSecuenciales(), CONSULTORIO,
                ENLACES, reloj).ejecutar();

        assertThat(email.enviados).isEmpty();
        assertThat(repositorio.unica().proximoIntento()).isNull();
    }
}
