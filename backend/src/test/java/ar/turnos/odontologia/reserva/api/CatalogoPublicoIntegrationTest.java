package ar.turnos.odontologia.reserva.api;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.reserva.IntegracionReservaBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Un test por escenario del requisito "Catálogo público para reservar" de {@code specs/disponibilidad-online}. */
@Tag("integration")
class CatalogoPublicoIntegrationTest extends IntegracionReservaBase {

    @Test
    @DisplayName("Prestaciones activas")
    void prestacionesActivas() throws Exception {
        jdbc.update("INSERT INTO prestacion(nombre, duracion_minutos, activo) VALUES ('Limpieza', 30, FALSE)");

        MvcResult respuesta = sinSesion().get("/api/v1/public/prestaciones").andReturn();

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(200);
        JsonNode prestaciones = cuerpo(respuesta);
        JsonNode primera = prestaciones.get(0);
        assertThat(primera.get("nombre").asText()).isEqualTo("Consulta");
        assertThat(primera.get("duracionMinutos").asInt()).isEqualTo(30);
        assertThat(primera.get("id").asLong()).isEqualTo(consulta);
        assertThat(texto(respuesta)).doesNotContain("Limpieza");
    }

    @Test
    @DisplayName("Profesionales habilitados para una prestación")
    void profesionalesHabilitados() throws Exception {
        MvcResult respuesta = sinSesion().get("/api/v1/public/profesionales?prestacionId=" + conducto).andReturn();

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(200);
        JsonNode profesionales = cuerpo(respuesta);
        assertThat(profesionales).hasSize(1);
        assertThat(profesionales.get(0).get("id").asLong()).isEqualTo(drB);
        assertThat(profesionales.get(0).get("nombre").asText()).isEqualTo("Bruno Prueba");
        assertThat(profesionales.get(0).fieldNames()).toIterable().containsExactlyInAnyOrder("id", "nombre");
        assertThat(texto(respuesta)).doesNotContain("MP-0002");
    }

    @Test
    @DisplayName("Profesionales sin prestación")
    void profesionalesSinPrestacion() throws Exception {
        MvcResult respuesta = sinSesion().get("/api/v1/public/profesionales").andReturn();

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(422);
        assertThat(cuerpo(respuesta).get("codigo").asText()).isEqualTo("DATOS_INVALIDOS");
        assertThat(camposConError(cuerpo(respuesta))).contains("prestacionId");
    }

    @Test
    @DisplayName("Los profesionales inactivos no se listan y una prestación inexistente es un error de datos")
    void inactivosYPrestacionInexistente() throws Exception {
        jdbc.update("UPDATE profesional SET activo = FALSE WHERE id = ?", drB);

        MvcResult sinB = sinSesion().get("/api/v1/public/profesionales?prestacionId=" + consulta).andReturn();
        assertThat(cuerpo(sinB)).hasSize(1);
        assertThat(cuerpo(sinB).get(0).get("id").asLong()).isEqualTo(draA);

        MvcResult inexistente = sinSesion().get("/api/v1/public/profesionales?prestacionId=999999").andReturn();
        assertThat(inexistente.getResponse().getStatus()).isEqualTo(422);
        assertThat(camposConError(cuerpo(inexistente))).contains("prestacionId");
    }
}
