package ar.turnos.odontologia.agenda.disponibilidad;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Un test por escenario de ausencias, bloqueos y turnos afectados del spec {@code disponibilidad-profesional}
 * (C-07).
 */
class AusenciasYBloqueosIntegrationTest extends IntegracionDisponibilidadBase {

    @Nested
    class Ausencias {

        @Test
        @DisplayName("Registrar una licencia")
        void registrarUnaLicencia() throws Exception {
            MvcResult resultado = registrarAusencia(como("recepcion"), draA, "LICENCIA",
                    diciembre(7, 0, 0), diciembre(12, 0, 0));

            assertThat(status(resultado)).isEqualTo(201);
            JsonNode periodo = cuerpo(resultado).path("periodo");
            assertThat(periodo.path("tipo").asText()).isEqualTo("LICENCIA");
            assertThat(idsDe(como("admin").get(ausencias(draA)).andReturn()))
                    .containsExactly(periodo.path("id").asLong());
        }

        @Test
        @DisplayName("Período con el fin antes del inicio")
        void periodoConElFinAntesDelInicio() throws Exception {
            MvcResult resultado = registrarAusencia(como("recepcion"), draA, "AUSENCIA",
                    diciembre(1, 12, 0), diciembre(1, 10, 0));

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("PERIODO_INVALIDO");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM ausencia_profesional", Integer.class)).isZero();
        }

        @Test
        @DisplayName("Borrar una ausencia")
        void borrarUnaAusencia() throws Exception {
            long id = idCreado(registrarAusencia(como("recepcion"), draA, "AUSENCIA",
                    diciembre(1, 10, 0), diciembre(1, 12, 0)));

            MvcResult resultado = borrar(como("admin"), ausencias(draA) + "/" + id);

            assertThat(status(resultado)).isEqualTo(204);
            assertThat(idsDe(como("admin").get(ausencias(draA)).andReturn())).doesNotContain(id);
        }

        @Test
        @DisplayName("El odontólogo consulta sus ausencias")
        void elOdontologoConsultaSusAusencias() throws Exception {
            long id = idCreado(registrarAusencia(como("recepcion"), draA, "LICENCIA",
                    diciembre(7, 0, 0), diciembre(12, 0, 0)));

            MvcResult resultado = como("ana.ejemplo").get(ausencias(draA)).andReturn();

            assertThat(status(resultado)).isEqualTo(200);
            assertThat(idsDe(resultado)).containsExactly(id);
            // y no las de otro profesional
            assertThat(status(como("ana.ejemplo").get(ausencias(drB)).andReturn())).isEqualTo(403);
        }

        @Test
        @DisplayName("El odontólogo intenta registrar una ausencia")
        void elOdontologoIntentaRegistrarUnaAusencia() throws Exception {
            MvcResult resultado = registrarAusencia(como("ana.ejemplo"), draA, "AUSENCIA",
                    diciembre(1, 10, 0), diciembre(1, 12, 0));

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
        }
    }

    @Nested
    class Bloqueos {

        @Test
        @DisplayName("El odontólogo bloquea su agenda")
        void elOdontologoBloqueaSuAgenda() throws Exception {
            MvcResult resultado = bloquear(como("ana.ejemplo"), draA, diciembre(1, 11, 0), diciembre(1, 12, 0));

            assertThat(status(resultado)).isEqualTo(201);
            long id = cuerpo(resultado).path("periodo").path("id").asLong();
            assertThat(idsDe(como("ana.ejemplo").get(bloqueos(draA)).andReturn())).containsExactly(id);
        }

        @Test
        @DisplayName("Borrar un bloqueo")
        void borrarUnBloqueo() throws Exception {
            long id = idCreado(bloquear(como("ana.ejemplo"), draA, diciembre(1, 11, 0), diciembre(1, 12, 0)));

            MvcResult resultado = borrar(como("ana.ejemplo"), bloqueos(draA) + "/" + id);

            assertThat(status(resultado)).isEqualTo(204);
            assertThat(idsDe(como("ana.ejemplo").get(bloqueos(draA)).andReturn())).isEmpty();
        }

        @Test
        @DisplayName("Un odontólogo bloquea la agenda de otro")
        void unOdontologoBloqueaLaAgendaDeOtro() throws Exception {
            MvcResult resultado = bloquear(como("bruno.prueba"), draA, diciembre(1, 11, 0), diciembre(1, 12, 0));

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM bloqueo_agenda", Integer.class)).isZero();
        }

