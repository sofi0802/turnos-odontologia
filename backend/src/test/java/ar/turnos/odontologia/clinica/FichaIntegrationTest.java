package ar.turnos.odontologia.clinica;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;

/** Un test por escenario de la ficha básica del spec {@code ficha-clinica} (C-13). */
@ExtendWith(OutputCaptureExtension.class)
class FichaIntegrationTest extends IntegracionClinicaBase {

    private static final String LARGO = "x".repeat(4001);

    @Test
    @DisplayName("Registrar la ficha por primera vez")
    void registrarLaFichaPorPrimeraVez() throws Exception {
        MvcResult resultado = guardarFicha(como("ana.ejemplo"), pacienteUno, "Hipertensión", "Penicilina", "Enalapril");

        assertThat(status(resultado)).isEqualTo(200);
        JsonNode ficha = cuerpo(como("ana.ejemplo").get(ficha(pacienteUno)).andReturn());
        assertThat(ficha.path("antecedentes").asText()).isEqualTo("Hipertensión");
        assertThat(ficha.path("alergias").asText()).isEqualTo("Penicilina");
        assertThat(ficha.path("medicacion").asText()).isEqualTo("Enalapril");
        assertThat(ficha.path("actualizadoPor").asText()).isEqualTo("ana.ejemplo");
        assertThat(ficha.path("actualizadoEn").asText()).isEqualTo(iso(diciembre(1, 8, 0)));
    }

    @Test
    @DisplayName("Actualizar la ficha reemplaza el contenido")
    void actualizarLaFichaReemplazaElContenido() throws Exception {
        guardarFicha(como("ana.ejemplo"), pacienteUno, null, "Penicilina", null);

        guardarFicha(como("bruno.prueba"), pacienteUno, null, "Penicilina y látex", null);

        JsonNode ficha = cuerpo(como("ana.ejemplo").get(ficha(pacienteUno)).andReturn());
        assertThat(ficha.path("alergias").asText()).isEqualTo("Penicilina y látex");
        assertThat(ficha.path("actualizadoPor").asText()).isEqualTo("bruno.prueba");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ficha_clinica", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Ficha con un texto demasiado largo")
    void fichaConUnTextoDemasiadoLargo() throws Exception {
        guardarFicha(como("ana.ejemplo"), pacienteUno, "Hipertensión", null, null);

        MvcResult resultado = guardarFicha(como("ana.ejemplo"), pacienteUno, LARGO, null, null);

        assertThat(status(resultado)).isEqualTo(422);
        assertThat(codigo(resultado)).isEqualTo("DATOS_INVALIDOS");
        assertThat(jdbc.queryForObject("SELECT antecedentes FROM ficha_clinica WHERE paciente_id = ?", String.class,
                pacienteUno)).isEqualTo("Hipertensión");
    }

    @Test
    @DisplayName("Paciente sin ficha")
    void pacienteSinFicha() throws Exception {
        MvcResult resultado = como("ana.ejemplo").get(ficha(pacienteUno)).andReturn();

        assertThat(status(resultado)).isEqualTo(200);
        JsonNode ficha = cuerpo(resultado);
        assertThat(ficha.path("antecedentes").asText()).isEmpty();
        assertThat(ficha.path("alergias").asText()).isEmpty();
        assertThat(ficha.path("medicacion").asText()).isEmpty();
        assertThat(ficha.path("actualizadoPor").isNull()).isTrue();
    }

    @Test
    @DisplayName("Ficha de un paciente inexistente")
    void fichaDeUnPacienteInexistente() throws Exception {
        MvcResult resultado = como("ana.ejemplo").get(ficha(999999)).andReturn();

        assertThat(status(resultado)).isEqualTo(404);
        assertThat(codigo(resultado)).isEqualTo("RECURSO_NO_ENCONTRADO");
    }

    @Test
    @DisplayName("El administrador lee la ficha")
    void elAdministradorLeeLaFicha() throws Exception {
        guardarFicha(como("ana.ejemplo"), pacienteUno, null, "Penicilina", null);

        MvcResult resultado = como("admin").get(ficha(pacienteUno)).andReturn();

        assertThat(status(resultado)).isEqualTo(200);
        assertThat(cuerpo(resultado).path("alergias").asText()).isEqualTo("Penicilina");
        // y no la puede modificar
        assertThat(status(guardarFicha(como("admin"), pacienteUno, null, "Otra", null))).isEqualTo(403);
    }

    @Test
    @DisplayName("La recepción consulta la ficha")
    void laRecepcionConsultaLaFicha() throws Exception {
        guardarFicha(como("ana.ejemplo"), pacienteUno, null, "Penicilina", null);

        MvcResult resultado = como("recepcion").get(ficha(pacienteUno)).andReturn();

        assertThat(status(resultado)).isEqualTo(403);
        assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
        assertThat(texto(resultado)).doesNotContain("Penicilina");
    }

    @Test
    @DisplayName("Un error de validación no repite el contenido")
    void unErrorDeValidacionNoRepiteElContenido(CapturedOutput salida) throws Exception {
        MvcResult resultado = guardarFicha(como("ana.ejemplo"), pacienteUno, LARGO, "Penicilina", null);

        assertThat(status(resultado)).isEqualTo(422);
        assertThat(codigo(resultado)).isEqualTo("DATOS_INVALIDOS");
        assertThat(texto(resultado)).doesNotContain("Penicilina", "xxxx");
        // tampoco en los logs (regla dura 6)
        assertThat(salida.getAll()).doesNotContain("Penicilina", "xxxxxxxxxx");
    }

    @Test
    @DisplayName("La ficha muestra la ausencia")
    void laFichaMuestraLaAusencia() throws Exception {
        turno(pacienteUno, draA, diciembre(1, 10, 0), "AUSENTE");
        turno(pacienteUno, draA, diciembre(1, 11, 0), "CANCELADO");

        JsonNode ausencias = cuerpo(como("ana.ejemplo").get(ficha(pacienteUno)).andReturn()).path("ausencias");

        assertThat(ausencias).hasSize(1);
        assertThat(ausencias.get(0).path("inicio").asText()).isEqualTo(iso(diciembre(1, 10, 0)));
        assertThat(ausencias.get(0).path("profesionalId").asLong()).isEqualTo(draA);
        assertThat(ausencias.get(0).path("profesional").asText()).isEqualTo("Ana Ejemplo");
    }
}
