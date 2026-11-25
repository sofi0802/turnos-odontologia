package ar.turnos.odontologia.agenda.listaespera.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * C-15 (design D7): vence ofertas e inscripciones, ofrece los horarios vencidos al siguiente y reintenta los avisos.
 * Se apaga con {@code APP_LISTA_ESPERA_ACTIVO=false}; el perfil de test la apaga y los tests la llaman a mano.
 */
@Component
@ConditionalOnProperty(name = "app.lista-espera.activo", havingValue = "true", matchIfMissing = true)
class TareaListaEspera {

    private static final Logger LOG = LoggerFactory.getLogger(TareaListaEspera.class);

    private final ServicioListaEspera servicio;

    TareaListaEspera(ServicioListaEspera servicio) {
        this.servicio = servicio;
    }

    @Scheduled(fixedDelayString = "${app.lista-espera.intervalo-ms:60000}",
            initialDelayString = "${app.lista-espera.intervalo-ms:60000}")
    void ejecutar() {
        try {
            servicio.procesarVencimientos();
        } catch (RuntimeException e) {
            // sin datos: ni tokens ni pacientes
            LOG.warn("Falló la pasada de la lista de espera; se reintenta en la próxima");
        }
    }
}
