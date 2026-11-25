package ar.turnos.odontologia.coberturas.infrastructure;

import ar.turnos.odontologia.coberturas.application.CargosDeTurno;
import ar.turnos.odontologia.coberturas.domain.CargoTurno;
import java.util.Collection;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adaptador de {@link CargosDeTurno} mientras C-22 (caja y cobros) no esté en {@code master}: ningún turno tiene cargo y
 * todas las líneas de una liquidación usan el arancel vigente al generar (C-20, design D5). Cuando C-22 se mergee, un
 * {@code CargosDeTurnoJdbc} que lea {@code cargo} lo reemplaza sin tocar el resto.
 */
@Configuration
class SinCargosDeCaja {

    /**
     * Se registra solo si ningún otro módulo aporta un {@link CargosDeTurno}: la condición vive en el método
     * {@code @Bean} porque en una clase escaneada Spring Boot no garantiza el orden de evaluación.
     */
    @Bean
    @ConditionalOnMissingBean(CargosDeTurno.class)
    CargosDeTurno cargosDeTurnoVacios() {
        return new Vacios();
    }

    private static final class Vacios implements CargosDeTurno {

        @Override
        public Map<Long, CargoTurno> de(Collection<Long> turnoIds) {
            return Map.of();
        }
    }
}
