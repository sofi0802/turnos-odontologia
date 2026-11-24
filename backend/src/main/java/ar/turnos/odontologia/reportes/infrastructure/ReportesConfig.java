package ar.turnos.odontologia.reportes.infrastructure;

import ar.turnos.odontologia.reportes.application.ConsultaReportes;
import ar.turnos.odontologia.reportes.application.LecturaReportes;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Usa la zona y el reloj del consultorio ya definidos en {@code AgendaConfig}; los tests fijan el reloj. */
@Configuration
class ReportesConfig {

    @Bean
    ConsultaReportes consultaReportes(LecturaReportes lectura, ZoneId zonaHoraria, Clock reloj) {
        return new ConsultaReportes(lectura, zonaHoraria, reloj);
    }
}
