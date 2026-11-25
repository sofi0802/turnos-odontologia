package ar.turnos.odontologia.pagos.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Puerto de lectura de lo que la revisión de las señas necesita de la agenda y de la caja (design D4): la auditoría de
 * cancelaciones y reprogramaciones, el inicio del turno y su cargo. Solo lee.
 */
public interface TurnosDeSenas {

    /** El turno nuevo si este turno se reprogramó (detalle de {@code REPROGRAMAR}). */
    Optional<Long> turnoReprogramado(long turnoId);

    /** Quién, cuándo y con qué causa se canceló el turno. */
    Optional<Cancelacion> cancelacion(long turnoId);

    Instant inicio(long turnoId);

    /** El cargo de C-22 del turno, si ya nació. */
    Optional<CargoDelTurno> cargo(long turnoId);

    /** @param causa {@code PACIENTE}, {@code CONSULTORIO} o {@code null} */
    record Cancelacion(String actor, Instant fecha, String causa) {}

    record CargoDelTurno(long id, BigDecimal copago) {}
}
