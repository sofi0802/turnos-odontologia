package ar.turnos.odontologia.pagos.domain;

import java.time.Duration;
import java.time.Instant;

/** Qué pasa con una seña pagada cuando su turno se cancela o queda ausente (RN-SE-06). */
public final class ReglaFinal {

    /** Anticipación con la que una cancelación del personal siempre reintegra (RN-AG-07). */
    public static final Duration PLAZO = Duration.ofHours(24);

    private ReglaFinal() {
    }

    public enum Destino {
        REINTEGRO,
        RETENCION
    }

    /**
     * Cómo terminó el turno.
     *
     * @param ausente {@code true} si el turno quedó {@code AUSENTE}
     * @param online {@code true} si lo canceló el paciente online
     * @param canceladoEn cuándo se canceló, o {@code null} si quedó ausente
     * @param causa la causa que indicó el personal, o {@code null}
     */
    public record FinDelTurno(boolean ausente, boolean online, Instant canceladoEn, Instant inicio, Causa causa) {

        public static FinDelTurno cancelado(boolean online, Instant canceladoEn, Instant inicio, Causa causa) {
            return new FinDelTurno(false, online, canceladoEn, inicio, causa);
        }

        public static FinDelTurno porAusencia() {
            return new FinDelTurno(true, false, null, null, null);
        }
    }

    public static Destino decidir(FinDelTurno fin) {
        if (fin.ausente()) {
            return Destino.RETENCION;
        }
        if (fin.online()) {
            return Destino.REINTEGRO;
        }
        if (Duration.between(fin.canceladoEn(), fin.inicio()).compareTo(PLAZO) >= 0) {
            return Destino.REINTEGRO;
        }
        // Una cancelación tarde sin causa no debería pasar (la API la pide); si pasa, se favorece al paciente.
        return fin.causa() == Causa.PACIENTE ? Destino.RETENCION : Destino.REINTEGRO;
    }
}
