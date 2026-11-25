package ar.turnos.odontologia.reserva.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Puerto de las señas que usa la reserva online (C-23, design D2). Lo implementa el módulo de pagos; la reserva no
 * depende de él. Sin implementación, la reserva funciona como antes de C-23 ({@link #NINGUNA}).
 */
public interface SenasDeReserva {

    /**
     * Crea la seña de un turno recién reservado online, si corresponde (RN-SE-02).
     *
     * @return la seña creada, o vacío si el turno no pide seña
     * @throws SenaNoDisponibleException si no se pudo guardar la seña (el turno ya quedó cancelado)
     */
    Optional<VistaSena> crearPara(long turnoId, long pacienteId);

    /** La seña de cada turno que tiene una; los turnos sin seña no aparecen. */
    Map<Long, VistaSena> deTurnos(Collection<Long> turnoIds);

    /**
     * Un link de pago nuevo para la seña pendiente del turno.
     *
     * @throws SenaNoPendienteException si el turno no tiene una seña pendiente
     * @throws SenaNoDisponibleException si la pasarela no respondió
     */
    String nuevoLink(long turnoId);

    /**
     * La seña tal como la ve el paciente (spec {@code turnos-paciente}).
     *
     * @param linkPago solo con la seña pendiente
     */
    record VistaSena(String estado, BigDecimal importe, Instant venceEn, String linkPago) {}

    /** El turno no tiene una seña pendiente ({@code 409 SENA_NO_PENDIENTE}). */
    class SenaNoPendienteException extends RuntimeException {
        public SenaNoPendienteException() {
            super("La seña de este turno no está pendiente.");
        }
    }

    /**
     * No se pudo crear la seña o pedir su link ({@code 503}).
     *
     * @param codigo {@code PASARELA_NO_DISPONIBLE} o {@code SENA_NO_DISPONIBLE}
     */
    class SenaNoDisponibleException extends RuntimeException {
        private final String codigo;

        public SenaNoDisponibleException(String codigo, String mensaje) {
            super(mensaje);
            this.codigo = codigo;
        }

        public String codigo() {
            return codigo;
        }
    }

    /** Sin módulo de pagos: ningún turno pide seña. */
    SenasDeReserva NINGUNA = new SenasDeReserva() {
        @Override
        public Optional<VistaSena> crearPara(long turnoId, long pacienteId) {
            return Optional.empty();
        }

        @Override
        public Map<Long, VistaSena> deTurnos(Collection<Long> turnoIds) {
            return Map.of();
        }

        @Override
        public String nuevoLink(long turnoId) {
            throw new SenaNoPendienteException();
        }
    };
}
