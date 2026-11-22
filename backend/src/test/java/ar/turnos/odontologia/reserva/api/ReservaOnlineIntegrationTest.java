package ar.turnos.odontologia.reserva.api;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.reserva.IntegracionReservaBase;
import ar.turnos.odontologia.shared.security.Navegador;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Un test por escenario de {@code specs/reserva-online} (las carreras están en {@code CarrerasReservaIntegrationTest}). */
@Tag("integration")
class ReservaOnlineIntegrationTest extends IntegracionReservaBase {

    static final String RESERVAS = "/api/v1/paciente/reservas";

    @BeforeEach
    void franjasDelSpec() {
        // spec reserva-online: la Dra. A trabaja el martes de 08:00 a 13:00 y el Dr. B de 10:00 a 18:00
        martes(draA, "08:00", "13:00");
        martes(drB, "10:00", "18:00");
    }

    private Map<String, Object> pedido(long prestacion, String inicio, Long profesional) {
        Map<String, Object> pedido = new LinkedHashMap<>();
        if (prestacion > 0) {
            pedido.put("prestacionId", prestacion);
        }
        if (inicio != null) {
            pedido.put("inicio", inicio);
        }
        if (profesional != null) {
            pedido.put("profesionalId", profesional);
        }
        return pedido;
    }

    private MvcResult reservar(Navegador navegador, Map<String, Object> pedido) throws Exception {
        return navegador.post(RESERVAS, cuerpoJson(pedido)).andReturn();
    }

    private MvcResult reservarComoUno(long prestacion, String inicio, Long profesional) throws Exception {
        return reservar(comoPacienteUno(), pedido(prestacion, inicio, profesional));
    }

    private void esperar201ConProfesional(MvcResult resultado, long profesional) throws Exception {
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(201);
        assertThat(cuerpo(resultado).get("profesional").get("id").asLong()).isEqualTo(profesional);
    }

    private void esperarRechazo(MvcResult resultado, int estado, String codigo) throws Exception {
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(estado);
        assertThat(cuerpo(resultado).get("codigo").asText()).isEqualTo(codigo);
    }

    private long sillonDelTurnoDe(long paciente) {
        return id("SELECT sillon_id FROM turno WHERE paciente_id = ? AND estado <> 'CANCELADO'", paciente);
    }

    @Nested
    @DisplayName("Reservar un turno online")
    class Reservar {

