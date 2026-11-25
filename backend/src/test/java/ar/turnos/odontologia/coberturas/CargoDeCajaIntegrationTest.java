package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.coberturas.application.CargosDeTurno;
import ar.turnos.odontologia.coberturas.domain.CargoTurno;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Precio del cargo de caja del turno (C-20, spec liquidaciones-obras-sociales, tarea 4.2, design D5). C-22 todavía no
 * está en {@code master}: un adaptador de prueba reemplaza al nulo y devuelve el cargo ficticio de cada turno. Sin esta
 * configuración todas las líneas salen con el arancel (lo verifican {@code MontosLiquidacionIntegrationTest} y
 * {@code TurnosSinArancelIntegrationTest}). Reloj: 2026-11-24 a las 12:00 en Buenos Aires. Solo datos ficticios.
 */
@Import(CargoDeCajaIntegrationTest.ConCargosFicticios.class)
class CargoDeCajaIntegrationTest extends IntegracionLiquidacionesBase {

    /** Los cargos ficticios que el test carga por turno. */
    static class CargosFicticios implements CargosDeTurno {
        final Map<Long, CargoTurno> cargos = new ConcurrentHashMap<>();

        @Override
        public Map<Long, CargoTurno> de(Collection<Long> turnoIds) {
            Map<Long, CargoTurno> encontrados = new ConcurrentHashMap<>();
            turnoIds.stream().filter(cargos::containsKey).forEach(id -> encontrados.put(id, cargos.get(id)));
            return encontrados;
        }
    }

    @TestConfiguration
    static class ConCargosFicticios {
        @Bean
        @Primary
        CargosFicticios cargosFicticios() {
            return new CargosFicticios();
        }
    }

    @Autowired
    private CargosFicticios cargosFicticios;

    @BeforeEach
    void sinCargos() {
        cargosFicticios.cargos.clear();
    }

    @Test
    @DisplayName("Turno con cargo de caja")
    void turnoConCargoDeCaja() throws Exception {
        // GIVEN el turno ATENDIDO de "Consulta o control" de "Paciente Uno" del 2026-10-06 tiene un cargo con precio 14000.00, porcentaje 100, cobertura 14000.00 y copago 0.00
        semilla();
        long turno = atendido(pacienteUno, consulta, "2026-10-06T10:00");
        cargosFicticios.cargos.put(turno, new CargoTurno(new BigDecimal("14000.00"), 100, new BigDecimal("14000.00")));
        // AND hoy el arancel de "Consulta o control" es 15000.00
        assertThat(precioGuardado(consulta)).isEqualByComparingTo("15000.00");

        // WHEN admin genera la liquidación de "Obra Social Ejemplo" para 2026-10
        JsonNode liquidacion = generada(obraSocialEjemplo, "2026-10");

        // THEN la línea tiene precio 14000.00, porcentaje 100 y monto 14000.00
        JsonNode linea = lineaDe(liquidacion, "Consulta o control");
        assertThat(numero(linea.path("precio"))).isEqualByComparingTo("14000.00");
        assertThat(linea.path("porcentaje").asInt()).isEqualTo(100);
        assertThat(numero(linea.path("montoSolicitado"))).isEqualByComparingTo("14000.00");
        assertThat(linea.path("origenPrecio").asText()).isEqualTo("CARGO");
        assertThat(numero(liquidacion.path("totalSolicitado"))).isEqualByComparingTo("14000.00");
    }

    @Test
    @DisplayName("Cargo sin cobertura")
    void cargoSinCobertura() throws Exception {
        // GIVEN el turno ATENDIDO de "Limpieza (profilaxis)" de "Paciente Uno" del 2026-10-20 tiene un cargo con porcentaje 0
        semilla();
        long turno = atendido(pacienteUno, limpieza, "2026-10-20T10:00");
        cargosFicticios.cargos.put(turno, new CargoTurno(new BigDecimal("25000.00"), 0, new BigDecimal("0.00")));

        // WHEN admin genera la liquidación de "Obra Social Ejemplo" para 2026-10
        JsonNode liquidacion = generada(obraSocialEjemplo, "2026-10");

        // THEN ese turno no está en la liquidación
        assertThat(idsDeTurnos(liquidacion)).doesNotContain(turno);
        assertThat(lineasDe(liquidacion)).isEmpty();
    }

    @Test
    @DisplayName("Con cargo del 50 % el monto es la cobertura del cargo")
    void cargoParcial() throws Exception {
        // GIVEN un cargo de la limpieza con precio 24000.00 al 50 %, mientras el arancel es 25000.00
        semilla();
        long turno = atendido(pacienteUno, limpieza, "2026-10-20T10:00");
        cargosFicticios.cargos.put(turno, new CargoTurno(new BigDecimal("24000.00"), 50, new BigDecimal("12000.00")));

        // WHEN admin genera la liquidación
        JsonNode liquidacion = generada(obraSocialEjemplo, "2026-10");

        // THEN el monto es el del cargo (12000.00), no el del arancel (12500.00), y la base lo acepta
        JsonNode linea = lineaDe(liquidacion, "Limpieza (profilaxis)");
        assertThat(numero(linea.path("montoSolicitado"))).isEqualByComparingTo("12000.00");
        assertThat(linea.path("origenPrecio").asText()).isEqualTo("CARGO");
        assertThat(sumaDeLasLineas(liquidacion.path("id").asLong())).isEqualByComparingTo("12000.00");
    }
}
