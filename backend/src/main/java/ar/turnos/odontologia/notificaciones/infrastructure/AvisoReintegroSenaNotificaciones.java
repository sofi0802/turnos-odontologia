package ar.turnos.odontologia.notificaciones.infrastructure;

import ar.turnos.odontologia.notificaciones.application.AvisarReintegroSena;
import ar.turnos.odontologia.pagos.application.AvisoReintegroSena;
import org.springframework.stereotype.Component;

/**
 * Adaptador del puerto {@link AvisoReintegroSena} de las señas (C-23): pide el aviso de reintegro del turno. Las reglas
 * de consentimiento, canal y contenido son de {@link AvisarReintegroSena}. El módulo de pagos depende solo del puerto.
 */
@Component
class AvisoReintegroSenaNotificaciones implements AvisoReintegroSena {

    private final AvisarReintegroSena avisar;

    AvisoReintegroSenaNotificaciones(AvisarReintegroSena avisar) {
        this.avisar = avisar;
    }

    @Override
    public void reintegrada(long turnoId, long pacienteId) {
        avisar.ejecutar(turnoId);
    }
}
