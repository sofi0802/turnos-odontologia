package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Sugerencias de presupuesto desde el odontograma (C-19, spec presupuestos, tarea 6.1, design D8): los registros se
 * insertan por SQL en {@code odontograma_registro}, sin usar código de {@code clinica/}.
 */
class SugerenciasPresupuestoIntegrationTest extends IntegracionPresupuestosBase {

    private static String sugerenciasUrl(long paciente) {
        return presupuestosUrl(paciente) + "/sugerencias";
    }

    private void registro(long paciente, int pieza, String cara, String estado, String instante) {
        jdbc.update("INSERT INTO odontograma_registro(paciente_id, pieza, cara, estado, profesional_id, registrado_en) "
                + "VALUES (?,?,?,?,?, CAST(? AS timestamptz))", paciente, pieza, cara, estado, profesionalAna, instante);
    }

    @Test
    @DisplayName("Sugerir caries y piezas a tratar")
    void sugerirCariesYPiezasATratar() throws Exception {
        // GIVEN el odontograma de "Paciente Uno" tiene la pieza 16 cara O en CARIES, la pieza 36 en A_TRATAR y la 21 en SANO
        registro(pacienteUno, 36, null, "A_TRATAR", "2026-11-01T10:00:00Z");
        registro(pacienteUno, 21, null, "SANO", "2026-11-01T10:01:00Z");
        registro(pacienteUno, 16, "O", "CARIES", "2026-11-01T10:02:00Z");
        int antes = cantidad("odontograma_registro");

        // WHEN ana.ejemplo pide las sugerencias de presupuesto de "Paciente Uno"
        MvcResult r = get(como("ana.ejemplo"), sugerenciasUrl(pacienteUno));

        // THEN la respuesta es 200 con la pieza 16 cara O (CARIES) y la pieza 36 sin cara (A_TRATAR), en ese orden
        assertThat(status(r)).isEqualTo(200);
        JsonNode lista = cuerpo(r);
        assertThat(lista).hasSize(2);
        assertThat(lista.get(0).path("pieza").asInt()).isEqualTo(16);
        assertThat(lista.get(0).path("cara").asText()).isEqualTo("O");
        assertThat(lista.get(0).path("estado").asText()).isEqualTo("CARIES");
        assertThat(lista.get(1).path("pieza").asInt()).isEqualTo(36);
        assertThat(lista.get(1).path("cara").isNull()).isTrue();
        assertThat(lista.get(1).path("estado").asText()).isEqualTo("A_TRATAR");
        // AND consultarlas no crea un presupuesto ni cambia el odontograma
        assertThat(cantidad("presupuesto")).isZero();
        assertThat(cantidad("odontograma_registro")).isEqualTo(antes);
    }

    @Test
    @DisplayName("Solo cuenta el último estado")
    void soloCuentaElUltimoEstado() throws Exception {
        // GIVEN la pieza 16 cara O de "Paciente Uno" se registró CARIES y después OBTURADO
        registro(pacienteUno, 16, "O", "CARIES", "2026-11-01T10:00:00Z");
        registro(pacienteUno, 16, "O", "OBTURADO", "2026-11-10T10:00:00Z");
        registro(pacienteUno, 26, "M", "CARIES", "2026-11-02T10:00:00Z");

        // WHEN ana.ejemplo pide las sugerencias de presupuesto de "Paciente Uno"
        MvcResult r = get(como("ana.ejemplo"), sugerenciasUrl(pacienteUno));

        // THEN la respuesta no incluye la pieza 16 cara O
        assertThat(status(r)).isEqualTo(200);
        JsonNode lista = cuerpo(r);
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).path("pieza").asInt()).isEqualTo(26);
    }

    @Test
    @DisplayName("Paciente sin odontograma")
    void pacienteSinOdontograma() throws Exception {
        // GIVEN "Paciente Dos" no tiene registros en el odontograma (y otro paciente sí)
        registro(pacienteUno, 16, "O", "CARIES", "2026-11-01T10:00:00Z");

        // WHEN ana.ejemplo pide las sugerencias de presupuesto de "Paciente Dos"
        MvcResult r = get(como("ana.ejemplo"), sugerenciasUrl(pacienteDos));

        // THEN la respuesta es 200 con una lista vacía
        assertThat(status(r)).isEqualTo(200);
        assertThat(cuerpo(r).isArray()).isTrue();
        assertThat(cuerpo(r)).isEmpty();
    }

    @Test
    @DisplayName("Un paciente inexistente es 404")
    void pacienteInexistente() throws Exception {
        MvcResult r = get(como("ana.ejemplo"), sugerenciasUrl(999999L));
        assertThat(status(r)).isEqualTo(404);
        assertThat(codigoDeError(r)).isEqualTo("RECURSO_NO_ENCONTRADO");
    }
}
