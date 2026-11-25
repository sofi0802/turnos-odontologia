package ar.turnos.odontologia.pagos.infrastructure;

import ar.turnos.odontologia.pagos.application.ConfiguracionSenas;
import ar.turnos.odontologia.pagos.domain.ConfiguracionSena;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Las tres claves de las señas en la tabla {@code configuracion} (KB 04). */
class ConfiguracionSenasJdbc implements ConfiguracionSenas {

    static final String ACTIVAS = "senas_activas";
    static final String PORCENTAJE = "senas_porcentaje";
    static final String PLAZO = "senas_plazo_minutos";

    private final JdbcTemplate jdbc;

    ConfiguracionSenasJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ConfiguracionSena leer() {
        Map<String, String> valores = new HashMap<>();
        jdbc.query("SELECT clave, valor FROM configuracion WHERE clave IN (?, ?, ?)",
                fila -> {
                    valores.put(fila.getString("clave"), fila.getString("valor"));
                }, ACTIVAS, PORCENTAJE, PLAZO);
        ConfiguracionSena inicial = ConfiguracionSena.INICIAL;
        return new ConfiguracionSena(
                Boolean.parseBoolean(valores.getOrDefault(ACTIVAS, String.valueOf(inicial.activas()))),
                Integer.parseInt(valores.getOrDefault(PORCENTAJE, String.valueOf(inicial.porcentaje()))),
                Integer.parseInt(valores.getOrDefault(PLAZO, String.valueOf(inicial.plazoMinutos()))));
    }

    @Override
    @Transactional
    public ConfiguracionSena guardar(ConfiguracionSena configuracion) {
        guardar(ACTIVAS, String.valueOf(configuracion.activas()));
        guardar(PORCENTAJE, String.valueOf(configuracion.porcentaje()));
        guardar(PLAZO, String.valueOf(configuracion.plazoMinutos()));
        return configuracion;
    }

    private void guardar(String clave, String valor) {
        jdbc.update("INSERT INTO configuracion(clave, valor) VALUES (?, ?) "
                + "ON CONFLICT (clave) DO UPDATE SET valor = EXCLUDED.valor", clave, valor);
    }
}
