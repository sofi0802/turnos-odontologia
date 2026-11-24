package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.shared.security.Navegador;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Nomenclador de prácticas con código único (C-18, spec catalogo-coberturas, tarea 3.3). */
@Tag("integration")
class NomencladorIntegrationTest extends IntegracionCoberturasBase {

    @Test
    @DisplayName("Alta de un código vinculado a una prestación")
    void altaDeUnCodigoVinculadoAUnaPrestacion() throws Exception {
        // GIVEN la prestación "Consulta o control"
        long consulta = prestacion("Consulta o control");

        // WHEN admin da de alta N-0101 "Consulta" vinculado a "Consulta o control"
        MvcResult r = post(como("admin"), NOMENCLADOR, codigoSolicitud("N-0101", "Consulta", consulta));

        // THEN la respuesta es 201 con el código activo y la prestación "Consulta o control"
        assertThat(status(r)).isEqualTo(201);
        JsonNode c = cuerpo(r);
        assertThat(c.path("codigo").asText()).isEqualTo("N-0101");
        assertThat(c.path("activo").asBoolean()).isTrue();
        assertThat(c.path("prestacion").path("id").asLong()).isEqualTo(consulta);
        assertThat(c.path("prestacion").path("nombre").asText()).isEqualTo("Consulta o control");
        assertThat(r.getResponse().getHeader("Location")).endsWith(NOMENCLADOR + "/" + c.path("id").asLong());
    }

    @Test
    @DisplayName("Alta de un código sin prestación")
    void altaDeUnCodigoSinPrestacion() throws Exception {
        // WHEN admin da de alta N-0301 "Extracción simple" sin prestación
        MvcResult r = post(como("admin"), NOMENCLADOR, codigoSolicitud("N-0301", "Extracción simple", null));

        // THEN la respuesta es 201 con el código activo y sin prestación
        assertThat(status(r)).isEqualTo(201);
        assertThat(cuerpo(r).path("activo").asBoolean()).isTrue();
        assertThat(cuerpo(r).path("prestacion").isNull()).isTrue();
    }

    @Test
    @DisplayName("Código repetido")
    void codigoRepetido() throws Exception {
        // GIVEN existe N-0101
        codigo("N-0101", "Consulta", null);

        // WHEN admin da de alta n-0101 "Otra consulta"
        MvcResult r = post(como("admin"), NOMENCLADOR, codigoSolicitud("n-0101", "Otra consulta", null));

        // THEN la respuesta es 409 con el código CODIGO_NOMENCLADOR_EXISTENTE
        assertThat(status(r)).isEqualTo(409);
        assertThat(codigoDeError(r)).isEqualTo("CODIGO_NOMENCLADOR_EXISTENTE");
        assertThat(cantidad("nomenclador")).isEqualTo(1);
    }

    @Test
    @DisplayName("Prestación inexistente")
    void prestacionInexistente() throws Exception {
        // WHEN admin da de alta N-0201 "Limpieza" vinculado a la prestación con id 999999
        MvcResult r = post(como("admin"), NOMENCLADOR, codigoSolicitud("N-0201", "Limpieza", 999999L));

        // THEN la respuesta es 422 con el código PRESTACION_INEXISTENTE
        assertThat(status(r)).isEqualTo(422);
        assertThat(codigoDeError(r)).isEqualTo("PRESTACION_INEXISTENTE");
        // AND no existe el código N-0201
        assertThat(cantidad("nomenclador")).isZero();
    }

