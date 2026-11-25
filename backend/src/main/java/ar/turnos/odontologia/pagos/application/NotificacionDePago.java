package ar.turnos.odontologia.pagos.application;

import ar.turnos.odontologia.pagos.application.PasarelaPago.PagoExterno;
import ar.turnos.odontologia.pagos.application.Senas.Movimiento;
import ar.turnos.odontologia.pagos.domain.EstadoSena;
import ar.turnos.odontologia.pagos.domain.FirmaMercadoPago;
import ar.turnos.odontologia.pagos.domain.Sena;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Notificación de Mercado Pago (RN-SE-05, design D5): firma, consulta del pago, idempotencia y pago tardío. La seña se
 * da por pagada solo si el pago consultado está aprobado, es de su referencia y tiene su importe.
 */
public class NotificacionDePago {

    private static final Logger LOG = LoggerFactory.getLogger(NotificacionDePago.class);

    private final Senas senas;
    private final PasarelaPago pasarela;
    private final Reintegros reintegros;
    private final TransactionTemplate transaccion;
    private final Clock reloj;
    private final String clave;

    public NotificacionDePago(Senas senas, PasarelaPago pasarela, Reintegros reintegros, TransactionTemplate transaccion,
                              Clock reloj, String clave) {
        this.senas = senas;
        this.pasarela = pasarela;
        this.reintegros = reintegros;
        this.transaccion = transaccion;
        this.reloj = reloj;
        this.clave = clave;
    }

    /**
     * @param tipo el tipo de la notificación; solo se procesan las de pagos ({@code payment})
     * @throws FirmaInvalidaException si la firma falta o no es válida
     * @throws PasarelaNoDisponibleException si no se pudo consultar el pago (Mercado Pago reintenta la notificación)
     */
    public void procesar(String xSignature, String xRequestId, String idPago, String tipo) {
        if (!FirmaMercadoPago.valida(xSignature, xRequestId, idPago, clave)) {
            throw new FirmaInvalidaException();
        }
        if (idPago == null || idPago.isBlank() || (tipo != null && !tipo.isBlank() && !tipo.equals("payment"))) {
            return;
        }
        Optional<PagoExterno> pago = pasarela.consultarPago(idPago);
        if (pago.isEmpty()) {
            LOG.info("Notificación de un pago que la pasarela no conoce");
            return;
        }
        Long aReintegrar = transaccion.execute(estado -> aplicar(pago.get(), reloj.instant()));
        if (aReintegrar != null) {
            reintegros.procesar(aReintegrar);
        }
    }

    /** Devuelve el id de la seña si hay que reintegrar un pago tardío. */
    private Long aplicar(PagoExterno pago, Instant ahora) {
        String claveNotificacion = pago.id() + ":" + pago.estado();
        Optional<Sena> encontrada = pago.referencia() == null ? Optional.empty()
                : senas.porReferenciaBloqueada(pago.referencia());
        if (encontrada.isEmpty()) {
            senas.registrarMovimiento(null, Movimiento.NOTIFICACION, claveNotificacion, null,
                    "Referencia desconocida", ahora);
            return null;
        }
        Sena sena = encontrada.get();
        if (!senas.registrarMovimiento(sena.id(), Movimiento.NOTIFICACION, claveNotificacion, null, null, ahora)) {
            return null;
        }
        if (!pago.aprobado() || sena.pagoExternoId() != null) {
            return null;
        }
        if (pago.importe() == null || pago.importe().compareTo(sena.importe()) != 0) {
            LOG.warn("Pago aprobado con un importe distinto al de la seña {}; no se aplica", sena.id());
            return null;
        }
        senas.registrarMovimiento(sena.id(), Movimiento.PAGO, pago.id(), pago.importe(), null, ahora);
        if (sena.estado() == EstadoSena.PENDIENTE && sena.turnoActivo()) {
            senas.marcarPagada(sena.id(), pago.id(), ahora);
            LOG.info("Seña {} pagada, referencia {}", sena.id(), sena.referencia());
            return null;
        }
        // Pago tardío (PA-58): el turno no se recupera y el pago se devuelve completo.
        senas.registrarPagoTardio(sena.id(), pago.id(), ahora);
        senas.fijarReintegro(sena.id(), sena.importe());
        if (sena.estado() == EstadoSena.PENDIENTE) {
            senas.cambiarEstado(sena.id(), EstadoSena.VENCIDA);
        }
        LOG.info("Pago tardío de la seña {}, referencia {}: se reintegra", sena.id(), sena.referencia());
        return sena.id();
    }
}
