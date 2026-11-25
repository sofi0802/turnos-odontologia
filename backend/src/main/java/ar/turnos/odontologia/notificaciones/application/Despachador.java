package ar.turnos.odontologia.notificaciones.application;

import ar.turnos.odontologia.notificaciones.application.NotificacionPort.EnvioNotificacion;
import ar.turnos.odontologia.notificaciones.application.NotificacionPort.ResultadoEnvio;
import ar.turnos.odontologia.notificaciones.domain.Canal;
import ar.turnos.odontologia.notificaciones.domain.Consultorio;
import ar.turnos.odontologia.notificaciones.domain.Enlaces;
import ar.turnos.odontologia.notificaciones.domain.MensajeRecordatorio;
import ar.turnos.odontologia.notificaciones.domain.Notificacion;
import ar.turnos.odontologia.notificaciones.domain.TurnoARecordar;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Arma el mensaje de una notificación, lo manda por su canal y devuelve la notificación con el resultado. Lo usan
 * el recordatorio, el reintento y el aviso de reasignación: el armado y el envío viven en un solo lugar.
 */
final class Despachador {

    private final Map<Canal, NotificacionPort> puertos = new EnumMap<>(Canal.class);
    private final Consultorio consultorio;
    private final Enlaces enlaces;

    Despachador(List<NotificacionPort> canales, Consultorio consultorio, Enlaces enlaces) {
        canales.forEach(p -> puertos.put(p.canal(), p));
        this.consultorio = Objects.requireNonNull(consultorio, "consultorio");
        this.enlaces = Objects.requireNonNull(enlaces, "enlaces");
    }

    /**
     * @param token el token en claro del link de confirmación; {@code null} si el tipo no lleva token
     * @return la notificación {@code ENVIADA}, o {@code FALLIDA} con su próximo intento
     */
    Notificacion despachar(Notificacion notificacion, TurnoARecordar turno, String token, Instant ahora) {
        EnvioNotificacion envio = new EnvioNotificacion(notificacion.id(),
                turno.contacto().destino(notificacion.canal()), cuerpo(notificacion, turno, token));
        return enviar(notificacion.canal(), envio)
                ? notificacion.enviada(ahora)
                : notificacion.fallida(ahora, turno.contacto().tieneEmail());
    }

    private String cuerpo(Notificacion notificacion, TurnoARecordar turno, String token) {
        return switch (notificacion.tipo()) {
            case RECORDATORIO -> MensajeRecordatorio.recordatorio(turno.datosDelTurno(), consultorio, enlaces,
                    Objects.requireNonNull(token, "token")).texto();
            case CONFIRMACION -> MensajeRecordatorio.confirmacion(turno.datosDelTurno(), consultorio, enlaces).texto();
            case CANCELACION -> MensajeRecordatorio.cancelacion(turno.datosDelTurno(), consultorio).texto();
            case REASIGNACION -> MensajeRecordatorio.reasignacion(turno.datosDelTurno(), consultorio).texto();
            case REINTEGRO_SENA -> MensajeRecordatorio.reintegroSena(turno.datosDelTurno(), consultorio).texto();
        };
    }

    private boolean enviar(Canal canal, EnvioNotificacion envio) {
        NotificacionPort puerto = puertos.get(canal);
        if (puerto == null) {
            return false;
        }
        try {
            return puerto.enviar(envio) == ResultadoEnvio.OK;
        } catch (RuntimeException e) {
            // el proveedor falló de forma inesperada: se trata como una falla de envío y se reintenta
            return false;
        }
    }
}
