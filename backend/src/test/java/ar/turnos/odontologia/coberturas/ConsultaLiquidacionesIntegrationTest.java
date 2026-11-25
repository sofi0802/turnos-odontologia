package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Consulta de las liquidaciones (C-20, spec liquidaciones-obras-sociales, tarea 6.2): el listado con filtros y sin datos
 * de pacientes, y el detalle. Reloj: 2026-11-24 a las 12:00 en Buenos Aires. Solo datos ficticios del seed de la KB.
 */
class ConsultaLiquidacionesIntegrationTest extends IntegracionLiquidacionesBase {

    private long septiembreOse;
    private long octubreOse;
    private long octubrePrepaga;

    @BeforeEach
    void tresLiquidaciones() throws Exception {
        // GIVEN existen las liquidaciones de "Obra Social Ejemplo" de 2026-09 y 2026-10 y la de "Prepaga Prueba" de 2026-10
        semilla();
        atendido(pacienteUno, consulta, "2026-09-15T10:00");
        atendido(pacienteUno, limpieza, "2026-10-20T10:00");
        atendido(pacienteTres, consulta, "2026-10-07T10:00");
        septiembreOse = generada(obraSocialEjemplo, "2026-09").path("id").asLong();
        octubreOse = generada(obraSocialEjemplo, "2026-10").path("id").asLong();
        octubrePrepaga = generada(prepaga, "2026-10").path("id").asLong();
    }

    private static List<Long> ids(JsonNode lista) {
        List<Long> ids = new ArrayList<>();
        lista.forEach(l -> ids.add(l.path("id").asLong()));
        return ids;
    }

    @Test
    @DisplayName("Listar las liquidaciones de una obra social")
    void listarLasLiquidacionesDeUnaObraSocial() throws Exception {
        // WHEN admin lista las liquidaciones de "Obra Social Ejemplo"
        MvcResult r = get(como("admin"), LIQUIDACIONES + "?obraSocialId=" + obraSocialEjemplo);

        // THEN la respuesta tiene dos liquidaciones, primero la de 2026-10, sin nombres ni DNI de pacientes
        assertThat(status(r)).isEqualTo(200);
        JsonNode lista = cuerpo(r);
        assertThat(ids(lista)).containsExactly(octubreOse, septiembreOse);
        assertThat(lista.get(0).path("periodo").asText()).isEqualTo("2026-10");
        assertThat(lista.get(0).path("estado").asText()).isEqualTo("BORRADOR");
        assertThat(lista.get(0).path("obraSocial").path("nombre").asText()).isEqualTo("Obra Social Ejemplo");
        assertThat(lista.get(0).path("cantidadLineas").asInt()).isEqualTo(1);
        assertThat(numero(lista.get(0).path("totalSolicitado"))).isEqualByComparingTo("12500.00");
        assertThat(numero(lista.get(0).path("totalCobrado"))).isEqualByComparingTo("0.00");
        assertThat(texto(r)).doesNotContain("Paciente", "99000001", "99000003", "OSE-000001", "PP-000003", "paciente",
                "dni", "numeroAfiliado", "lineas", "Consulta o control", "Limpieza");
    }

    @Test
    @DisplayName("Listar sin filtros y filtrar por estado")
    void listarSinFiltrosYFiltrarPorEstado() throws Exception {
        // GIVEN la de 2026-09 de "Obra Social Ejemplo" está PRESENTADA
        presentar(septiembreOse);

        // WHEN admin lista todas, las presentadas y las presentadas de la prepaga
        JsonNode todas = cuerpo(get(como("admin"), LIQUIDACIONES));
        JsonNode presentadas = cuerpo(get(como("admin"), LIQUIDACIONES + "?estado=PRESENTADA"));
        JsonNode deLaPrepaga = cuerpo(get(como("admin"), LIQUIDACIONES + "?obraSocialId=" + prepaga
                + "&estado=PRESENTADA"));
        JsonNode borradores = cuerpo(get(como("admin"), LIQUIDACIONES + "?estado=BORRADOR"));

        // THEN el orden es el más reciente primero (período y después id) y cada filtro recorta el listado
        assertThat(ids(todas)).containsExactly(octubrePrepaga, octubreOse, septiembreOse);
        assertThat(ids(presentadas)).containsExactly(septiembreOse);
        assertThat(ids(deLaPrepaga)).isEmpty();
        assertThat(ids(borradores)).containsExactly(octubrePrepaga, octubreOse);
    }

    @Test
    @DisplayName("Un estado desconocido en el filtro es DATOS_INVALIDOS")
    void estadoDesconocido() throws Exception {
        MvcResult r = get(como("admin"), LIQUIDACIONES + "?estado=ANULADA");

        assertThat(status(r)).isEqualTo(422);
        assertThat(codigoDeError(r)).isEqualTo("DATOS_INVALIDOS");
    }

    @Test
    @DisplayName("El detalle trae los grupos por plan, las fechas y la conciliación de cada línea")
    void detalleCompleto() throws Exception {
        // WHEN admin consulta el detalle de la liquidación de 2026-10 de "Obra Social Ejemplo"
        JsonNode detalle = detalle(octubreOse);

        // THEN trae la cabecera, el grupo "Plan Base" con su subtotal, los totales y quién la generó
        assertThat(detalle.path("id").asLong()).isEqualTo(octubreOse);
        assertThat(detalle.path("estado").asText()).isEqualTo("BORRADOR");
        assertThat(detalle.path("generadaPor").asText()).isEqualTo("admin");
        assertThat(detalle.path("generadaEn").asText()).startsWith("2026-11-24T15:00");
        assertThat(detalle.path("presentadaEn").isNull()).isTrue();
        assertThat(detalle.path("cerradaEn").isNull()).isTrue();
        assertThat(detalle.path("cantidadLineas").asInt()).isEqualTo(1);
        assertThat(numero(grupoDe(detalle, "Plan Base").path("subtotal"))).isEqualByComparingTo("12500.00");
        JsonNode linea = lineaDe(detalle, "Limpieza (profilaxis)");
        assertThat(linea.path("paciente").path("nombre").asText()).isEqualTo("Paciente Uno");
        assertThat(linea.path("resultado").isNull()).isTrue();
        assertThat(linea.path("montoCobrado").isNull()).isTrue();
        assertThat(linea.path("conciliadaEn").isNull()).isTrue();
        // AND "turnosSinArancel" solo viaja al generar o regenerar
        assertThat(detalle.has("turnosSinArancel")).isFalse();
    }

    @Test
    @DisplayName("Liquidación inexistente")
    void liquidacionInexistente() throws Exception {
        // WHEN admin consulta una liquidación que no existe
        MvcResult r = get(como("admin"), url(999999L));

        // THEN la respuesta es 404 con el código RECURSO_NO_ENCONTRADO
        assertThat(status(r)).isEqualTo(404);
        assertThat(codigoDeError(r)).isEqualTo("RECURSO_NO_ENCONTRADO");
        assertThat(status(get(como("admin"), LIQUIDACIONES + "/abc"))).isEqualTo(422);
    }
}
