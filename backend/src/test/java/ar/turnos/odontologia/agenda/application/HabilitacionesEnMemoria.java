package ar.turnos.odontologia.agenda.application;

import ar.turnos.odontologia.configuracion.application.HabilitacionProfesional;
import java.util.HashSet;
import java.util.Set;

/** Doble en memoria del puerto {@link HabilitacionProfesional} (RN-AG-15): sin pares cargados, nadie está habilitado. */
class HabilitacionesEnMemoria implements HabilitacionProfesional {

    private final Set<String> pares = new HashSet<>();

    HabilitacionesEnMemoria habilitar(Long profesionalId, Long prestacionId) {
        pares.add(profesionalId + ":" + prestacionId);
        return this;
    }

    HabilitacionesEnMemoria quitar(Long profesionalId, Long prestacionId) {
        pares.remove(profesionalId + ":" + prestacionId);
        return this;
    }

    @Override
    public boolean estaHabilitado(Long profesionalId, Long prestacionId) {
        return pares.contains(profesionalId + ":" + prestacionId);
    }
}
