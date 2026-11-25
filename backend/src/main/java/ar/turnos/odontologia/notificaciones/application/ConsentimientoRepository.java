package ar.turnos.odontologia.notificaciones.application;

import ar.turnos.odontologia.notificaciones.domain.CambioConsentimiento;
import ar.turnos.odontologia.notificaciones.domain.EstadoConsentimiento;
import java.time.Instant;
import java.util.Optional;

/** Estado vigente e historial del consentimiento de comunicaciones (C-17, design D1). */
public interface ConsentimientoRepository {

    /**
     * Bloquea al paciente hasta el final de la transacción y devuelve si hoy tiene el consentimiento otorgado, así dos
     * cambios simultáneos no desordenan el historial. Vacío si el paciente no existe.
     */
    Optional<Boolean> vigenteBloqueando(long pacienteId);

    /** Agrega el cambio al historial y actualiza el estado vigente del paciente, en la misma transacción. */
    void registrar(long pacienteId, CambioConsentimiento cambio, Instant cuando);

    /** Estado vigente con el historial; vacío si el paciente no existe. */
    Optional<EstadoConsentimiento> consultar(long pacienteId);
}
