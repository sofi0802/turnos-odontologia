package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Estados y vencimiento del presupuesto (C-19, spec presupuestos, tarea 5.2): aceptar y rechazar un pendiente, que un
 * presupuesto cerrado no cambie, la ruta del paciente, la falta de borrado y el vencimiento con el reloj ajustable.
 */
class EstadosPresupuestoIntegrationTest extends IntegracionPresupuestosBase {

    private static String aceptarUrl(long paciente, long presupuesto) {
        return presupuestoUrl(paciente, presupuesto) + "/aceptar";
    }

    private static String rechazarUrl(long paciente, long presupuesto) {
        return presupuestoUrl(paciente, presupuesto) + "/rechazar";
    }

    private long pendienteDe(long paciente) throws Exception {
        return emitirComo("ana.ejemplo", paciente, item(consulta, 1));
    }

    @Nested
    class EstadosDelPresupuesto {

        @Test
        @DisplayName("Aceptar un presupuesto pendiente")
        void aceptarUnPendiente() throws Exception {
            // GIVEN un presupuesto PENDIENTE de "Paciente Uno" emitido por ana.ejemplo
            semilla();
            long presupuesto = pendienteDe(pacienteUno);

            // WHEN bruno.prueba lo acepta
            MvcResult r = post(como("bruno.prueba"), aceptarUrl(pacienteUno, presupuesto), null);

            // THEN la respuesta es 200 con estado ACEPTADO
            assertThat(status(r)).isEqualTo(200);
            assertThat(cuerpo(r).path("estado").asText()).isEqualTo("ACEPTADO");
            assertThat(cuerpo(r).path("vencido").asBoolean()).isFalse();
            assertThat(estadoGuardado(presupuesto)).isEqualTo("ACEPTADO");
        }

        @Test
        @DisplayName("Rechazar un presupuesto pendiente")
        void rechazarUnPendiente() throws Exception {
            // GIVEN un presupuesto PENDIENTE de "Paciente Dos"
            semilla();
            long presupuesto = pendienteDe(pacienteDos);

            // WHEN ana.ejemplo lo rechaza
            MvcResult r = post(como("ana.ejemplo"), rechazarUrl(pacienteDos, presupuesto), null);

            // THEN la respuesta es 200 con estado RECHAZADO
            assertThat(status(r)).isEqualTo(200);
            assertThat(cuerpo(r).path("estado").asText()).isEqualTo("RECHAZADO");
            assertThat(estadoGuardado(presupuesto)).isEqualTo("RECHAZADO");
        }

        @Test
        @DisplayName("Un presupuesto aceptado no se rechaza")
        void unAceptadoNoSeRechaza() throws Exception {
            // GIVEN un presupuesto ACEPTADO de "Paciente Uno"
            semilla();
            long presupuesto = pendienteDe(pacienteUno);
            assertThat(status(post(como("ana.ejemplo"), aceptarUrl(pacienteUno, presupuesto), null))).isEqualTo(200);

            // WHEN ana.ejemplo lo rechaza
            MvcResult r = post(como("ana.ejemplo"), rechazarUrl(pacienteUno, presupuesto), null);

            // THEN la respuesta es 409 con el código PRESUPUESTO_CERRADO
            assertThat(status(r)).isEqualTo(409);
            assertThat(codigoDeError(r)).isEqualTo("PRESUPUESTO_CERRADO");
            // AND el presupuesto sigue ACEPTADO
            assertThat(estadoGuardado(presupuesto)).isEqualTo("ACEPTADO");
        }

        @Test
        @DisplayName("Un presupuesto rechazado no se acepta ni se vuelve a rechazar")
        void unRechazadoNoCambia() throws Exception {
            semilla();
            long presupuesto = pendienteDe(pacienteUno);
            assertThat(status(post(como("ana.ejemplo"), rechazarUrl(pacienteUno, presupuesto), null))).isEqualTo(200);
            MvcResult aceptar = post(como("ana.ejemplo"), aceptarUrl(pacienteUno, presupuesto), null);
            assertThat(status(aceptar)).isEqualTo(409);
            assertThat(codigoDeError(aceptar)).isEqualTo("PRESUPUESTO_CERRADO");
            MvcResult otraVez = post(como("ana.ejemplo"), rechazarUrl(pacienteUno, presupuesto), null);
            assertThat(status(otraVez)).isEqualTo(409);
            assertThat(estadoGuardado(presupuesto)).isEqualTo("RECHAZADO");
        }

        @Test
        @DisplayName("Presupuesto de otro paciente en la ruta")
        void presupuestoDeOtroPaciente() throws Exception {
            // GIVEN un presupuesto PENDIENTE de "Paciente Uno"
            semilla();
            long presupuesto = pendienteDe(pacienteUno);

            // WHEN ana.ejemplo lo acepta en la ruta de "Paciente Dos"
            MvcResult r = post(como("ana.ejemplo"), aceptarUrl(pacienteDos, presupuesto), null);

            // THEN la respuesta es 404 con el código RECURSO_NO_ENCONTRADO
            assertThat(status(r)).isEqualTo(404);
            assertThat(codigoDeError(r)).isEqualTo("RECURSO_NO_ENCONTRADO");
            // AND el presupuesto sigue PENDIENTE
            assertThat(estadoGuardado(presupuesto)).isEqualTo("PENDIENTE");
            // AND un presupuesto inexistente también es 404
            assertThat(status(post(como("ana.ejemplo"), rechazarUrl(pacienteUno, 999999L), null))).isEqualTo(404);
        }

