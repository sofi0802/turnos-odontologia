package ar.turnos.odontologia.notificaciones.application;

import ar.turnos.odontologia.notificaciones.domain.CambioConsentimiento;
import ar.turnos.odontologia.notificaciones.domain.EstadoConsentimiento;
import ar.turnos.odontologia.notificaciones.domain.EventoConsentimiento;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Repositorio del consentimiento en memoria para los tests unitarios (C-17). */
class ConsentimientosEnMemoria implements ConsentimientoRepository {

    private final Map<Long, List<EventoConsentimiento>> historiales = new HashMap<>();
    private final Map<Long, String> usuarios = new HashMap<>();

    void agregarPaciente(long pacienteId) {
        historiales.put(pacienteId, new ArrayList<>());
    }

    void agregarUsuario(long usuarioId, String username) {
        usuarios.put(usuarioId, username);
    }

    @Override
    public Optional<Boolean> vigenteBloqueando(long pacienteId) {
        return consultar(pacienteId).map(EstadoConsentimiento::otorgado);
    }

    @Override
    public void registrar(long pacienteId, CambioConsentimiento cambio, Instant cuando) {
        String usuario = cambio.usuarioId() == null ? null : usuarios.get(cambio.usuarioId());
        historiales.get(pacienteId).add(
                new EventoConsentimiento(cambio.otorgado(), cuando, cambio.origen(), cambio.medio(), usuario));
    }

    @Override
    public Optional<EstadoConsentimiento> consultar(long pacienteId) {
        List<EventoConsentimiento> historial = historiales.get(pacienteId);
        return historial == null ? Optional.empty() : Optional.of(EstadoConsentimiento.de(List.copyOf(historial)));
    }
}
