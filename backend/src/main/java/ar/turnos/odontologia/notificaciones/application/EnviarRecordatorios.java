package ar.turnos.odontologia.notificaciones.application;

import ar.turnos.odontologia.notificaciones.application.NotificacionRepository.Reintento;
import ar.turnos.odontologia.notificaciones.domain.Canal;
import ar.turnos.odontologia.notificaciones.domain.Consultorio;
import ar.turnos.odontologia.notificaciones.domain.Enlaces;
import ar.turnos.odontologia.notificaciones.domain.Notificacion;
import ar.turnos.odontologia.notificaciones.domain.TipoNotificacion;
import ar.turnos.odontologia.notificaciones.domain.TurnoARecordar;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caso de uso de la tarea programada: envía el recordatorio de los turnos activos que empiezan dentro de las
 * próximas 24 horas (RN-NO-04), una sola vez por turno, y reintenta los envíos fallidos que ya vencieron.
 *
 * <p>Las reglas se aplican acá aunque el puerto ya prefiltre: turno activo, dentro de las 24 horas, reservado con
 * 24 horas o más de anticipación, con consentimiento y con algún contacto. La unicidad la decide la inserción
 * (el índice único de la base), así dos corridas simultáneas nunca mandan dos recordatorios.
 */
public class EnviarRecordatorios {

    private static final Logger LOG = LoggerFactory.getLogger(EnviarRecordatorios.class);

    private final TurnosARecordar turnos;
    private final NotificacionRepository notificaciones;
    private final GeneradorToken tokens;
    private final Despachador despachador;
    private final Clock reloj;

    /** Con las rutas por defecto de los links de cancelar y reprogramar. */
    public EnviarRecordatorios(TurnosARecordar turnos, NotificacionRepository notificaciones,
                               List<NotificacionPort> canales, GeneradorToken tokens, Consultorio consultorio,
                               String baseUrl, Clock reloj) {
        this(turnos, notificaciones, canales, tokens, consultorio, Enlaces.de(baseUrl), reloj);
    }

