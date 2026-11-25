package ar.turnos.odontologia.pagos.application;

import ar.turnos.odontologia.pagos.application.Senas.Movimiento;
import ar.turnos.odontologia.pagos.domain.EstadoSena;
import ar.turnos.odontologia.pagos.domain.Sena;
import java.math.BigDecimal;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pide a la pasarela lo que falta reintegrar de una seña (RN-SE-09, design D4 paso 5). La clave de idempotencia incluye
 * el importe a reintegrar, así un reintento no devuelve dos veces. Un rechazo queda registrado y se reintenta en la
 * próxima revisión.
 */
public class Reintegros {

    private static final Logger LOG = LoggerFactory.getLogger(Reintegros.class);

    private final Senas senas;
    private final PasarelaPago pasarela;
    private final AvisoReintegroSena aviso;
    private final Clock reloj;

    public Reintegros(Senas senas, PasarelaPago pasarela, AvisoReintegroSena aviso, Clock reloj) {
        this.senas = senas;
        this.pasarela = pasarela;
        this.aviso = aviso;
        this.reloj = reloj;
    }

    @Transactional
    public void procesar(long senaId) {
        Sena sena = senas.porIdBloqueada(senaId).orElse(null);
        if (sena == null || sena.pagoExternoId() == null) {
            return;
        }
        BigDecimal pendiente = sena.pendienteDeReintegro();
        if (pendiente.signum() <= 0) {
            return;
        }
        String clave = "sena-" + senaId + "-" + sena.aReintegrar().toPlainString();
        try {
            pasarela.reintegrar(sena.pagoExternoId(), pendiente, clave);
        } catch (PasarelaNoDisponibleException e) {
            senas.registrarMovimiento(senaId, Movimiento.REINTEGRO_FALLIDO, null, pendiente,
                    "La pasarela no hizo el reintegro", reloj.instant());
            LOG.warn("Reintegro de la seña {} rechazado por la pasarela; se reintenta en la próxima revisión", senaId);
            return;
        }
        senas.registrarReintegrado(senaId, sena.aReintegrar());
        senas.registrarMovimiento(senaId, Movimiento.REINTEGRO, clave, pendiente, null, reloj.instant());
        if (sena.estado() != EstadoSena.IMPUTADA) {
            senas.cambiarEstado(senaId, EstadoSena.DEVUELTA);
        }
        LOG.info("Seña {} reintegrada, referencia {}", senaId, sena.referencia());
        try {
            aviso.reintegrada(sena.turnoId(), sena.pacienteId());
        } catch (RuntimeException e) {
            // El dinero ya se devolvió: un aviso que falla no deshace el reintegro.
            LOG.warn("No se pudo avisar el reintegro de la seña {}", senaId);
        }
    }
}
