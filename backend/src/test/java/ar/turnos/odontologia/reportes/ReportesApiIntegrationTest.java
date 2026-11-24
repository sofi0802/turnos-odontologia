package ar.turnos.odontologia.reportes;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import ar.turnos.odontologia.shared.security.Navegador;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Escenarios de la spec {@code reportes} que necesitan la base, la sesión y la seguridad reales (C-32). */
class ReportesApiIntegrationTest extends IntegracionReportesBase {

    @Nested
    class ConsultarLosReportes {

        @Test
        @DisplayName("Reporte de una semana")
        void reporteDeUnaSemana() throws Exception {
            como("admin").get(SEMANA)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.desde").value("2026-12-07"))
                    .andExpect(jsonPath("$.hasta").value("2026-12-13"))
                    .andExpect(jsonPath("$.ocupacion.profesionales").isArray())
                    .andExpect(jsonPath("$.origen.online").value(0))
                    .andExpect(jsonPath("$.ausencias.pacientes.tasa").value(nullValue()))
                    .andExpect(jsonPath("$.cancelaciones.porPaciente").value(0));
        }

        @Test
        @DisplayName("Un turno cuenta por su inicio")
        void unTurnoCuentaPorSuInicio() throws Exception {
            auditar(turno(ana, sillon1, "2026-12-13", "23:30", "23:50", "RESERVADO", false), "CREAR", "recepcion");
            auditar(turno(ana, sillon1, "2026-12-14", "00:10", "00:30", "RESERVADO", false), "CREAR", "recepcion");

            como("admin").get(SEMANA)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.origen.consultorio").value(1))
                    .andExpect(jsonPath("$.ocupacion.profesionales[0].minutosOcupados").value(20));
        }

        @Test
        @DisplayName("Reporte de un profesional")
        void reporteDeUnProfesional() throws Exception {
            auditar(turno(ana, sillon1, "2026-12-08", "09:00", "09:30", "ATENDIDO", false), "CREAR", "recepcion");
            auditar(turno(bruno, sillon2, "2026-12-07", "09:00", "09:30", "AUSENTE", false), "CREAR",
                    "paciente-online:5");

            como("admin").get(SEMANA + "&profesionalId=" + bruno)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.profesionalId").value(bruno))
                    .andExpect(jsonPath("$.ocupacion.profesionales[*].nombre", contains("Bruno Prueba")))
                    .andExpect(jsonPath("$.ocupacion.sillones", empty()))
                    .andExpect(jsonPath("$.origen.online").value(1))
                    .andExpect(jsonPath("$.origen.consultorio").value(0))
                    .andExpect(jsonPath("$.ausencias.pacientes.ausentes").value(1))
                    .andExpect(jsonPath("$.ausencias.pacientes.atendidos").value(0));
        }
    }

    @Nested
    class PeriodoValido {

        @Test
        @DisplayName("Fin anterior al inicio")
        void finAnteriorAlInicio() throws Exception {
            como("admin").get(REPORTES + "?desde=2026-12-13&hasta=2026-12-07")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                    .andExpect(jsonPath("$.errores[0].campo").value("hasta"));
        }

        @Test
        @DisplayName("Período de 93 días")
        void periodoDe93Dias() throws Exception {
            como("admin").get(REPORTES + "?desde=2026-12-01&hasta=2027-03-03")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                    .andExpect(jsonPath("$.errores[0].campo").value("hasta"));
        }

        @Test
        @DisplayName("Período de 92 días")
        void periodoDe92Dias() throws Exception {
            como("admin").get(REPORTES + "?desde=2026-12-01&hasta=2027-03-02").andExpect(status().isOk());
        }

        @Test
        @DisplayName("Falta una fecha")
        void faltaUnaFecha() throws Exception {
            como("admin").get(REPORTES + "?desde=2026-12-07")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("hasta"));
        }