    @Test
    @DisplayName("Quitar el vínculo con la prestación")
    void quitarElVinculoConLaPrestacion() throws Exception {
        // GIVEN N-0101 está vinculado a "Consulta o control"
        long consulta = prestacion("Consulta o control");
        long id = codigo("N-0101", "Consulta", consulta);

        // WHEN admin modifica N-0101 sin prestación
        MvcResult r = put(como("admin"), NOMENCLADOR + "/" + id, codigoSolicitud("N-0101", "Consulta", null));

        // THEN la respuesta es 200 y N-0101 queda sin prestación
        assertThat(status(r)).isEqualTo(200);
        assertThat(cuerpo(r).path("prestacion").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT prestacion_id FROM nomenclador WHERE id = ?", Long.class, id)).isNull();
    }

    @Test
    @DisplayName("Un código desactivado sigue en la lista")
    void unCodigoDesactivadoSigueEnLaLista() throws Exception {
        // GIVEN N-0301 está activo
        long id = codigo("N-0301", "Extracción simple", null);
        Navegador admin = como("admin");

        // WHEN admin desactiva N-0301 y luego consulta el nomenclador
        MvcResult desactivado = post(admin, NOMENCLADOR + "/" + id + "/desactivar", null);
        MvcResult lista = get(admin, NOMENCLADOR);

        // THEN la lista incluye N-0301 marcado como inactivo
        assertThat(status(desactivado)).isEqualTo(200);
        assertThat(cuerpo(lista)).hasSize(1);
        assertThat(cuerpo(lista).get(0).path("codigo").asText()).isEqualTo("N-0301");
        assertThat(cuerpo(lista).get(0).path("activo").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Reactivar un código")
    void reactivarUnCodigo() throws Exception {
        long id = codigo("N-0301", "Extracción simple", null);
        jdbc.update("UPDATE nomenclador SET activo = FALSE WHERE id = ?", id);

        MvcResult r = post(como("admin"), NOMENCLADOR + "/" + id + "/activar", null);

        assertThat(status(r)).isEqualTo(200);
        assertThat(cuerpo(r).path("activo").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Listar el nomenclador")
    void listarElNomenclador() throws Exception {
        // GIVEN existen N-0201, N-0101 y N-0301
        long limpieza = prestacion("Limpieza (profilaxis)");
        codigo("N-0201", "Limpieza", limpieza);
        codigo("N-0101", "Consulta", null);
        codigo("N-0301", "Extracción simple", null);

        // WHEN ana.ejemplo lista el nomenclador
        MvcResult r = get(como("ana.ejemplo"), NOMENCLADOR);

        // THEN la respuesta es 200 con N-0101, N-0201 y N-0301, en ese orden
        assertThat(status(r)).isEqualTo(200);
        assertThat(cuerpo(r)).extracting(c -> c.path("codigo").asText()).containsExactly("N-0101", "N-0201", "N-0301");
        assertThat(cuerpo(r).get(1).path("prestacion").path("nombre").asText()).isEqualTo("Limpieza (profilaxis)");
    }

    @Test
    @DisplayName("Consultar un código por id")
    void consultarUnCodigoPorId() throws Exception {
        long id = codigo("N-0101", "Consulta", null);
        Navegador recepcion = como("recepcion");

        assertThat(cuerpo(get(recepcion, NOMENCLADOR + "/" + id)).path("codigo").asText()).isEqualTo("N-0101");
        MvcResult inexistente = get(recepcion, NOMENCLADOR + "/999999");
        assertThat(status(inexistente)).isEqualTo(404);
        assertThat(codigoDeError(inexistente)).isEqualTo("RECURSO_NO_ENCONTRADO");
    }

    @Test
    @DisplayName("Una misma prestación puede tener varios códigos")
    void variosCodigosPorPrestacion() throws Exception {
        long consulta = prestacion("Consulta o control");
        Navegador admin = como("admin");

        assertThat(status(post(admin, NOMENCLADOR, codigoSolicitud("N-0101", "Consulta", consulta)))).isEqualTo(201);
        assertThat(status(post(admin, NOMENCLADOR, codigoSolicitud("N-0102", "Control", consulta)))).isEqualTo(201);
    }

    @Test
    @DisplayName("Modificar un código con el código de otro")
    void modificarConElCodigoDeOtro() throws Exception {
        codigo("N-0101", "Consulta", null);
        long otro = codigo("N-0201", "Limpieza", null);

        MvcResult r = put(como("admin"), NOMENCLADOR + "/" + otro, codigoSolicitud("n-0101", "Limpieza", null));

        assertThat(status(r)).isEqualTo(409);
        assertThat(codigoDeError(r)).isEqualTo("CODIGO_NOMENCLADOR_EXISTENTE");
    }
}
