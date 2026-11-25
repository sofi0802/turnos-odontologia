package ar.turnos.odontologia.notificaciones.infrastructure;

import ar.turnos.odontologia.agenda.listaespera.application.PuertosListaEspera.AvisoOferta;
import ar.turnos.odontologia.notificaciones.application.NotificacionPort;
import ar.turnos.odontologia.notificaciones.application.NotificacionPort.EnvioNotificacion;
import ar.turnos.odontologia.notificaciones.application.NotificacionPort.ResultadoEnvio;
import ar.turnos.odontologia.notificaciones.domain.Canal;
import ar.turnos.odontologia.notificaciones.domain.Consultorio;
import ar.turnos.odontologia.notificaciones.domain.ContactoPaciente;
import ar.turnos.odontologia.notificaciones.domain.Enlaces;
import ar.turnos.odontologia.notificaciones.domain.MensajeRecordatorio;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Aviso de la oferta de la lista de espera (C-15, design D6): las mismas reglas de consentimiento y canal que el
 * recordatorio (RN-NO-02, RN-NO-03). Si WhatsApp falla y el paciente tiene email, sale por email en el mismo intento.
 * No guarda nada en {@code notificacion}: la oferta guarda si el aviso salió y la tarea de la lista de espera lo
 * reintenta con un token nuevo, así el token en claro nunca se persiste.
 */
@Component
class AvisoOfertaNotificaciones implements AvisoOferta {

    private final JdbcTemplate jdbc;
    private final Map<Canal, NotificacionPort> canales = new EnumMap<>(Canal.class);
    private final Consultorio consultorio;
    private final Enlaces enlaces;

    AvisoOfertaNotificaciones(JdbcTemplate jdbc, List<NotificacionPort> canales, Consultorio consultorio,
                              Enlaces enlaces) {
        this.jdbc = jdbc;
        canales.forEach(canal -> this.canales.put(canal.canal(), canal));
        this.consultorio = consultorio;
        this.enlaces = enlaces;
    }

    @Override
    public boolean avisar(Long pacienteId, Instant inicio, Instant venceEn, String token) {
        Optional<ContactoPaciente> contacto = contacto(pacienteId);
        Optional<Canal> canal = contacto.flatMap(ContactoPaciente::canalDeEnvio);
        if (canal.isEmpty()) {
            return false;
        }
        String cuerpo = MensajeRecordatorio.oferta(inicio, venceEn, consultorio, enlaces, token).texto();
        if (enviar(canal.get(), contacto.get(), cuerpo)) {
            return true;
        }
        return canal.get() == Canal.WHATSAPP && contacto.get().tieneEmail()
                && enviar(Canal.EMAIL, contacto.get(), cuerpo);
    }

    private Optional<ContactoPaciente> contacto(Long pacienteId) {
        return jdbc.query("SELECT email, telefono, tiene_whatsapp, consentimiento_comunicaciones FROM paciente "
                        + "WHERE id = ?",
                (rs, fila) -> new ContactoPaciente(rs.getString(1), rs.getString(2), rs.getBoolean(3),
                        rs.getBoolean(4)), pacienteId).stream().findFirst();
    }

    private boolean enviar(Canal canal, ContactoPaciente contacto, String cuerpo) {
        NotificacionPort puerto = canales.get(canal);
        if (puerto == null) {
            return false;
        }
        try {
            return puerto.enviar(new EnvioNotificacion(null, contacto.destino(canal), cuerpo)) == ResultadoEnvio.OK;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
