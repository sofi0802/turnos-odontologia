package ar.turnos.odontologia.pagos.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Puerto de la pasarela de pagos de las señas (RN-SE-05, RN-SE-09, design D6). Los adaptadores son la pasarela
 * simulada y Mercado Pago en sandbox. A la pasarela le llegan solo el importe, una descripción genérica y una
 * referencia opaca (RN-SE-11).
 */
public interface PasarelaPago {

    /** El link para pagar la seña. Lanza {@link PasarelaNoDisponibleException} si la pasarela no responde. */
    String crearLink(String referencia, BigDecimal importe, String descripcion, Instant vence);

    /** El pago notificado, o vacío si la pasarela no lo conoce. */
    Optional<PagoExterno> consultarPago(String idPago);

    /** Reintegra un importe de un pago. La clave de idempotencia evita que un reintento devuelva dos veces. */
    void reintegrar(String idPago, BigDecimal importe, String claveIdempotencia);

    /**
     * Un pago tal como lo informa la pasarela.
     *
     * @param estado el estado de la pasarela ({@code approved}, {@code rejected}, {@code pending}, …)
     * @param referencia la referencia de la seña que se mandó al crear el link
     */
    record PagoExterno(String id, String estado, String referencia, BigDecimal importe) {

        public boolean aprobado() {
            return "approved".equals(estado);
        }
    }
}
