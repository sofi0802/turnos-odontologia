package ar.turnos.odontologia.notificaciones.infrastructure;

import ar.turnos.odontologia.notificaciones.application.CambiarConsentimiento;
import ar.turnos.odontologia.notificaciones.application.ConsentimientoRepository;
import ar.turnos.odontologia.notificaciones.application.PacienteNoEncontradoException;
import ar.turnos.odontologia.notificaciones.domain.CambioConsentimiento;
import ar.turnos.odontologia.notificaciones.domain.EstadoConsentimiento;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bordes transaccionales del consentimiento (C-17, design D2). Nunca registra en los logs datos del paciente.
 */
@Service
public class ServicioConsentimiento {

    private final CambiarConsentimiento cambiar;
    private final ConsentimientoRepository repositorio;

    ServicioConsentimiento(CambiarConsentimiento cambiar, ConsentimientoRepository repositorio) {
        this.cambiar = cambiar;
        this.repositorio = repositorio;
    }

    @Transactional
    public EstadoConsentimiento cambiar(long pacienteId, CambioConsentimiento cambio) {
        return cambiar.ejecutar(pacienteId, cambio);
    }

    @Transactional(readOnly = true)
    public EstadoConsentimiento consultar(long pacienteId) {
        return repositorio.consultar(pacienteId).orElseThrow(PacienteNoEncontradoException::new);
    }
}
