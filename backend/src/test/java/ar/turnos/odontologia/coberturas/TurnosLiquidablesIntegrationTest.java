package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Qué turnos entran en una liquidación y cuáles no (C-20, spec liquidaciones-obras-sociales, tarea 3.3, RN-LQ-01 y
 * RN-LQ-04). Reloj: 2026-11-24 a las 12:00 en Buenos Aires. Solo datos ficticios del seed de la KB.
 */
class TurnosLiquidablesIntegrationTest extends IntegracionLiquidacionesBase {

    private static List<String> pacientes(JsonNode detalle) {
        return lineasDe(detalle).stream().map(l -> l.path("paciente").path("nombre").asText()).toList();
    }

    @Test
    @DisplayName("Turnos atendidos de un afiliado")
    void turnosAtendidosDeUnAfiliado() throws Exception {
        // GIVEN "Paciente Uno" tiene turnos ATENDIDO de "Consulta o control" el 2026-10-06 y de "Limpieza (profilaxis)" el 2026-10-20
        semilla();
        atendido(pacienteUno, consulta, "2026-10-06T10:00");
        atendido(pacienteUno, limpieza, "2026-10-20T10:00");

        // WHEN admin genera la liquidación de "Obra Social Ejemplo" para 2026-10
        JsonNode liquidacion = generada(obraSocialEjemplo, "2026-10");

        // THEN la liquidación tiene dos líneas de "Paciente Uno" con "Plan Base", los códigos N-0101 y N-0201
        assertThat(lineasDe(liquidacion)).hasSize(2);
        assertThat(pacientes(liquidacion)).containsOnly("Paciente Uno");
        assertThat(grupoDe(liquidacion, "Plan Base").path("lineas")).hasSize(2);
        assertThat(lineaDe(liquidacion, "Consulta o control").path("codigo").asText()).isEqualTo("N-0101");
        assertThat(lineaDe(liquidacion, "Limpieza (profilaxis)").path("codigo").asText()).isEqualTo("N-0201");
        assertThat(lineaDe(liquidacion, "Consulta o control").path("fecha").asText()).isEqualTo("2026-10-06");
        assertThat(lineaDe(liquidacion, "Consulta o control").path("numeroAfiliado").asText()).isEqualTo("OSE-000001");
        assertThat(lineaDe(liquidacion, "Consulta o control").path("paciente").path("dni").asText())
                .isEqualTo("99000001");
    }

    @Test
    @DisplayName("Paciente particular")
    void pacienteParticular() throws Exception {
        // GIVEN "Paciente Dos" (particular) tiene un turno ATENDIDO de "Consulta o control" el 2026-10-08
        semilla();
        atendido(pacienteDos, consulta, "2026-10-08T10:00");

        // WHEN admin genera la liquidación de "Obra Social Ejemplo" para 2026-10
        JsonNode liquidacion = generada(obraSocialEjemplo, "2026-10");

        // THEN la liquidación no tiene ninguna línea de "Paciente Dos"
        assertThat(pacientes(liquidacion)).doesNotContain("Paciente Dos");
        assertThat(lineasDe(liquidacion)).isEmpty();
    }

    @Test
    @DisplayName("Código que el plan no cubre y prestación sin código")
    void codigoQueElPlanNoCubreYPrestacionSinCodigo() throws Exception {
        // GIVEN "Paciente Uno" tiene turnos ATENDIDO de "Extracción simple" (N-0301, sin cobertura en "Plan Base") el 2026-10-09 y de "Tratamiento de conducto" (sin código) el 2026-10-10
        semilla();
        long extraccionTurno = atendido(pacienteUno, extraccion, "2026-10-09T10:00");
        long conductoTurno = atendido(pacienteUno, conducto, "2026-10-10T10:00");

        // WHEN admin genera la liquidación de "Obra Social Ejemplo" para 2026-10
        JsonNode liquidacion = generada(obraSocialEjemplo, "2026-10");

        // THEN ninguno de los dos turnos está en la liquidación
        assertThat(idsDeTurnos(liquidacion)).doesNotContain(extraccionTurno, conductoTurno);
        assertThat(lineasDe(liquidacion)).isEmpty();
        // AND tampoco cuentan como turnos sin arancel: no cumplían las condiciones para entrar
        assertThat(liquidacion.path("turnosSinArancel").asInt()).isZero();
    }

