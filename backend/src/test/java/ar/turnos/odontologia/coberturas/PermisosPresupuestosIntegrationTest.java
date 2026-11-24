package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Permisos sobre los presupuestos (C-19, spec presupuestos, tarea 6.4): solo el odontólogo emite, acepta, rechaza y
 * pide sugerencias; administración y recepción consultan. Un {@code 403} no crea ni cambia nada.
 */
class PermisosPresupuestosIntegrationTest extends IntegracionPresupuestosBase {

    @Test
    @DisplayName("La recepción no emite presupuestos")
    void laRecepcionNoEmite() throws Exception {
        semilla();

        // WHEN recepcion emite un presupuesto para "Paciente Uno" con "Consulta o control"
        MvcResult r = post(como("recepcion"), presupuestosUrl(pacienteUno), presupuestoSolicitud(item(consulta, 1)));

        // THEN la respuesta es 403 con el código ACCESO_DENEGADO
        assertThat(status(r)).isEqualTo(403);
        assertThat(codigoDeError(r)).isEqualTo("ACCESO_DENEGADO");
        // AND "Paciente Uno" no tiene presupuestos
        assertThat(jdbc.queryForObject("SELECT count(*) FROM presupuesto WHERE paciente_id = ?", Integer.class,
                pacienteUno)).isZero();
    }

    @Test
    @DisplayName("El administrador no acepta presupuestos")
    void elAdministradorNoAcepta() throws Exception {
        // GIVEN un presupuesto PENDIENTE de "Paciente Uno"
        semilla();
        long presupuesto = emitirComo("ana.ejemplo", pacienteUno, item(consulta, 1));

        // WHEN admin lo acepta
        MvcResult r = post(como("admin"), presupuestoUrl(pacienteUno, presupuesto) + "/aceptar", null);

        // THEN la respuesta es 403 con el código ACCESO_DENEGADO
        assertThat(status(r)).isEqualTo(403);
        assertThat(codigoDeError(r)).isEqualTo("ACCESO_DENEGADO");
        // AND el presupuesto sigue PENDIENTE
        assertThat(estadoGuardado(presupuesto)).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("La recepción no pide sugerencias")
    void laRecepcionNoPideSugerencias() throws Exception {
        // WHEN recepcion pide las sugerencias de presupuesto de "Paciente Uno"
        MvcResult r = get(como("recepcion"), presupuestosUrl(pacienteUno) + "/sugerencias");

        // THEN la respuesta es 403 con el código ACCESO_DENEGADO
        assertThat(status(r)).isEqualTo(403);
        assertThat(codigoDeError(r)).isEqualTo("ACCESO_DENEGADO");
        assertThat(status(get(como("admin"), presupuestosUrl(pacienteUno) + "/sugerencias"))).isEqualTo(403);
    }

    @Test
    @DisplayName("Ni la recepción ni el administrador rechazan ni emiten")
    void ningunoDeLosDosEscribe() throws Exception {
        semilla();
        long presupuesto = emitirComo("ana.ejemplo", pacienteUno, item(consulta, 1));
        for (String usuario : new String[] {"recepcion", "admin"}) {
            MvcResult rechazo = post(como(usuario), presupuestoUrl(pacienteUno, presupuesto) + "/rechazar", null);
            assertThat(status(rechazo)).isEqualTo(403);
            assertThat(codigoDeError(rechazo)).isEqualTo("ACCESO_DENEGADO");
            assertThat(status(post(como(usuario), presupuestosUrl(pacienteUno),
                    presupuestoSolicitud(item(consulta, 1))))).isEqualTo(403);
        }
        assertThat(estadoGuardado(presupuesto)).isEqualTo("PENDIENTE");
        assertThat(cantidad("presupuesto")).isEqualTo(1);
    }

    @Test
    @DisplayName("Una sesión de paciente no entra a ninguna ruta de presupuestos del personal")
    void elPacienteNoEntraALasRutasDelPersonal() throws Exception {
        semilla();
        long presupuesto = emitirComo("ana.ejemplo", pacienteUno, item(consulta, 1));
        for (String url : new String[] {presupuestosUrl(pacienteUno), presupuestoUrl(pacienteUno, presupuesto),
                presupuestosUrl(pacienteUno) + "/sugerencias"}) {
            MvcResult r = get(como("paciente.uno"), url);
            assertThat(status(r)).isEqualTo(403);
            assertThat(codigoDeError(r)).isEqualTo("ACCESO_DENEGADO");
        }
        MvcResult emitir = post(como("paciente.uno"), presupuestosUrl(pacienteUno), presupuestoSolicitud(item(consulta, 1)));
        assertThat(status(emitir)).isEqualTo(403);
        assertThat(cantidad("presupuesto")).isEqualTo(1);
    }

    @Test
    @DisplayName("Un odontólogo y el personal consultan los presupuestos")
    void elPersonalConsulta() throws Exception {
        semilla();
        long presupuesto = emitirComo("ana.ejemplo", pacienteUno, item(consulta, 1));
        for (String usuario : new String[] {"admin", "recepcion", "ana.ejemplo", "bruno.prueba"}) {
            assertThat(status(get(como(usuario), presupuestosUrl(pacienteUno)))).isEqualTo(200);
            assertThat(status(get(como(usuario), presupuestoUrl(pacienteUno, presupuesto)))).isEqualTo(200);
        }
    }
}