    public EnviarRecordatorios(TurnosARecordar turnos, NotificacionRepository notificaciones,
                               List<NotificacionPort> canales, GeneradorToken tokens, Consultorio consultorio,
                               Enlaces enlaces, Clock reloj) {
        this.turnos = Objects.requireNonNull(turnos, "turnos");
        this.notificaciones = Objects.requireNonNull(notificaciones, "notificaciones");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.despachador = new Despachador(canales, consultorio, enlaces);
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    /** Cuántas notificaciones se procesaron en la corrida. */
    public record Resumen(int nuevas, int reintentadas) {}

    public Resumen ejecutar() {
        Instant ahora = reloj.instant();
        int nuevas = 0;
        for (TurnoARecordar turno : turnos.candidatos(ahora)) {
            // una fila que lanza no corta el resto de la corrida
            try {
                if (corresponde(turno, ahora) && enviarNuevo(turno, ahora)) {
                    nuevas++;
                }
            } catch (RuntimeException e) {
                LOG.error("Falló el procesamiento de un turno a recordar: turno={} excepcion={}", turno.turnoId(),
                        e.getClass().getSimpleName());
            }
        }
        int reintentadas = 0;
        for (Reintento reintento : notificaciones.reintentosVencidos(ahora)) {
            if (reintentar(reintento, ahora)) {
                reintentadas++;
            }
        }
        return new Resumen(nuevas, reintentadas);
    }

    private boolean corresponde(TurnoARecordar turno, Instant ahora) {
        return turno.activo()
                && turno.empiezaDentroDeLasProximas24Horas(ahora)
                && turno.reservadoConAnticipacion()
                && turno.contacto().puedeRecibirComunicaciones()
                && !notificaciones.existeRecordatorio(turno.turnoId());
    }

    private boolean enviarNuevo(TurnoARecordar turno, Instant ahora) {
        Canal canal = turno.contacto().canalDeEnvio().orElseThrow();
        String token = tokens.generar();
        Notificacion pendiente = Notificacion.pendiente(turno.turnoId(), canal, TipoNotificacion.RECORDATORIO, ahora,
                HuellaToken.de(token));
        // la inserción decide quién envía: si otra corrida ganó, esta no manda nada
        Optional<Notificacion> insertada = notificaciones.insertarRecordatorio(pendiente);
        if (insertada.isEmpty()) {
            return false;
        }
        try {
            notificaciones.actualizar(despachador.despachar(insertada.get(), turno, token, ahora));
        } catch (RuntimeException e) {
            registrarFalla(insertada.get(), turno, ahora, e);
        }
        return true;
    }

    /** @return {@code true} si esta corrida se quedó con la notificación y la procesó */
    private boolean reintentar(Reintento reintento, Instant ahora) {
        Notificacion vista = reintento.notificacion();
        TurnoARecordar turno = reintento.turno();
        try {
            // una sola corrida se queda con la fila: las demás la ven tomada y la dejan
            if (!notificaciones.reclamar(vista, ahora)) {
                return false;
            }
        } catch (RuntimeException e) {
            LOG.error("Falló al reclamar una notificación: id={} excepcion={}", vista.id(), e.getClass().getSimpleName());
            return false;
        }
        Notificacion reclamada = vista.reclamada(ahora);
        try {
            procesarReclamada(reclamada, turno, ahora);
        } catch (RuntimeException e) {
            // la fila ya es de esta corrida: si lanza cuenta como un intento fallido, así no se reprocesa para siempre
            registrarFalla(reclamada, turno, ahora, e);
        }
        return true;
    }

    private void procesarReclamada(Notificacion reclamada, TurnoARecordar turno, Instant ahora) {
        Notificacion notificacion = reclamada;
        boolean sigueCorrespondiendo = sigueCorrespondiendo(reclamada, turno, ahora);
        if (!sigueCorrespondiendo) {
            notificaciones.actualizar(notificacion.abandonada());
            return;
        }
        if (notificacion.debeCambiarAEmail(turno.contacto().tieneEmail())) {
            notificacion = notificacion.aEmail();
        }
        String destino = turno.contacto().destino(notificacion.canal());
        if (destino == null || destino.isBlank()) {
            notificaciones.actualizar(notificacion.abandonada());
            return;
        }
        // el token anterior no se guarda en claro: cada intento de un recordatorio lleva un token nuevo. Los demás
        // tipos (la reasignación) no llevan token ni link: nunca guardan una huella
        String token = null;
        if (notificacion.tipo() == TipoNotificacion.RECORDATORIO) {
            token = tokens.generar();
            notificacion = notificacion.conHuellaDeToken(HuellaToken.de(token));
        }
        notificaciones.actualizar(despachador.despachar(notificacion, turno, token, ahora));
    }

    /**
     * Si todavía tiene sentido avisar: hace falta el consentimiento y, salvo en una cancelación (el turno ya está
     * cancelado cuando se avisa), que el turno siga activo y no haya empezado.
     */
    private static boolean sigueCorrespondiendo(Notificacion notificacion, TurnoARecordar turno, Instant ahora) {
        if (!turno.contacto().consentimiento()) {
            return false;
        }
        return notificacion.tipo() == TipoNotificacion.CANCELACION || (turno.activo() && turno.inicio().isAfter(ahora));
    }

    /**
     * Una excepción sobre una fila ya registrada cuenta como un intento fallido, por el mismo camino y con el mismo
     * tope de 3 que una falla del proveedor. En el log va solo el id y la clase de la excepción: nunca su mensaje,
     * ni contactos ni tokens. Si ni siquiera se puede guardar la falla, la fila sigue PENDIENTE y se recupera luego.
     */
    private void registrarFalla(Notificacion notificacion, TurnoARecordar turno, Instant ahora, RuntimeException causa) {
        LOG.error("Falló el procesamiento de una notificación: id={} excepcion={}", notificacion.id(),
                causa.getClass().getSimpleName());
        try {
            Notificacion base = notificacion.debeCambiarAEmail(turno.contacto().tieneEmail())
                    ? notificacion.aEmail() : notificacion;
            notificaciones.actualizar(base.fallida(ahora, turno.contacto().tieneEmail()));
        } catch (RuntimeException e) {
            LOG.error("No se pudo registrar la falla de una notificación: id={} excepcion={}", notificacion.id(),
                    e.getClass().getSimpleName());
        }
    }
}