    @Test
    @DisplayName("Turnos no atendidos")
    void turnosNoAtendidos() throws Exception {
        // GIVEN "Paciente Uno" tiene un turno AUSENTE el 2026-10-13, uno CANCELADO el 2026-10-14 y uno CONFIRMADO el 2026-10-15, todos de "Consulta o control"
        semilla();
        turno(pacienteUno, consulta, "2026-10-13T10:00", "AUSENTE");
        turno(pacienteUno, consulta, "2026-10-14T10:00", "CANCELADO");
        turno(pacienteUno, consulta, "2026-10-15T10:00", "CONFIRMADO");
        turno(pacienteUno, consulta, "2026-10-16T10:00", "RESERVADO");

        // WHEN admin genera la liquidación de "Obra Social Ejemplo" para 2026-10
        JsonNode liquidacion = generada(obraSocialEjemplo, "2026-10");

        // THEN ninguno de esos turnos está en la liquidación
        assertThat(lineasDe(liquidacion)).isEmpty();
    }

    @Test
    @DisplayName("Afiliado a otra obra social")
    void afiliadoAOtraObraSocial() throws Exception {
        // GIVEN "Paciente Tres" ("Plan Oro" de "Prepaga Prueba") tiene un turno ATENDIDO de "Consulta o control" el 2026-10-07
        semilla();
        long turno = atendido(pacienteTres, consulta, "2026-10-07T10:00");

        // WHEN admin genera las liquidaciones de 2026-10 de "Obra Social Ejemplo" y de "Prepaga Prueba"
        JsonNode deLaObraSocial = generada(obraSocialEjemplo, "2026-10");
        JsonNode deLaPrepaga = generada(prepaga, "2026-10");

        // THEN el turno no está en la de "Obra Social Ejemplo" y está en la de "Prepaga Prueba" con "Plan Oro" y el código N-0101
        assertThat(idsDeTurnos(deLaObraSocial)).doesNotContain(turno);
        assertThat(idsDeTurnos(deLaPrepaga)).containsExactly(turno);
        assertThat(grupoDe(deLaPrepaga, "Plan Oro").path("lineas")).hasSize(1);
        assertThat(lineaDe(deLaPrepaga, "Consulta o control").path("codigo").asText()).isEqualTo("N-0101");
    }

    @Test
    @DisplayName("Afiliación vigente en la fecha del turno")
    void afiliacionVigenteEnLaFechaDelTurno() throws Exception {
        // GIVEN "Paciente Dos" tiene un turno ATENDIDO de "Consulta o control" el 2026-10-02 y una afiliación a "Plan Base" vigente desde 2026-10-05
        semilla();
        afiliacion(pacienteDos, planBase, "OSE-000002", "2026-10-05", null);
        long antes = atendido(pacienteDos, consulta, "2026-10-02T10:00");
        long despues = atendido(pacienteDos, consulta, "2026-10-06T10:00");

        // WHEN admin genera la liquidación de "Obra Social Ejemplo" para 2026-10
        JsonNode liquidacion = generada(obraSocialEjemplo, "2026-10");

        // THEN el turno del 2026-10-02 no está en la liquidación
        assertThat(idsDeTurnos(liquidacion)).doesNotContain(antes);
        // AND el posterior a la vigencia sí
        assertThat(idsDeTurnos(liquidacion)).containsExactly(despues);
    }