        @Test
        @DisplayName("Una fecha con formato inválido marca su campo")
        void fechaConFormatoInvalido() throws Exception {
            como("admin").get(REPORTES + "?desde=07/12/2026&hasta=2026-12-13")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("desde"));
        }

        @Test
        @DisplayName("Profesional inexistente")
        void profesionalInexistente() throws Exception {
            como("admin").get(SEMANA + "&profesionalId=999999")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("profesionalId"));
        }
    }

    @Nested
    class PermisosDeLosReportes {

        @Test
        @DisplayName("La recepción ve los reportes")
        void laRecepcionVeLosReportes() throws Exception {
            como("recepcion").get(SEMANA).andExpect(status().isOk());
        }

        @Test
        @DisplayName("El odontólogo no ve los reportes")
        void elOdontologoNoVeLosReportes() throws Exception {
            como("ana.ejemplo").get(SEMANA).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("El paciente no ve los reportes")
        void elPacienteNoVeLosReportes() throws Exception {
            como("paciente.uno@example.com").get(SEMANA).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Sin sesión")
        void sinSesion() throws Exception {
            Navegador navegador = navegador();
            navegador.get(SESION);
            navegador.get(SEMANA).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class SinDatosDePacientes {

        @Test
        @DisplayName("La respuesta no identifica pacientes")
        void laRespuestaNoIdentificaPacientes() throws Exception {
            auditar(turno(ana, sillon1, "2026-12-08", "09:00", "09:30", "ATENDIDO", false), "CREAR", "recepcion");
            auditar(turno(ana, sillon2, "2026-12-08", "09:15", "09:45", "RESERVADO", true), "SOBRETURNO", "admin");

            MvcResult resultado = como("admin").get(SEMANA).andExpect(status().isOk()).andReturn();
            String cuerpo = resultado.getResponse().getContentAsString();

            assertThat(cuerpo).doesNotContain("Paciente Uno").doesNotContain("Uno").doesNotContain("99000001")
                    .doesNotContain("pacienteId").doesNotContain("paciente.uno").doesNotContain("Urgencia");
        }
    }

    @Nested
    class LecturaDesdeLaBase {

        @Test
        @DisplayName("La lectura arma ocupación, origen, ausencias y cancelaciones desde la base y la auditoría")
        void lecturaCompleta() throws Exception {
            auditar(turno(ana, sillon1, "2026-12-07", "09:00", "09:30", "ATENDIDO", false), "CREAR", "recepcion");
            long ausente = turno(ana, sillon1, "2026-12-08", "09:00", "09:30", "AUSENTE", false);
            auditar(ausente, "CREAR", "paciente-online:3");
            long cancelado = turno(ana, sillon1, "2026-12-09", "09:00", "09:30", "CANCELADO", false);
            auditar(cancelado, "CREAR", "paciente-online:3");
            auditar(cancelado, "CANCELAR", "paciente-online:3");
            long reprogramado = turno(ana, sillon1, "2026-12-09", "10:00", "10:30", "CANCELADO", false);
            auditar(reprogramado, "CREAR", "paciente-online:3");
            auditar(reprogramado, "REPROGRAMAR", "recepcion");
            auditar(turno(ana, sillon1, "2026-12-10", "10:00", "10:30", "RESERVADO", false), "CREAR", "recepcion");
            turno(ana, sillon3, "2026-12-11", "09:00", "09:30", "RESERVADO", false);
            jdbc.update("INSERT INTO ausencia_profesional(profesional_id, tipo, inicio, fin, registrado_por, registrado_en) "
                            + "VALUES (?, 'LICENCIA', ?, ?, 'admin', now())", bruno,
                    instante("2026-12-04", "00:00").atZone(ZONA).toOffsetDateTime(),
                    instante("2026-12-08", "00:00").atZone(ZONA).toOffsetDateTime());
            jdbc.update("INSERT INTO bloqueo_agenda(profesional_id, inicio, fin, registrado_por, registrado_en) "
                            + "VALUES (?, ?, ?, 'admin', now())", ana,
                    instante("2026-12-10", "11:00").atZone(ZONA).toOffsetDateTime(),
                    instante("2026-12-10", "12:00").atZone(ZONA).toOffsetDateTime());

            como("recepcion").get(SEMANA)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ocupacion.profesionales[*].nombre", contains("Ana Ejemplo", "Bruno Prueba")))
                    .andExpect(jsonPath("$.ocupacion.profesionales[0].minutosOcupados").value(120))
                    .andExpect(jsonPath("$.ocupacion.profesionales[0].minutosAtencion").value(1140))
                    .andExpect(jsonPath("$.ocupacion.profesionales[0].porcentaje").value(11))
                    .andExpect(jsonPath("$.ocupacion.profesionales[1].minutosAtencion").value(0))
                    .andExpect(jsonPath("$.ocupacion.profesionales[1].porcentaje").value(nullValue()))
                    .andExpect(jsonPath("$.ocupacion.sillones", hasSize(3)))
                    .andExpect(jsonPath("$.ocupacion.sillones[2].sillonId").value(sillon3))
                    .andExpect(jsonPath("$.ocupacion.sillones[2].porcentaje").value(nullValue()))
                    .andExpect(jsonPath("$.origen.online").value(2))
                    .andExpect(jsonPath("$.origen.consultorio").value(2))
                    .andExpect(jsonPath("$.origen.sinDato").value(1))
                    .andExpect(jsonPath("$.ausencias.pacientes.ausentes").value(1))
                    .andExpect(jsonPath("$.ausencias.pacientes.atendidos").value(1))
                    .andExpect(jsonPath("$.ausencias.pacientes.tasa").value(50))
                    .andExpect(jsonPath("$.ausencias.pacientes.sinRegistrar").value(2))
                    .andExpect(jsonPath("$.ausencias.profesionales[0].nombre").value("Bruno Prueba"))
                    .andExpect(jsonPath("$.ausencias.profesionales[0].minutos").value(1440))
                    .andExpect(jsonPath("$.cancelaciones.porPaciente").value(1))
                    .andExpect(jsonPath("$.cancelaciones.porReprogramacion").value(1));
        }
    }
}