        @Test
        @DisplayName("La recepción consulta los bloqueos")
        void laRecepcionConsultaLosBloqueos() throws Exception {
            // GIVEN un bloqueo de la Dra. Ana Ejemplo el 2026-12-01 de 11:00 a 12:00
            long id = idCreado(bloquear(como("ana.ejemplo"), draA, diciembre(1, 11, 0), diciembre(1, 12, 0)));

            // WHEN recepcion consulta los bloqueos de la Dra. Ana Ejemplo
            MvcResult resultado = como("recepcion").get(bloqueos(draA)).andReturn();

            // THEN la respuesta es 200 e incluye ese bloqueo
            assertThat(status(resultado)).isEqualTo(200);
            assertThat(idsDe(resultado)).containsExactly(id);
        }

        @Test
        @DisplayName("La recepción registra un bloqueo")
        void laRecepcionRegistraUnBloqueo() throws Exception {
            MvcResult resultado = bloquear(como("recepcion"), draA, diciembre(1, 11, 0), diciembre(1, 12, 0));

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM bloqueo_agenda", Integer.class)).isZero();
        }

        @Test
        @DisplayName("La recepción borra un bloqueo")
        void laRecepcionBorraUnBloqueo() throws Exception {
            // GIVEN un bloqueo de la Dra. Ana Ejemplo el 2026-12-01 de 11:00 a 12:00
            long id = idCreado(bloquear(como("ana.ejemplo"), draA, diciembre(1, 11, 0), diciembre(1, 12, 0)));

            // WHEN recepcion lo borra
            MvcResult resultado = borrar(como("recepcion"), bloqueos(draA) + "/" + id);

            // THEN la respuesta es 403 ACCESO_DENEGADO y el bloqueo sigue registrado
            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(idsDe(como("admin").get(bloqueos(draA)).andReturn())).containsExactly(id);
        }
    }

    @Nested
    class TurnosAfectados {

        @Test
        @DisplayName("Licencia con turnos dados")
        void licenciaConTurnosDados() throws Exception {
            cargarDeLunesAViernes(draA, "08:00", "13:00");
            long activo = turnoCreado(pacienteUno, draA, sillon1, diciembre(9, 10, 0)).path("id").asLong();
            long cancelado = turnoCreado(pacienteDos, draA, sillon1, diciembre(9, 11, 0)).path("id").asLong();
            jdbc.update("UPDATE turno SET estado = 'CANCELADO' WHERE id = ?", cancelado);

            MvcResult resultado = registrarAusencia(como("recepcion"), draA, "LICENCIA",
                    diciembre(7, 0, 0), diciembre(12, 0, 0));

            assertThat(status(resultado)).isEqualTo(201);
            JsonNode afectados = cuerpo(resultado).path("turnosAfectados");
            assertThat(afectados).hasSize(1);
            assertThat(afectados.get(0).path("turnoId").asLong()).isEqualTo(activo);
            assertThat(afectados.get(0).path("inicio").asText()).isEqualTo(diciembre(9, 10, 0));
            assertThat(afectados.get(0).path("fin").asText()).isEqualTo(diciembre(9, 10, 30));
            assertThat(afectados.get(0).path("prestacionId").asLong()).isEqualTo(consulta);
        }

        @Test
        @DisplayName("Bloqueo sin turnos dados")
        void bloqueoSinTurnosDados() throws Exception {
            MvcResult resultado = bloquear(como("ana.ejemplo"), draA, diciembre(1, 11, 0), diciembre(1, 12, 0));

            assertThat(status(resultado)).isEqualTo(201);
            assertThat(cuerpo(resultado).path("turnosAfectados").isArray()).isTrue();
            assertThat(cuerpo(resultado).path("turnosAfectados")).isEmpty();
        }

        @Test
        @DisplayName("Los turnos afectados no muestran al paciente")
        void losTurnosAfectadosNoMuestranAlPaciente() throws Exception {
            cargarDeLunesAViernes(draA, "08:00", "13:00");
            turnoCreado(pacienteUno, draA, sillon1, diciembre(9, 10, 0));

            MvcResult resultado = registrarAusencia(como("recepcion"), draA, "LICENCIA",
                    diciembre(7, 0, 0), diciembre(12, 0, 0));

            String respuesta = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(cuerpo(resultado).path("turnosAfectados")).hasSize(1);
            assertThat(respuesta).doesNotContain("Paciente", DNI_PACIENTE_UNO, "pacienteId", "numeroReserva");
        }
    }

    private List<Long> idsDe(MvcResult resultado) throws Exception {
        List<Long> ids = new ArrayList<>();
        cuerpo(resultado).forEach(p -> ids.add(p.path("id").asLong()));
        return ids;
    }
}
