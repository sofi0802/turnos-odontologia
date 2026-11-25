package ar.turnos.odontologia.pagos.infrastructure;

import ar.turnos.odontologia.pagos.application.RevisionDeSenas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Revisión periódica de las señas (design D4), cada minuto (configurable con {@code app.pagos.senas.intervalo-ms}).
 * Se apaga con {@code app.pagos.senas.activo=false}; los tests la llaman a mano.
 */
@Component
@ConditionalOnProperty(name = "app.pagos.senas.activo", havingValue = "true", matchIfMissing = true)
class TareaSenas {

    private static final Logger LOG = LoggerFactory.getLogger(TareaSenas.class);

    private final RevisionDeSenas revision;

    TareaSenas(RevisionDeSenas revision) {
        this.revision = revision;
    }

    @Scheduled(fixedDelayString = "${app.pagos.senas.intervalo-ms:60000}",
            initialDelayString = "${app.pagos.senas.intervalo-ms:60000}")
    void revisar() {
        try {
            revision.ejecutar();
        } catch (RuntimeException e) {
            // Solo el tipo del error, nunca importes ni datos del paciente (RN-SE-11).
            LOG.error("Falló la revisión de las señas: {}", e.getClass().getSimpleName());
        }
    }
}
