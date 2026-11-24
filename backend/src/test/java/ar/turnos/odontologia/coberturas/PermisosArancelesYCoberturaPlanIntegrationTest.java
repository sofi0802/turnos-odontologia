package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Sin borrado y permisos de aranceles y cobertura por plan (C-19, spec aranceles-y-cobertura-planes, tarea 3.4):
 * solo {@code ADMIN} escribe, el personal consulta y el paciente no entra; un {@code 403} no cambia nada.
 */
class PermisosArancelesYCoberturaPlanIntegrationTest extends IntegracionPresupuestosBase {

    @Test
    @DisplayName("No se borra un precio")
    void noSeBorraUnPrecio() throws Exception {
        // GIVEN "Consulta o control" tiene el precio 15000.00
        arancel(consulta, "15000.00");

        // WHEN admin pide DELETE sobre el arancel de "Consulta o control"
        MvcResult r = delete(como("admin"), arancelUrl(consulta));

        // THEN la respuesta no es exitosa y "Consulta o control" sigue con 15000.00
        assertThat(status(r)).isGreaterThanOrEqualTo(400);
        assertThat(precioGuardado(consulta)).isEqualByComparingTo("15000.00");
    }

    @Test
    @DisplayName("No se borra una cobertura")
    void noSeBorraUnaCobertura() throws Exception {
        // GIVEN "Plan Base" cubre N-0101 al 100 %
        semilla();

        // WHEN admin pide DELETE sobre la cobertura de N-0101 de "Plan Base"
        MvcResult r = delete(como("admin"), coberturaPlanUrl(obraSocialEjemplo, planBase) + "/" + n0101);

        // THEN la respuesta no es exitosa y "Plan Base" sigue cubriendo N-0101 al 100 %
        assertThat(status(r)).isGreaterThanOrEqualTo(400);
        assertThat(porcentajeGuardado(planBase, n0101)).isEqualTo(100);
    }

    @Test
    @DisplayName("La recepción no cambia un precio")
    void laRecepcionNoCambiaUnPrecio() throws Exception {
        // GIVEN "Consulta o control" tiene el precio 15000.00
        arancel(consulta, "15000.00");

        // WHEN recepcion carga el precio de "Consulta o control" en 1.00
        MvcResult r = put(como("recepcion"), arancelUrl(consulta), precioSolicitud(new BigDecimal("1.00")));

        // THEN la respuesta es 403 con el código ACCESO_DENEGADO
        assertThat(status(r)).isEqualTo(403);
        assertThat(codigoDeError(r)).isEqualTo("ACCESO_DENEGADO");
        // AND "Consulta o control" sigue con 15000.00
        assertThat(precioGuardado(consulta)).isEqualByComparingTo("15000.00");
    }

    @Test
    @DisplayName("El odontólogo no cambia una cobertura")
    void elOdontologoNoCambiaUnaCobertura() throws Exception {
        // GIVEN "Plan Base" cubre N-0201 al 50 %
        semilla();

        // WHEN ana.ejemplo carga para "Plan Base" el código N-0201 al 100 %
        MvcResult r = put(como("ana.ejemplo"), coberturaPlanUrl(obraSocialEjemplo, planBase) + "/" + n0201,
                porcentajeSolicitud(100));

        // THEN la respuesta es 403 con el código ACCESO_DENEGADO
        assertThat(status(r)).isEqualTo(403);
        assertThat(codigoDeError(r)).isEqualTo("ACCESO_DENEGADO");
        // AND "Plan Base" sigue cubriendo N-0201 al 50 %
        assertThat(porcentajeGuardado(planBase, n0201)).isEqualTo(50);
    }

    @Test
    @DisplayName("El paciente no consulta los aranceles")
    void elPacienteNoConsultaLosAranceles() throws Exception {
        // WHEN paciente.uno lista los aranceles
        MvcResult r = get(como("paciente.uno"), ARANCELES);

        // THEN la respuesta es 403 con el código ACCESO_DENEGADO
        assertThat(status(r)).isEqualTo(403);
        assertThat(codigoDeError(r)).isEqualTo("ACCESO_DENEGADO");
    }

    @Test
    @DisplayName("El paciente no consulta la cobertura de un plan y el personal sí")
    void elPacienteNoConsultaLaCoberturaDeUnPlan() throws Exception {
        semilla();
        MvcResult paciente = get(como("paciente.uno"), coberturaPlanUrl(obraSocialEjemplo, planBase));
        assertThat(status(paciente)).isEqualTo(403);
        assertThat(codigoDeError(paciente)).isEqualTo("ACCESO_DENEGADO");
        assertThat(status(get(como("recepcion"), coberturaPlanUrl(obraSocialEjemplo, planBase)))).isEqualTo(200);
        assertThat(status(get(como("ana.ejemplo"), ARANCELES))).isEqualTo(200);
    }
}
