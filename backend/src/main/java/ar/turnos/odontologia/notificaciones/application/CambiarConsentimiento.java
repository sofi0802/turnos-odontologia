package ar.turnos.odontologia.notificaciones.application;

import ar.turnos.odontologia.notificaciones.domain.CambioConsentimiento;
import ar.turnos.odontologia.notificaciones.domain.EstadoConsentimiento;
import java.time.Clock;
import java.util.Objects;

/**
 * Único lugar que cambia el consentimiento de comunicaciones (C-17, design D2), lo use el paciente, el registro o la
 * recepción. Si el valor pedido es el vigente, no agrega historial. Corre dentro de la transacción de quien lo llama.
 */
public class CambiarConsentimiento {

    private final ConsentimientoRepository repositorio;
    private final Clock reloj;

    public CambiarConsentimiento(ConsentimientoRepository repositorio, Clock reloj) {
        this.repositorio = Objects.requireNonNull(repositorio);
        this.reloj = Objects.requireNonNull(reloj);
    }

    public EstadoConsentimiento ejecutar(long pacienteId, CambioConsentimiento cambio) {
        boolean vigente = repositorio.vigenteBloqueando(pacienteId).orElseThrow(PacienteNoEncontradoException::new);
        if (vigente != cambio.otorgado()) {
            repositorio.registrar(pacienteId, cambio, reloj.instant());
        }
        return repositorio.consultar(pacienteId).orElseThrow(PacienteNoEncontradoException::new);
    }
}
