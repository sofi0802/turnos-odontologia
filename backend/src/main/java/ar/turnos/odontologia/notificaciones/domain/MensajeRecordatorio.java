package ar.turnos.odontologia.notificaciones.domain;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Texto de un recordatorio o de un aviso. Solo lleva la fecha, la hora, el consultorio (nombre y dirección) y los
 * links que corresponden al tipo (RN-NO-01, regla dura 6): ni el nombre del paciente ni el del profesional, ni la
 * prestación, el motivo, el número de reserva o el DNI. La fecha y la hora van en la zona de Buenos Aires.
 *
 * <ul>
 *   <li>recordatorio: links de confirmar, cancelar y reprogramar;</li>
 *   <li>confirmación (reserva o reprogramación online): links de cancelar y reprogramar;</li>
 *   <li>cancelación y reasignación: sin links.</li>
 * </ul>
 */
public final class MensajeRecordatorio {

    public static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    static final String PLANTILLA_RECORDATORIO = "Te recordamos tu turno del %s a las %s en %s (%s). "
            + "Confirmá tu asistencia acá: %s. Si necesitás cancelarlo: %s. Para reprogramarlo: %s";
    static final String PLANTILLA_CONFIRMACION = "Tu turno quedó confirmado para el %s a las %s en %s (%s). "
            + "Para cancelarlo: %s. Para reprogramarlo: %s";
    static final String PLANTILLA_CANCELACION = "Tu turno del %s a las %s en %s (%s) fue cancelado.";
    static final String PLANTILLA_REASIGNACION = "Tu turno fue reasignado: ahora es el %s a las %s en %s (%s).";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZONA);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm").withZone(ZONA);

    private final String texto;

    private MensajeRecordatorio(String texto) {
        this.texto = texto;
    }

    /** @param token el token de confirmación en claro, que solo viaja en el link */
    public static MensajeRecordatorio recordatorio(DatosDelTurno turno, Consultorio consultorio, Enlaces enlaces,
                                                   String token) {
        return new MensajeRecordatorio(PLANTILLA_RECORDATORIO.formatted(fecha(turno), hora(turno),
                consultorio.nombre(), consultorio.direccion(), enlaces.confirmar(token), enlaces.cancelar(),
                enlaces.reprogramar()));
    }

    /** Aviso de una reserva o reprogramación online: el turno nuevo, con los links de cancelar y reprogramar. */
    public static MensajeRecordatorio confirmacion(DatosDelTurno turno, Consultorio consultorio, Enlaces enlaces) {
        return new MensajeRecordatorio(PLANTILLA_CONFIRMACION.formatted(fecha(turno), hora(turno),
                consultorio.nombre(), consultorio.direccion(), enlaces.cancelar(), enlaces.reprogramar()));
    }

    /** Aviso de una cancelación online: fecha, hora y consultorio del turno cancelado, sin links. */
    public static MensajeRecordatorio cancelacion(DatosDelTurno turno, Consultorio consultorio) {
        return new MensajeRecordatorio(PLANTILLA_CANCELACION.formatted(fecha(turno), hora(turno),
                consultorio.nombre(), consultorio.direccion()));
    }

    /** Aviso de que el turno cambió de fecha, hora o profesional (RN-AG-16): sin link ni token. */
    public static MensajeRecordatorio reasignacion(DatosDelTurno turno, Consultorio consultorio) {
        return new MensajeRecordatorio(PLANTILLA_REASIGNACION.formatted(fecha(turno), hora(turno),
                consultorio.nombre(), consultorio.direccion()));
    }

    public String texto() {
        return texto;
    }

    private static String fecha(DatosDelTurno turno) {
        return FECHA.format(Objects.requireNonNull(turno, "turno").inicio());
    }

    private static String hora(DatosDelTurno turno) {
        return HORA.format(turno.inicio());
    }
}