        @Test
        @DisplayName("No hay borrado")
        void noHayBorrado() throws Exception {
            // GIVEN un presupuesto RECHAZADO de "Paciente Dos"
            semilla();
            long presupuesto = pendienteDe(pacienteDos);
            assertThat(status(post(como("ana.ejemplo"), rechazarUrl(pacienteDos, presupuesto), null))).isEqualTo(200);

            // WHEN admin pide DELETE sobre ese presupuesto
            MvcResult r = delete(como("admin"), presupuestoUrl(pacienteDos, presupuesto));

            // THEN la respuesta no es exitosa y el presupuesto sigue existiendo
            assertThat(status(r)).isGreaterThanOrEqualTo(400);
            assertThat(cantidad("presupuesto")).isEqualTo(1);
        }
    }

    @Nested
    class VencimientoDelPresupuesto {

        private long vencimiento2026_12_24() throws Exception {
            // un presupuesto PENDIENTE de "Paciente Uno" con fecha 2026-11-24 y vencimiento 2026-12-24
            semilla();
            long presupuesto = pendienteDe(pacienteUno);
            assertThat(jdbc.queryForObject("SELECT vence_el::text FROM presupuesto WHERE id = ?", String.class,
                    presupuesto)).isEqualTo("2026-12-24");
            return presupuesto;
        }

        @Test
        @DisplayName("Se acepta el día del vencimiento")
        void seAceptaElDiaDelVencimiento() throws Exception {
            long presupuesto = vencimiento2026_12_24();

            // WHEN el 2026-12-24 a las 23:59 de Buenos Aires ana.ejemplo lo acepta
            ahoraEs("2026-12-25T02:59:00Z");
            MvcResult r = post(como("ana.ejemplo"), aceptarUrl(pacienteUno, presupuesto), null);

            // THEN la respuesta es 200 con estado ACEPTADO
            assertThat(status(r)).isEqualTo(200);
            assertThat(cuerpo(r).path("estado").asText()).isEqualTo("ACEPTADO");
        }

        @Test
        @DisplayName("No se acepta un presupuesto vencido")
        void noSeAceptaUnVencido() throws Exception {
            long presupuesto = vencimiento2026_12_24();

            // WHEN el 2026-12-25 a las 00:00 de Buenos Aires ana.ejemplo lo acepta
            ahoraEs("2026-12-25T03:00:00Z");
            MvcResult r = post(como("ana.ejemplo"), aceptarUrl(pacienteUno, presupuesto), null);

            // THEN la respuesta es 409 con el código PRESUPUESTO_VENCIDO
            assertThat(status(r)).isEqualTo(409);
            assertThat(codigoDeError(r)).isEqualTo("PRESUPUESTO_VENCIDO");
            // AND el presupuesto sigue PENDIENTE y se informa vencido
            assertThat(estadoGuardado(presupuesto)).isEqualTo("PENDIENTE");
            JsonNode p = presupuesto(como("ana.ejemplo"), pacienteUno, presupuesto);
            assertThat(p.path("estado").asText()).isEqualTo("PENDIENTE");
            assertThat(p.path("vencido").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("Se rechaza un presupuesto vencido")
        void seRechazaUnVencido() throws Exception {
            long presupuesto = vencimiento2026_12_24();

            // WHEN el 2026-12-30 ana.ejemplo lo rechaza
            ahoraEs("2026-12-30T15:00:00Z");
            MvcResult r = post(como("ana.ejemplo"), rechazarUrl(pacienteUno, presupuesto), null);

            // THEN la respuesta es 200 con estado RECHAZADO y no se informa vencido
            assertThat(status(r)).isEqualTo(200);
            assertThat(cuerpo(r).path("estado").asText()).isEqualTo("RECHAZADO");
            assertThat(cuerpo(r).path("vencido").asBoolean()).isFalse();
            assertThat(presupuesto(como("ana.ejemplo"), pacienteUno, presupuesto).path("vencido").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("Un presupuesto aceptado no se informa vencido con el paso del tiempo")
        void unAceptadoNoSeInformaVencido() throws Exception {
            long presupuesto = vencimiento2026_12_24();
            assertThat(status(post(como("ana.ejemplo"), aceptarUrl(pacienteUno, presupuesto), null))).isEqualTo(200);
            ahoraEs("2027-03-01T15:00:00Z");
            JsonNode p = presupuesto(como("ana.ejemplo"), pacienteUno, presupuesto);
            assertThat(p.path("estado").asText()).isEqualTo("ACEPTADO");
            assertThat(p.path("vencido").asBoolean()).isFalse();
        }
    }
}
