package ar.turnos.odontologia.reserva.domain;

import java.time.Duration;
import java.time.Instant;

/** Plazo para que el paciente cancele o reprograme online: 24 horas o más antes del inicio (RN-RO-08, design D6). */
public final class PlazoModificacion {

    public static final Duration ANTICIPACION = Duration.ofHours(24);

    private PlazoModificacion() {}

    public static boolean permitido(Instant ahora, Instant inicioDelTurno) {
        return !ahora.plus(ANTICIPACION).isAfter(inicioDelTurno);
    }
}
