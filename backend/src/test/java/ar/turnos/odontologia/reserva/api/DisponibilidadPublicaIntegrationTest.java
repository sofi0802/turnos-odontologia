package ar.turnos.odontologia.reserva.api;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.reserva.IntegracionReservaBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Un test por escenario de {@code specs/disponibilidad-online}. */
@Tag("integration")
class DisponibilidadPublicaIntegrationTest extends IntegracionReservaBase {

    private static final String RUTA = "/api/v1/public/disponibilidad";

    private MvcResult pedir(String consulta) throws Exception {
        return sinSesion().get(RUTA + consulta).andReturn();
    }

    private MvcResult pedir(long prestacion, String fecha, Long profesional) throws Exception {
        String consulta = "?prestacionId=" + prestacion + "&fecha=" + fecha
                + (profesional == null ? "" : "&profesionalId=" + profesional);
        return pedir(consulta);
    }

    private void esperarInicios(MvcResult resultado, String... esperados) throws Exception {
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        List<String> horas = new ArrayList<>();
        for (String inicio : inicios(cuerpo(resultado))) {
            horas.add(inicio.length() == 5 ? inicio : inicio.substring(0, 5));
        }
        assertThat(horas).containsExactly(esperados);
    }

    @Nested
    @DisplayName("Horarios libres de un profesional elegido")
    class ProfesionalElegido {

        @Test
        @DisplayName("Profesional con una franja libre")
        void profesionalConUnaFranjaLibre() throws Exception {
            martes(draA, "10:00", "11:00");

            MvcResult respuesta = pedir(consulta, "2026-12-01", draA);

            esperarInicios(respuesta, "10:00", "10:15", "10:30");
            JsonNode horarios = cuerpo(respuesta);
            assertThat(horarios.get(2).get("fin").asText()).isEqualTo("2026-12-01T11:00:00-03:00");
        }

        @Test
        @DisplayName("Excluye los horarios que chocan o no respetan el margen")
        void excluyeLosQueChocanOSinMargen() throws Exception {
            martes(draA, "10:00", "12:00");
            turnoActivo(pacienteDos, draA, sillon1, 1, 10, 0);

            esperarInicios(pedir(consulta, "2026-12-01", draA), "10:45", "11:00", "11:15", "11:30");
        }

        @Test
        @DisplayName("Un turno cancelado no ocupa el horario")
        void canceladoNoOcupa() throws Exception {
            martes(draA, "10:00", "11:00");
            turnoCancelado(pacienteDos, draA, sillon1, 1, 10, 0);

            esperarInicios(pedir(consulta, "2026-12-01", draA), "10:00", "10:15", "10:30");
        }

        @Test
        @DisplayName("Día sin franja de trabajo")
        void diaSinFranja() throws Exception {
            franja(draA, 3, "10:00", "11:00");

            esperarInicios(pedir(consulta, "2026-12-01", draA));
        }
    }

    @Nested
    @DisplayName("Unión de horarios sin preferencia")
    class SinPreferencia {

        @Test
        @DisplayName("Unión sin horarios repetidos")
        void unionSinRepetidos() throws Exception {
            martes(draA, "10:00", "11:00");
            martes(drB, "10:30", "11:30");

            MvcResult respuesta = pedir(consulta, "2026-12-01", null);

            esperarInicios(respuesta, "10:00", "10:15", "10:30", "10:45", "11:00");
        }

        @Test
        @DisplayName("Profesional no habilitado excluido")
        void noHabilitadoExcluido() throws Exception {
            martes(draA, "10:00", "11:00");
            martes(drB, "11:00", "12:00");

            esperarInicios(pedir(conducto, "2026-12-01", null), "11:00", "11:15", "11:30");
        }

        @Test
        @DisplayName("Profesional inactivo excluido")
        void inactivoExcluido() throws Exception {
            martes(draA, "10:00", "11:00");
            jdbc.update("UPDATE profesional SET activo = FALSE WHERE id = ?", draA);
            martes(drB, "11:00", "12:00");

            esperarInicios(pedir(consulta, "2026-12-01", null), "11:00", "11:15", "11:30");
        }
    }

    @Nested
    @DisplayName("Un horario necesita un sillón libre")
    class SillonLibre {

        @Test
        @DisplayName("Profesional libre pero sin sillón")
        void libreSinSillon() throws Exception {
            jdbc.update("UPDATE sillon SET activo = FALSE WHERE id = ?", sillon2);
            martes(draA, "10:00", "11:30");
            turnoActivo(pacienteDos, drB, sillon1, 1, 10, 0);

            esperarInicios(pedir(consulta, "2026-12-01", draA), "10:45", "11:00");
        }
    }

    @Nested
    @DisplayName("Ausencias, bloqueos y horarios pasados")
    class AusenciasBloqueosYPasado {

        @Test
        @DisplayName("Bloqueo de agenda")
        void bloqueoDeAgenda() throws Exception {
            martes(draA, "10:00", "11:00");
            bloqueo(draA, 1, 10, 0, 10, 30);

            esperarInicios(pedir(consulta, "2026-12-01", draA), "10:30");
        }

