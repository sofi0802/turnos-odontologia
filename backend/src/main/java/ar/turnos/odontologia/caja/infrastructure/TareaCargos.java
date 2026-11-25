package ar.turnos.odontologia.caja.infrastructure;

import ar.turnos.odontologia.caja.application.ServicioCobros;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Barrido periódico de design D2: crea los cargos de los turnos atendidos sin cargo, cada 5 minutos (configurable con
 * {@code app.caja.cargos.intervalo-ms}). Se apaga con {@code app.caja.cargos.activo=false}; los tests lo llaman a mano.
 */
@Component
@ConditionalOnProperty(name = "app.caja.cargos.activo", havingValue = "true", matchIfMissing = true)
class TareaCargos {

    private static final Logger LOG = LoggerFactory.getLogger(TareaCargos.class);

    private final ServicioCobros servicio;

    TareaCargos(ServicioCobros servicio) {
        this.servicio = servicio;
    }

    @Scheduled(fixedDelayString = "${app.caja.cargos.intervalo-ms:300000}",
            initialDelayString = "${app.caja.cargos.intervalo-ms:300000}")
    void generarCargos() {
        try {
            int creados = servicio.generarCargos();
            if (creados > 0) {
                LOG.info("Cargos generados: {}", creados);
            }
        } catch (RuntimeException e) {
            // Solo el tipo del error, nunca importes ni datos del paciente (RN-CA-08).
            LOG.error("Falló el barrido de cargos: {}", e.getClass().getSimpleName());
        }
    }
}