        @Test
        @DisplayName("Reserva con profesional elegido")
        void reservaConProfesionalElegido() throws Exception {
            MvcResult resultado = reservarComoUno(consulta, hora(10, 0), draA);

            assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(201);
            JsonNode reserva = cuerpo(resultado);
            assertThat(reserva.get("estado").asText()).isEqualTo("RESERVADO");
            assertThat(reserva.get("inicio").asText()).isEqualTo("2026-12-01T10:00:00-03:00");
            assertThat(reserva.get("fin").asText()).isEqualTo("2026-12-01T10:30:00-03:00");
            assertThat(reserva.get("profesional").get("id").asLong()).isEqualTo(draA);
            assertThat(reserva.get("profesional").get("nombre").asText()).isEqualTo("Ana Ejemplo");
            assertThat(reserva.get("prestacion").get("nombre").asText()).isEqualTo("Consulta");
            assertThat(reserva.get("numeroReserva").asText()).matches("[A-HJKMNP-Z2-9]{4}-[A-HJKMNP-Z2-9]{4}");
            assertThat(texto(resultado)).doesNotContain("sillon");
            assertThat(id("SELECT count(*) FROM turno WHERE paciente_id = ? AND profesional_id = ? "
                    + "AND estado = 'RESERVADO' AND inicio = ?::timestamptz", pacienteUno, draA,
                    "2026-12-01T10:00:00-03:00")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("El paciente de la reserva es el de la sesión")
    class PacienteDeLaSesion {

        @Test
        @DisplayName("Intento de reservar a nombre de otro paciente")
        void reservarANombreDeOtro() throws Exception {
            Map<String, Object> pedido = pedido(consulta, hora(10, 0), draA);
            pedido.put("pacienteId", pacienteDos);

            MvcResult resultado = reservar(comoPacienteUno(), pedido);

            assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(201);
            assertThat(contarTurnosDe(pacienteUno)).isEqualTo(1);
            assertThat(contarTurnosDe(pacienteDos)).isZero();
        }
    }

    @Nested
    @DisplayName("Asignación del profesional sin preferencia")
    class AsignacionDelProfesional {

        @Test
        @DisplayName("Sin preferencia con dos profesionales libres")
        void sinPreferenciaConDosLibres() throws Exception {
            turnoActivo(pacienteDos, draA, sillon1, 1, 8, 0);
            turnoActivo(pacienteDos, draA, sillon1, 1, 9, 0);
            turnoActivo(pacienteDos, drB, sillon2, 1, 14, 0);

            esperar201ConProfesional(reservarComoUno(consulta, hora(11, 0), null), drB);
        }

        @Test
        @DisplayName("Empate resuelto al azar")
        void empateResueltoAlAzar() throws Exception {
            turnoActivo(pacienteDos, draA, sillon1, 1, 8, 0);
            turnoActivo(pacienteDos, drB, sillon2, 1, 14, 0);

            desempate.ultimo();
            esperar201ConProfesional(reservarComoUno(consulta, hora(11, 0), null), drB);

            // la misma reserva sobre una agenda igual: se cancela la anterior y el desempate elige el primero
            jdbc.update("UPDATE turno SET estado = 'CANCELADO' WHERE paciente_id = ?", pacienteUno);
            desempate.primero();
            esperar201ConProfesional(reservarComoUno(consulta, hora(11, 0), null), draA);
        }

        @Test
        @DisplayName("Profesional no habilitado excluido de la asignación")
        void noHabilitadoExcluido() throws Exception {
            turnoActivo(pacienteDos, drB, sillon2, 1, 14, 0);
            turnoActivo(pacienteDos, drB, sillon2, 1, 15, 0);
            turnoActivo(pacienteDos, drB, sillon2, 1, 16, 0);

            esperar201ConProfesional(reservarComoUno(conducto, hora(11, 0), null), drB);
        }

        @Test
        @DisplayName("Solo se asigna un profesional libre")
        void soloUnProfesionalLibre() throws Exception {
            turnoActivo(pacienteDos, draA, sillon1, 1, 11, 0);
            turnoActivo(pacienteDos, drB, sillon2, 1, 14, 0);
            turnoActivo(pacienteDos, drB, sillon2, 1, 15, 0);
            turnoActivo(pacienteDos, drB, sillon2, 1, 16, 0);

            esperar201ConProfesional(reservarComoUno(consulta, hora(11, 0), null), drB);
        }

        @Test
        @DisplayName("Los turnos cancelados no cuentan")
        void canceladosNoCuentan() throws Exception {
            turnoCancelado(pacienteDos, draA, sillon1, 1, 8, 0);
            turnoCancelado(pacienteDos, draA, sillon1, 1, 9, 0);
            turnoCancelado(pacienteDos, draA, sillon1, 1, 12, 0);
            turnoActivo(pacienteDos, drB, sillon2, 1, 14, 0);

            esperar201ConProfesional(reservarComoUno(consulta, hora(11, 0), null), draA);
        }
    }

    @Nested
    @DisplayName("Asignación del sillón")
    class AsignacionDelSillon {

        @Test
        @DisplayName("Sillón habitual libre")
        void habitualLibre() throws Exception {
            MvcResult resultado = reservarComoUno(consulta, hora(10, 0), drB);

            assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
            assertThat(sillonDelTurnoDe(pacienteUno)).isEqualTo(sillon2);
        }

        @Test
        @DisplayName("Sillón habitual ocupado")
        void habitualOcupado() throws Exception {
            turnoActivo(pacienteDos, drB, sillon1, 1, 10, 0);

            MvcResult resultado = reservarComoUno(consulta, hora(10, 0), draA);

            assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
            assertThat(sillonDelTurnoDe(pacienteUno)).isEqualTo(sillon2);
        }

        @Test
        @DisplayName("Profesional sin sillón habitual")
        void sinSillonHabitual() throws Exception {
            jdbc.update("UPDATE profesional SET sillon_habitual_id = NULL WHERE id = ?", drB);

            MvcResult resultado = reservarComoUno(consulta, hora(10, 0), drB);

            assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
            assertThat(sillonDelTurnoDe(pacienteUno)).isEqualTo(sillon1);
        }

        @Test
        @DisplayName("Sillón enviado en el pedido")
        void sillonEnviadoEnElPedido() throws Exception {
            Map<String, Object> pedido = pedido(consulta, hora(10, 0), draA);
            pedido.put("sillonId", sillon2);

            MvcResult resultado = reservar(comoPacienteUno(), pedido);

            assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
            assertThat(sillonDelTurnoDe(pacienteUno)).isEqualTo(sillon1);
        }
    }

    @Nested
    @DisplayName("Horario no disponible")
    class HorarioNoDisponible {

        @Test
        @DisplayName("Profesional elegido ocupado")
        void profesionalElegidoOcupado() throws Exception {
            turnoActivo(pacienteDos, draA, sillon1, 1, 10, 0);

            MvcResult resultado = reservarComoUno(consulta, hora(10, 15), draA);

            esperarRechazo(resultado, 409, "SIN_DISPONIBILIDAD");
            assertThat(inicios(cuerpo(resultado).get("alternativas"))).containsExactly("10:45", "11:00", "11:15");
            assertThat(contarTurnosDe(pacienteUno)).isZero();
        }

        @Test
        @DisplayName("Horario fuera de la franja de trabajo")
        void fueraDeLaFranja() throws Exception {
            esperarRechazo(reservarComoUno(consulta, hora(13, 0), draA), 409, "SIN_DISPONIBILIDAD");
            assertThat(contarTurnos()).isZero();
        }

        @Test
        @DisplayName("Profesional con ausencia")
        void profesionalConAusencia() throws Exception {
            ausencia(draA, "AUSENCIA", 1, 9, 0, 12, 0, "Motivo administrativo ficticio");

            MvcResult resultado = reservarComoUno(consulta, hora(10, 0), draA);

            esperarRechazo(resultado, 409, "SIN_DISPONIBILIDAD");
            assertThat(texto(resultado)).doesNotContain("Motivo administrativo ficticio")
                    .doesNotContain("PROFESIONAL_AUSENTE");
        }

        @Test
        @DisplayName("Inicio fuera de la grilla")
        void inicioFueraDeLaGrilla() throws Exception {
            esperarRechazo(reservarComoUno(consulta, hora(10, 5), draA), 409, "SIN_DISPONIBILIDAD");
        }

        @Test
        @DisplayName("Ningún profesional libre sin preferencia")
        void ningunProfesionalLibre() throws Exception {
            turnoActivo(pacienteDos, draA, sillon1, 1, 11, 0);
            turnoActivo(pacienteDos, drB, sillon2, 1, 11, 0);

            esperarRechazo(reservarComoUno(consulta, hora(11, 0), null), 409, "SIN_DISPONIBILIDAD");
            assertThat(contarTurnos()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Rechazos de negocio de la reserva")
    class RechazosDeNegocio {

        @Test
        @DisplayName("Reserva en el pasado")
        void reservaEnElPasado() throws Exception {
            reloj.fijarEn(1, 10, 20);

            esperarRechazo(reservarComoUno(consulta, hora(10, 0), draA), 409, "INICIO_EN_EL_PASADO");
        }

        @Test
        @DisplayName("Profesional elegido no habilitado")
        void profesionalNoHabilitado() throws Exception {
            esperarRechazo(reservarComoUno(conducto, hora(10, 0), draA), 409, "PROFESIONAL_NO_HABILITADO");
            assertThat(contarTurnos()).isZero();
        }

        @Test
        @DisplayName("Prestación inactiva")
        void prestacionInactiva() throws Exception {
            jdbc.update("UPDATE prestacion SET activo = FALSE WHERE id = ?", consulta);

            esperarRechazo(reservarComoUno(consulta, hora(10, 0), draA), 409, "RECURSO_INACTIVO");
        }
    }

    @Nested
    @DisplayName("Validación de la reserva")
    class Validacion {

        @Test
        @DisplayName("Falta el inicio")
        void faltaElInicio() throws Exception {
            MvcResult resultado = reservarComoUno(consulta, null, draA);

            esperarRechazo(resultado, 422, "DATOS_INVALIDOS");
            assertThat(camposConError(cuerpo(resultado))).contains("inicio");
            assertThat(contarTurnos()).isZero();
        }

        @Test
        @DisplayName("Inicio sin zona horaria")
        void inicioSinZonaHoraria() throws Exception {
            MvcResult resultado = reservarComoUno(consulta, "2026-12-01T10:00:00", draA);

            esperarRechazo(resultado, 422, "DATOS_INVALIDOS");
            assertThat(camposConError(cuerpo(resultado))).contains("inicio");
        }

        @Test
        @DisplayName("Profesional inexistente")
        void profesionalInexistente() throws Exception {
            MvcResult resultado = reservarComoUno(consulta, hora(10, 0), 999_999L);

            esperarRechazo(resultado, 422, "DATOS_INVALIDOS");
            assertThat(camposConError(cuerpo(resultado))).contains("profesionalId");
        }

        @Test
        @DisplayName("Falta la prestación y prestación inexistente")
        void prestacionFaltanteOInexistente() throws Exception {
            MvcResult falta = reservarComoUno(0, hora(10, 0), draA);
            esperarRechazo(falta, 422, "DATOS_INVALIDOS");
            assertThat(camposConError(cuerpo(falta))).contains("prestacionId");

            MvcResult inexistente = reservarComoUno(999_999L, hora(10, 0), draA);
            esperarRechazo(inexistente, 422, "DATOS_INVALIDOS");
            assertThat(camposConError(cuerpo(inexistente))).contains("prestacionId");
        }
    }

    @Nested
    @DisplayName("Solo el paciente con sesión reserva")
    class SoloElPaciente {

        @Test
        @DisplayName("Reserva sin sesión")
        void reservaSinSesion() throws Exception {
            MvcResult resultado = reservar(sinSesion(), pedido(consulta, hora(10, 0), draA));

            esperarRechazo(resultado, 401, "NO_AUTENTICADO");
            assertThat(contarTurnos()).isZero();
        }

        @Test
        @DisplayName("Reserva pedida por el personal")
        void reservaPedidaPorElPersonal() throws Exception {
            MvcResult resultado = reservar(comoRecepcion(), pedido(consulta, hora(10, 0), draA));

            esperarRechazo(resultado, 403, "ACCESO_DENEGADO");
            assertThat(contarTurnos()).isZero();
        }

        @Test
        @DisplayName("Reserva sin token CSRF")
        void reservaSinTokenCsrf() throws Exception {
            Navegador navegador = comoPacienteUno();

            MvcResult resultado = navegador.postSinEncabezadoCsrf(RESERVAS,
                    cuerpoJson(pedido(consulta, hora(10, 0), draA))).andReturn();

            esperarRechazo(resultado, 403, "CSRF_INVALIDO");
            assertThat(contarTurnos()).isZero();
        }
    }
}
