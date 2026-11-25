package ar.turnos.odontologia.pagos.infrastructure;

import ar.turnos.odontologia.pagos.application.ConfiguracionSenas;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Beans del módulo {@code pagos} (design D1). */
@Configuration
class PagosConfig {

    @Bean
    ConfiguracionSenas configuracionSenas(JdbcTemplate jdbc) {
        return new ConfiguracionSenasJdbc(jdbc);
    }
}
