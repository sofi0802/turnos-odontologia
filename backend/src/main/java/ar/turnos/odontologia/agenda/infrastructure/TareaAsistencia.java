package ar.turnos.odontologia.agenda.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Capa fina de la tarea programada de C-43 (design D2), igual que {@code TareaRecordatorios} de C-11: cada 5 minutos
 * (configurable con {@code app.agenda.asistencia.intervalo-ms}) pasa a {@code ATENDIDO} los turnos con llegada cuyo
 * margen terminó. Se apaga con {@code app.agenda.asistencia.activo=false}; los tests de integración llaman al
 * servicio a mano.
 */
@Component
@ConditionalOnProperty(name = "app.agenda.asistencia.activo", havingValue = "true", matchIfMissing = true)
class TareaAsistencia {

    private static final Logger LOG = LoggerFactory.getLogger(TareaAsistencia.class);

    private final ServicioAgenda servicio;

    TareaAsistencia(ServicioAgenda servicio) {
        this.servicio = servicio;
    }

    @Scheduled(fixedDelayString = "${app.agenda.asistencia.intervalo-ms:300000}",
            initialDelayString = "${app.agenda.asistencia.intervalo-ms:300000}")
    void pasarAAtendidos() {
        try {
            int cambiados = servicio.pasarAAtendidos();
            if (cambiados > 0) {
                LOG.info("Turnos pasados a atendidos: {}", cambiados);
            }
        } catch (RuntimeException e) {
            // un error no corta la tarea: se reintenta en la próxima corrida. Solo el tipo, nunca datos del paciente.
            LOG.error("Falló el paso a atendidos: {}", e.getClass().getSimpleName());
        }
    }
}
