package ar.turnos.odontologia.agenda.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Turno de la agenda. Ocupa el intervalo {@code [inicio, fin)} de un profesional y un sillón.
 *
 * <p>Los identificadores son {@code BIGINT} en la base (DD-12). Un turno recién creado no tiene
 * {@code id} hasta que se guarda: lo asigna la base al insertarlo. Los campos de sobreturno y
 * extensión son aditivos (C-08): las firmas usadas por C-01 y C-06 siguen disponibles.
 */
public record Turno(
        Long id,
        Long pacienteId,
        Long profesionalId,
        Long sillonId,
        Long prestacionId,
        Intervalo intervalo,
        EstadoTurno estado,
        NumeroReserva numeroReserva,
        boolean esSobreturno,
        String motivoSobreturno,
        boolean extendido,
        String motivoExtension) {

    /** Firma original: un turno normal, ni sobreturno ni extendido. */
    public Turno(Long id, Long pacienteId, Long profesionalId, Long sillonId, Long prestacionId,
            Intervalo intervalo, EstadoTurno estado, NumeroReserva numeroReserva) {
        this(id, pacienteId, profesionalId, sillonId, prestacionId, intervalo, estado, numeroReserva,
                false, null, false, null);
    }

    public Turno {
        Objects.requireNonNull(pacienteId, "pacienteId");
        Objects.requireNonNull(profesionalId, "profesionalId");
        Objects.requireNonNull(sillonId, "sillonId");
        Objects.requireNonNull(prestacionId, "prestacionId");
        Objects.requireNonNull(intervalo, "intervalo");
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(numeroReserva, "numeroReserva");
    }

    /** Crea un turno nuevo, todavía sin guardar, en estado {@link EstadoTurno#RESERVADO}. */
    public static Turno reservar(Long pacienteId, Long profesionalId, Long sillonId,
            Long prestacionId, Intervalo intervalo, NumeroReserva numeroReserva) {
        return new Turno(null, pacienteId, profesionalId, sillonId, prestacionId,
                intervalo, EstadoTurno.RESERVADO, numeroReserva);
    }

    /** C-08: crea un sobreturno con su motivo administrativo obligatorio (RN-AG-06). */
    public static Turno sobreturno(Long pacienteId, Long profesionalId, Long sillonId, Long prestacionId,
            Intervalo intervalo, NumeroReserva numeroReserva, String motivo) {
        verificarMotivo(motivo);
        return new Turno(null, pacienteId, profesionalId, sillonId, prestacionId,
                intervalo, EstadoTurno.RESERVADO, numeroReserva, true, motivo, false, null);
    }

    /** El mismo turno con el identificador que le asignó la base al guardarlo. */
    public Turno conId(Long nuevoId) {
        return new Turno(Objects.requireNonNull(nuevoId, "nuevoId"), pacienteId, profesionalId,
                sillonId, prestacionId, intervalo, estado, numeroReserva,
                esSobreturno, motivoSobreturno, extendido, motivoExtension);
    }

    /** El mismo turno en estado {@link EstadoTurno#CANCELADO}: deja de ocupar la agenda (RN-AG-08). */
    public Turno cancelado() {
        return new Turno(id, pacienteId, profesionalId, sillonId, prestacionId, intervalo,
                EstadoTurno.CANCELADO, numeroReserva, esSobreturno, motivoSobreturno, extendido, motivoExtension);
    }

    /**
     * C-08: el mismo turno con un fin absoluto posterior y su motivo de extensión. No cambian el id,
     * el número de reserva, el estado ni el inicio (RN-AG-14).
     */
    public Turno conExtension(Instant nuevoFin, String motivo) {
        if (nuevoFin == null || !nuevoFin.isAfter(intervalo.fin())) {
            throw new TurnoRechazadoException(CodigoRechazo.DATOS_INVALIDOS,
                    "El nuevo fin debe ser posterior al fin actual del turno");
        }
        verificarMotivo(motivo);
        return new Turno(id, pacienteId, profesionalId, sillonId, prestacionId,
                new Intervalo(intervalo.inicio(), nuevoFin), estado, numeroReserva,
                esSobreturno, motivoSobreturno, true, motivo);
    }

    private static void verificarMotivo(String motivo) {
        if (motivo == null || motivo.isBlank() || motivo.trim().length() > 500) {
            throw new TurnoRechazadoException(CodigoRechazo.DATOS_INVALIDOS,
                    "El motivo administrativo es obligatorio y no puede superar 500 caracteres");
        }
    }

    /**
     * El mismo turno en estado {@link EstadoTurno#CONFIRMADO} (C-11): no cambia el horario, el sillón, el
     * profesional ni el número de reserva, y sigue ocupando la agenda. Solo un turno RESERVADO se confirma.
     */
    public Turno confirmado() {
        if (estado != EstadoTurno.RESERVADO) {
            throw new TurnoRechazadoException(CodigoRechazo.TURNO_NO_ACTIVO,
                    "El turno no está reservado: está " + estado);
        }
        return new Turno(id, pacienteId, profesionalId, sillonId, prestacionId, intervalo,
                EstadoTurno.CONFIRMADO, numeroReserva, esSobreturno, motivoSobreturno, extendido, motivoExtension);
    }

    /**
     * C-13: el mismo turno en estado {@link EstadoTurno#AUSENTE}: el paciente no se presentó y el turno deja de
     * ocupar la agenda (RN-AG-17). Conserva todos los campos, incluidos los de sobreturno y extensión de C-08.
     */
    public Turno ausente() {
        return new Turno(id, pacienteId, profesionalId, sillonId, prestacionId, intervalo,
                EstadoTurno.AUSENTE, numeroReserva, esSobreturno, motivoSobreturno, extendido, motivoExtension);
    }

    public boolean esActivo() {
        return estado.esActivo();
    }

    public boolean mismoProfesionalQue(Turno otro) {
        return profesionalId.equals(otro.profesionalId);
    }

    public boolean mismoSillonQue(Turno otro) {
        return sillonId.equals(otro.sillonId);
    }
}
