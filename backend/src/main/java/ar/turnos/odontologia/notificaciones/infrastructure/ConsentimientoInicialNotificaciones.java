package ar.turnos.odontologia.notificaciones.infrastructure;

import ar.turnos.odontologia.notificaciones.application.CambiarConsentimiento;
import ar.turnos.odontologia.notificaciones.domain.CambioConsentimiento;
import ar.turnos.odontologia.usuarios.application.ConsentimientoInicial;
import org.springframework.stereotype.Component;

/** Implementa el puerto del registro (C-17, design D3): consentimiento otorgado con origen {@code REGISTRO}. */
@Component
class ConsentimientoInicialNotificaciones implements ConsentimientoInicial {

    private final CambiarConsentimiento cambiar;

    ConsentimientoInicialNotificaciones(CambiarConsentimiento cambiar) {
        this.cambiar = cambiar;
    }

    @Override
    public void otorgadoAlRegistrarse(long pacienteId) {
        cambiar.ejecutar(pacienteId, CambioConsentimiento.alRegistrarse());
    }
}
