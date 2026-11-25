package ar.turnos.odontologia.pagos.application;

import ar.turnos.odontologia.pagos.domain.ConfiguracionSena;

/** Puerto de la configuración de las señas (RN-SE-01). Sin configuración guardada vale {@link ConfiguracionSena#INICIAL}. */
public interface ConfiguracionSenas {

    ConfiguracionSena leer();

    ConfiguracionSena guardar(ConfiguracionSena configuracion);
}
