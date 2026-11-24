package ar.turnos.odontologia.coberturas.application;

import java.util.Collection;
import java.util.Map;

/**
 * Puerto de lectura propio sobre las prestaciones del consultorio (C-18, design D1): el módulo de coberturas no
 * depende del de configuración, solo consulta el nombre por id.
 */
public interface PrestacionesExistentes {

    /** El nombre de cada prestación que existe; los ids que no existen no aparecen. */
    Map<Long, String> nombres(Collection<Long> ids);
}
