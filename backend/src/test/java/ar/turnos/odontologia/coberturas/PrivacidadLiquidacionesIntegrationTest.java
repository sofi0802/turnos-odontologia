package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Privacidad de la liquidación (C-20, spec liquidaciones-obras-sociales, tarea 8.2, RN-LQ-08): los nombres, DNI y
 * números de afiliado de los pacientes, las prestaciones y los importes no aparecen en los logs ni en los errores.
 * Reloj: 2026-11-24 a las 12:00 en Buenos Aires. Solo datos ficticios del seed de la KB.
 */
@ExtendWith(OutputCaptureExtension.class)
class PrivacidadLiquidacionesIntegrationTest extends IntegracionLiquidacionesBase {

    private static final String[] DATOS_PERSONALES = {"99000001", "OSE-000001", "Paciente Uno", "Consulta o control",
            "Limpieza (profilaxis)", "15000", "12500", "27500"};

    @Test
    @DisplayName("Generar y fallar sin datos en los logs")
    void generarYFallarSinDatosEnLosLogs(CapturedOutput salida) throws Exception {
        // GIVEN "Paciente Uno" tiene un turno ATENDIDO de "Consulta o control" en octubre
        semilla();
        atendido(pacienteUno, consulta, "2026-10-06T10:00");

        // WHEN admin genera la liquidación de "Obra Social Ejemplo" de 2026-10 y después la genera otra vez
        MvcResult primera = generar(obraSocialEjemplo, "2026-10");
        MvcResult repetida = generar(obraSocialEjemplo, "2026-10");

        // THEN los logs no contienen 99000001, OSE-000001, Paciente Uno, Consulta o control ni 15000
        assertThat(status(primera)).isEqualTo(201);
        assertThat(status(repetida)).isEqualTo(409);
        assertThat(salida.getAll()).doesNotContain(DATOS_PERSONALES);
        // AND el 409 LIQUIDACION_EXISTENTE no menciona pacientes ni importes
        assertThat(codigoDeError(repetida)).isEqualTo("LIQUIDACION_EXISTENTE");
        assertThat(texto(repetida)).doesNotContain(DATOS_PERSONALES);
    }

    @Test
    @DisplayName("Conciliar, cerrar y descargar el CSV sin datos en los logs ni en los errores")
    void conciliarCerrarYDescargarSinDatos(CapturedOutput salida) throws Exception {
        // GIVEN la liquidación de 2026-10 con la consulta y la limpieza, PRESENTADA
        semilla();
        atendido(pacienteUno, consulta, "2026-10-06T10:00");
        atendido(pacienteUno, limpieza, "2026-10-20T10:00");
        JsonNode generada = generada(obraSocialEjemplo, "2026-10");
        long id = generada.path("id").asLong();
        long consultaId = lineaDe(generada, "Consulta o control").path("id").asLong();
        long limpiezaId = lineaDe(generada, "Limpieza (profilaxis)").path("id").asLong();
        MvcResult csvEnBorrador = get(como("admin"), url(id) + "/csv");
        presentar(id);

        // WHEN admin concilia con montos y motivos inválidos, concilia bien, cierra de más y descarga el CSV
        MvcResult mayor = conciliar(id, consultaId, new BigDecimal("15000.01"), null);
        MvcResult sinMotivo = conciliar(id, limpiezaId, new BigDecimal("0.00"), null);
        MvcResult motivoDeMas = conciliar(id, consultaId, new BigDecimal("15000.00"), "Motivo ficticio");
        MvcResult ajena = conciliar(id, 999999L, new BigDecimal("1.00"), null);
        MvcResult prematuro = post(como("admin"), url(id) + "/cerrar", null);
        MvcResult ok1 = conciliar(id, consultaId, new BigDecimal("15000.00"), null);
        MvcResult ok2 = conciliar(id, limpiezaId, new BigDecimal("0.00"), "Falta la orden de la práctica");
        MvcResult cierre = post(como("admin"), url(id) + "/cerrar", null);
        MvcResult tardia = conciliar(id, consultaId, new BigDecimal("1.00"), null);
        MvcResult csv = get(como("admin"), url(id) + "/csv");

        // THEN cada error responde su código y no repite datos del paciente ni importes
        assertThat(status(csvEnBorrador)).isEqualTo(409);
        assertThat(status(mayor)).isEqualTo(422);
        assertThat(status(sinMotivo)).isEqualTo(422);
        assertThat(status(motivoDeMas)).isEqualTo(422);
        assertThat(status(ajena)).isEqualTo(404);
        assertThat(status(prematuro)).isEqualTo(409);
        assertThat(status(ok1)).isEqualTo(200);
        assertThat(status(ok2)).isEqualTo(200);
        assertThat(status(cierre)).isEqualTo(200);
        assertThat(status(tardia)).isEqualTo(409);
        assertThat(status(csv)).isEqualTo(200);
        for (MvcResult error : new MvcResult[] {csvEnBorrador, mayor, sinMotivo, motivoDeMas, ajena, prematuro, tardia}) {
            assertThat(texto(error)).doesNotContain(DATOS_PERSONALES).doesNotContain("Motivo ficticio");
        }
        // AND los logs de todos esos pedidos tampoco (ni el motivo del rechazo)
        assertThat(salida.getAll()).doesNotContain(DATOS_PERSONALES)
                .doesNotContain("Falta la orden de la práctica", "Motivo ficticio");
    }

    @Test
    @DisplayName("Los listados y las respuestas no guardan caché")
    void sinCache() throws Exception {
        semilla();
        atendido(pacienteUno, consulta, "2026-10-06T10:00");
        long id = generada(obraSocialEjemplo, "2026-10").path("id").asLong();

        MvcResult detalle = get(como("admin"), url(id));
        MvcResult lista = get(como("admin"), LIQUIDACIONES);

        assertThat(detalle.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(lista.getResponse().getHeader("Cache-Control")).contains("no-store");
    }
}
