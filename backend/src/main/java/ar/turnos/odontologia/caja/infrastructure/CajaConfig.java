package ar.turnos.odontologia.caja.infrastructure;

import ar.turnos.odontologia.caja.application.Cajas;
import ar.turnos.odontologia.caja.application.Cargos;
import ar.turnos.odontologia.caja.application.Cobros;
import ar.turnos.odontologia.caja.application.ServicioCaja;
import ar.turnos.odontologia.caja.application.ServicioCobros;
import ar.turnos.odontologia.caja.application.TurnosACobrar;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Usa la zona y el reloj del consultorio de {@code AgendaConfig}; los tests fijan el reloj. */
@Configuration
class CajaConfig {

    @Bean
    ServicioCaja servicioCaja(Cajas cajas, Cobros cobros, Clock reloj, ZoneId zonaHoraria) {
        return new ServicioCaja(cajas, cobros, reloj, zonaHoraria);
    }

    @Bean
    ServicioCobros servicioCobros(Cajas cajas, Cargos cargos, Cobros cobros, TurnosACobrar turnos, Clock reloj) {
        return new ServicioCobros(cajas, cargos, cobros, turnos, reloj);
    }
}