    @Test
    @DisplayName("Afiliación ya cerrada antes del turno")
    void afiliacionYaCerrada() throws Exception {
        // GIVEN "Paciente Dos" estuvo afiliado a "Plan Base" hasta el 2026-10-10 y tiene turnos el 10-10 y el 10-11
        semilla();
        afiliacion(pacienteDos, planBase, "OSE-000002", "2026-01-01", "2026-10-10");
        long ultimoDia = atendido(pacienteDos, consulta, "2026-10-10T23:00");
        long despues = atendido(pacienteDos, consulta, "2026-10-11T08:00");

        // WHEN admin genera la liquidación de "Obra Social Ejemplo" para 2026-10
        JsonNode liquidacion = generada(obraSocialEjemplo, "2026-10");

        // THEN el último día de la vigencia entra (días inclusivos) y el siguiente no
        assertThat(idsDeTurnos(liquidacion)).containsExactly(ultimoDia).doesNotContain(despues);
    }

    @Test
    @DisplayName("Turno del último instante del mes")
    void turnoDelUltimoInstanteDelMes() throws Exception {
        // GIVEN "Paciente Uno" tiene turnos ATENDIDO de "Consulta o control" el 2026-10-31 a las 23:30 y el 2026-11-01 a las 00:00
        semilla();
        long deOctubre = atendido(pacienteUno, consulta, "2026-10-31T23:30");
        long deNoviembre = atendido(pacienteUno, consulta, "2026-11-01T00:00");

        // WHEN admin genera la liquidación de "Obra Social Ejemplo" para 2026-10
        JsonNode liquidacion = generada(obraSocialEjemplo, "2026-10");

        // THEN el turno del 2026-10-31 está en la liquidación y el del 2026-11-01 no
        assertThat(idsDeTurnos(liquidacion)).containsExactly(deOctubre).doesNotContain(deNoviembre);
        assertThat(lineaDe(liquidacion, "Consulta o control").path("fecha").asText()).isEqualTo("2026-10-31");
    }

    @Test
    @DisplayName("Turno ya liquidado")
    void turnoYaLiquidado() throws Exception {
        // GIVEN la liquidación de "Obra Social Ejemplo" de 2026-09 tiene el turno de "Paciente Uno" del 2026-09-15
        semilla();
        long deSeptiembre = atendido(pacienteUno, consulta, "2026-09-15T10:00");
        long deOctubre = atendido(pacienteUno, consulta, "2026-10-06T10:00");
        JsonNode septiembre = generada(obraSocialEjemplo, "2026-09");
        assertThat(idsDeTurnos(septiembre)).containsExactly(deSeptiembre);

        // WHEN admin genera la liquidación de "Obra Social Ejemplo" para 2026-10
        JsonNode octubre = generada(obraSocialEjemplo, "2026-10");

        // THEN ese turno no está en la de 2026-10
        assertThat(idsDeTurnos(octubre)).containsExactly(deOctubre).doesNotContain(deSeptiembre);
        assertThat(cantidad("liquidacion_linea")).isEqualTo(2);
    }

    @Test
    @DisplayName("Turno marcado atendido tarde")
    void turnoMarcadoAtendidoTarde() throws Exception {
        // GIVEN la liquidación de "Obra Social Ejemplo" de 2026-09 ya se generó
        semilla();
        long tarde = turno(pacienteUno, consulta, "2026-09-30T10:00", "CONFIRMADO");
        JsonNode septiembre = generada(obraSocialEjemplo, "2026-09");
        assertThat(lineasDe(septiembre)).isEmpty();
        // AND después se marcó ATENDIDO el turno de "Consulta o control" de "Paciente Uno" del 2026-09-30
        marcarAtendido(tarde);

        // WHEN admin genera la liquidación de "Obra Social Ejemplo" para 2026-10
        JsonNode octubre = generada(obraSocialEjemplo, "2026-10");

        // THEN la de 2026-10 tiene ese turno con fecha 2026-09-30 y monto 15000.00
        assertThat(idsDeTurnos(octubre)).containsExactly(tarde);
        JsonNode linea = lineaDe(octubre, "Consulta o control");
        assertThat(linea.path("fecha").asText()).isEqualTo("2026-09-30");
        assertThat(numero(linea.path("montoSolicitado"))).isEqualByComparingTo("15000.00");
    }
}