        @Test
        @DisplayName("Licencia del profesional")
        void licenciaDelProfesional() throws Exception {
            martes(draA, "10:00", "11:00");
            ausencia(draA, "LICENCIA", 1, 0, 0, 23, 59, "Motivo administrativo ficticio");

            MvcResult respuesta = pedir(consulta, "2026-12-01", draA);

            esperarInicios(respuesta);
            assertThat(texto(respuesta)).doesNotContain("Motivo administrativo ficticio").doesNotContain("LICENCIA");
        }

        @Test
        @DisplayName("Horarios anteriores al momento actual")
        void anterioresAlMomentoActual() throws Exception {
            reloj.fijarEn(1, 10, 20);
            martes(draA, "10:00", "11:00");

            esperarInicios(pedir(consulta, "2026-12-01", draA), "10:30");
        }

        @Test
        @DisplayName("Fecha pasada")
        void fechaPasada() throws Exception {
            franja(draA, 1, "10:00", "11:00");

            esperarInicios(pedir(consulta, "2026-11-30", draA));
        }
    }

    @Nested
    @DisplayName("Disponibilidad pública sin datos de terceros")
    class SinDatosDeTerceros {

        @Test
        @DisplayName("Consulta sin sesión con la agenda ocupada")
        void sinSesionConAgendaOcupada() throws Exception {
            martes(draA, "10:00", "12:00");
            long turno = turnoActivo(pacienteDos, draA, sillon1, 1, 10, 0);

            MvcResult respuesta = pedir(consulta, "2026-12-01", null);

            assertThat(respuesta.getResponse().getStatus()).isEqualTo(200);
            String cuerpo = texto(respuesta);
            assertThat(cuerpo).doesNotContain("Paciente Dos").doesNotContain(DNI_DOS).doesNotContain(numeroDe(turno))
                    .doesNotContain("profesional").doesNotContain("sillon").doesNotContain("Ana Ejemplo");
            for (JsonNode horario : cuerpo(respuesta)) {
                assertThat(horario.fieldNames()).toIterable().containsExactlyInAnyOrder("inicio", "fin");
            }
        }
    }

    @Nested
    @DisplayName("Validación de la consulta de disponibilidad")
    class Validacion {

        @Test
        @DisplayName("Falta la fecha")
        void faltaLaFecha() throws Exception {
            MvcResult respuesta = pedir("?prestacionId=" + consulta);

            assertThat(respuesta.getResponse().getStatus()).isEqualTo(422);
            assertThat(cuerpo(respuesta).get("codigo").asText()).isEqualTo("DATOS_INVALIDOS");
            assertThat(camposConError(cuerpo(respuesta))).contains("fecha");
        }

        @Test
        @DisplayName("Fecha con formato inválido")
        void fechaConFormatoInvalido() throws Exception {
            MvcResult respuesta = pedir("?prestacionId=" + consulta + "&fecha=01/12/2026");

            assertThat(respuesta.getResponse().getStatus()).isEqualTo(422);
            assertThat(cuerpo(respuesta).get("codigo").asText()).isEqualTo("DATOS_INVALIDOS");
            assertThat(camposConError(cuerpo(respuesta))).contains("fecha");
        }

        @Test
        @DisplayName("Prestación inexistente")
        void prestacionInexistente() throws Exception {
            MvcResult respuesta = pedir("?prestacionId=999999&fecha=2026-12-01");

            assertThat(respuesta.getResponse().getStatus()).isEqualTo(422);
            assertThat(cuerpo(respuesta).get("codigo").asText()).isEqualTo("DATOS_INVALIDOS");
            assertThat(camposConError(cuerpo(respuesta))).contains("prestacionId");
        }

        @Test
        @DisplayName("Profesional inexistente")
        void profesionalInexistente() throws Exception {
            MvcResult respuesta = pedir(consulta, "2026-12-01", 999_999L);

            assertThat(respuesta.getResponse().getStatus()).isEqualTo(422);
            assertThat(camposConError(cuerpo(respuesta))).contains("profesionalId");
        }

        @Test
        @DisplayName("Falta la prestación")
        void faltaLaPrestacion() throws Exception {
            MvcResult respuesta = pedir("?fecha=2026-12-01");

            assertThat(respuesta.getResponse().getStatus()).isEqualTo(422);
            assertThat(camposConError(cuerpo(respuesta))).contains("prestacionId");
        }
    }

    @Nested
    @DisplayName("Combinaciones que no se ofrecen")
    class CombinacionesQueNoSeOfrecen {

        @Test
        @DisplayName("Profesional elegido no habilitado")
        void elegidoNoHabilitado() throws Exception {
            martes(draA, "10:00", "11:00");

            esperarInicios(pedir(conducto, "2026-12-01", draA));
        }

        @Test
        @DisplayName("Prestación inactiva")
        void prestacionInactiva() throws Exception {
            martes(draA, "10:00", "11:00");
            jdbc.update("UPDATE prestacion SET activo = FALSE WHERE id = ?", consulta);

            esperarInicios(pedir(consulta, "2026-12-01", draA));
        }
    }
}
