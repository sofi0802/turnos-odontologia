package ar.turnos.odontologia.pagos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Escenarios de "Configurar las señas" y "Permisos de las señas" (spec {@code senas}). */
class ConfiguracionSenasIntegrationTest extends IntegracionPagosBase {

    private static final String CONFIGURACION = "/api/v1/senas/configuracion";

    @Nested
    class ConfigurarLasSenas {

        @Test
        @DisplayName("Configuración inicial")
        void configuracionInicial() throws Exception {
            como("admin").get(CONFIGURACION)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activas").value(false))
                    .andExpect(jsonPath("$.porcentaje").value(30))
                    .andExpect(jsonPath("$.plazoMinutos").value(30));
        }

        @Test
        @DisplayName("Activar las señas")
        void activarLasSenas() throws Exception {
            var admin = como("admin");
            put(admin, CONFIGURACION, cuerpoJson(Map.of("activas", true, "porcentaje", 25, "plazoMinutos", 45)))
                    .andExpect(status().isOk());
            admin.get(CONFIGURACION)
                    .andExpect(jsonPath("$.activas").value(true))
                    .andExpect(jsonPath("$.porcentaje").value(25))
                    .andExpect(jsonPath("$.plazoMinutos").value(45));
        }

        @Test
        @DisplayName("Porcentaje fuera de rango")
        void porcentajeFueraDeRango() throws Exception {
            put(como("admin"), CONFIGURACION, cuerpoJson(Map.of("activas", true, "porcentaje", 0, "plazoMinutos", 30)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                    .andExpect(jsonPath("$.errores[0].campo").value("porcentaje"));
        }

        @Test
        @DisplayName("Un cambio no toca las señas creadas")
        void unCambioNoTocaLasSenasCreadas() throws Exception {
            long turno = turno(pacienteDos, consulta, "RESERVADO");
            long sena = senaDirecta(turno, pacienteDos, "4500.00", "PENDIENTE", instante("2026-12-07", "12:30"));
            put(como("admin"), CONFIGURACION, cuerpoJson(Map.of("activas", true, "porcentaje", 50, "plazoMinutos", 30)))
                    .andExpect(status().isOk());
            assertThat(jdbc.queryForObject("SELECT importe FROM sena WHERE id = ?", BigDecimal.class, sena))
                    .isEqualByComparingTo("4500.00");
        }
    }

    @Nested
    class PermisosDeLasSenas {

        @Test
        @DisplayName("La recepción no configura las señas")
        void laRecepcionNoConfiguraLasSenas() throws Exception {
            como("recepcion").get(CONFIGURACION).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("El paciente no configura las señas")
        void elPacienteNoConfiguraLasSenas() throws Exception {
            put(iniciarSesion("paciente.uno@example.com", clave), CONFIGURACION,
                    cuerpoJson(Map.of("activas", true, "porcentaje", 30, "plazoMinutos", 30)))
                    .andExpect(status().isForbidden());
        }
    }
}
