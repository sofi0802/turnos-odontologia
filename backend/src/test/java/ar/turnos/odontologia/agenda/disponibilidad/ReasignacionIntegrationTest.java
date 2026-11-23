package ar.turnos.odontologia.agenda.disponibilidad;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.agenda.disponibilidad.application.AvisoReasignacion;
import ar.turnos.odontologia.shared.security.Navegador;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Un test por escenario de reasignación y aviso del spec {@code disponibilidad-profesional} (C-07, design D10).
 * El aviso es un doble que registra los pedidos y puede fallar. Es {@code @Primary} para reemplazar al adaptador de
 * {@code notificaciones}, que tiene su propio test de punta a punta.
 */
@Import(ReasignacionIntegrationTest.ConAvisoDoble.class)
class ReasignacionIntegrationTest extends IntegracionDisponibilidadBase {

    @Autowired
    private AvisoDoble aviso;

    private long licencia;
    private long turno;

    @BeforeEach
    void prepararTurnoAfectado() throws Exception {
        aviso.reiniciar();
        cargarDeLunesAViernes(draA, "08:00", "13:00");
        cargarDeLunesAViernes(drB, "08:00", "18:00");
        habilitar(drB, consulta);
        // GIVEN un turno activo de "Paciente Uno" con la Dra. Ana Ejemplo el 2026-12-09 de 10:00 a 10:30 en el
        // Sillón 1, dado antes de registrar su licencia del 2026-12-07 al 2026-12-12
        turno = turnoCreado(pacienteUno, draA, sillon1, diciembre(9, 10, 0)).path("id").asLong();
        licencia = idCreado(registrarAusencia(como("recepcion"), draA, "LICENCIA",
                diciembre(7, 0, 0), diciembre(12, 0, 0)));
    }

    @Test
    @DisplayName("Reasignar a otro profesional en el mismo horario")
    void reasignarAOtroProfesionalEnElMismoHorario() throws Exception {
        String numeroOriginal = jdbc.queryForObject("SELECT codigo_reserva FROM turno WHERE id = ?", String.class, turno);

        MvcResult resultado = reasignar(como("recepcion"), draA, licencia, turno, destino(drB, null));

        assertThat(status(resultado)).isEqualTo(200);
        assertThat(estadoEnLaBase(turno)).isEqualTo("CANCELADO");
        JsonNode nuevo = cuerpo(resultado);
        assertThat(nuevo.path("profesionalId").asLong()).isEqualTo(drB);
        assertThat(nuevo.path("sillonId").asLong()).isEqualTo(sillon1);
        assertThat(nuevo.path("inicio").asText()).isEqualTo(diciembre(9, 10, 0));
        assertThat(nuevo.path("fin").asText()).isEqualTo(diciembre(9, 10, 30));
        assertThat(nuevo.path("numeroReserva").asText()).isNotEqualTo(numeroOriginal);
        assertThat(pacienteDe(nuevo.path("turnoId").asLong())).isEqualTo(pacienteUno);
        assertThat(estadoEnLaBase(nuevo.path("turnoId").asLong())).isEqualTo("RESERVADO");
    }

    @Test
    @DisplayName("Reprogramar con el mismo profesional después de la licencia")
    void reprogramarConElMismoProfesionalDespuesDeLaLicencia() throws Exception {
        MvcResult resultado = reasignar(como("recepcion"), draA, licencia, turno, destino(null, diciembre(14, 10, 0)));

        assertThat(status(resultado)).isEqualTo(200);
        JsonNode nuevo = cuerpo(resultado);
        assertThat(nuevo.path("profesionalId").asLong()).isEqualTo(draA);
        assertThat(nuevo.path("inicio").asText()).isEqualTo(diciembre(14, 10, 0));
        assertThat(pacienteDe(nuevo.path("turnoId").asLong())).isEqualTo(pacienteUno);
        assertThat(estadoEnLaBase(turno)).isEqualTo("CANCELADO");
    }

    @Test
    @DisplayName("Profesional no habilitado para la prestación")
    void profesionalNoHabilitadoParaLaPrestacion() throws Exception {
        // GIVEN un turno de "Tratamiento de conducto" del Dr. Bruno Prueba afectado por una licencia suya, y la
        // Dra. Ana Ejemplo no habilitada para esa prestación
        long conducto = jdbc.queryForObject("INSERT INTO prestacion(nombre, duracion_minutos) "
                + "VALUES ('Tratamiento de conducto', 60) RETURNING id", Long.class);
        habilitar(drB, conducto);
        long turnoConducto = turnoDePrestacion(drB, conducto, diciembre(16, 14, 0));
        long licenciaB = idCreado(registrarAusencia(como("recepcion"), drB, "LICENCIA",
                diciembre(16, 0, 0), diciembre(17, 0, 0)));
        aviso.reiniciar();

        MvcResult resultado = reasignar(como("recepcion"), drB, licenciaB, turnoConducto, destino(draA, null));

        assertThat(status(resultado)).isEqualTo(409);
        assertThat(codigo(resultado)).isEqualTo("PROFESIONAL_NO_HABILITADO");
        assertThat(estadoEnLaBase(turnoConducto)).isEqualTo("RESERVADO");
        assertThat(aviso.pedidos()).isEmpty();
    }

    @Test
    @DisplayName("El nuevo horario también cae en la licencia")
    void elNuevoHorarioTambienCaeEnLaLicencia() throws Exception {
        MvcResult resultado = reasignar(como("recepcion"), draA, licencia, turno, destino(null, diciembre(10, 10, 0)));

        assertThat(status(resultado)).isEqualTo(409);
        assertThat(codigo(resultado)).isEqualTo("PROFESIONAL_AUSENTE");
        assertThat(estadoEnLaBase(turno)).isEqualTo("RESERVADO");
        assertThat(aviso.pedidos()).isEmpty();
    }

    @Test
    @DisplayName("Turno que no está afectado por la ausencia")
    void turnoQueNoEstaAfectadoPorLaAusencia() throws Exception {
        long noAfectado = turnoCreado(pacienteDos, draA, sillon1, diciembre(15, 10, 0)).path("id").asLong();

        MvcResult resultado = reasignar(como("recepcion"), draA, licencia, noAfectado, destino(drB, null));

        assertThat(status(resultado)).isEqualTo(409);
        assertThat(codigo(resultado)).isEqualTo("TURNO_NO_AFECTADO");
        assertThat(estadoEnLaBase(noAfectado)).isEqualTo("RESERVADO");
    }

    @Test
    @DisplayName("El odontólogo intenta reasignar")
    void elOdontologoIntentaReasignar() throws Exception {
        MvcResult resultado = reasignar(como("ana.ejemplo"), draA, licencia, turno, destino(drB, null));

        assertThat(status(resultado)).isEqualTo(403);
        assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
        assertThat(estadoEnLaBase(turno)).isEqualTo("RESERVADO");
    }

    @Test
    @DisplayName("Se pide el aviso para el turno nuevo")
    void sePideElAvisoParaElTurnoNuevo() throws Exception {
        MvcResult resultado = reasignar(como("recepcion"), draA, licencia, turno, destino(drB, null));

        assertThat(aviso.pedidos()).containsExactly(cuerpo(resultado).path("turnoId").asLong());
    }

    @Test
    @DisplayName("Un aviso que falla no deshace la reasignación")
    void unAvisoQueFallaNoDeshaceLaReasignacion() throws Exception {
        aviso.fallarDesdeAhora();

        MvcResult resultado = reasignar(como("recepcion"), draA, licencia, turno, destino(drB, null));

        assertThat(status(resultado)).isEqualTo(200);
        assertThat(estadoEnLaBase(cuerpo(resultado).path("turnoId").asLong())).isEqualTo("RESERVADO");
        assertThat(estadoEnLaBase(turno)).isEqualTo("CANCELADO");
    }

    @Nested
    @DisplayName("Reasignación por un bloqueo de agenda (RN-AG-19, design D16)")
    class PorBloqueo {

        private long bloqueo;

        @BeforeEach
        void prepararBloqueo() throws Exception {
            // GIVEN un bloqueo de la Dra. Ana Ejemplo el 2026-12-09 de 09:00 a 12:00 (sin la licencia de la base,
            // para que el único motivo de rechazo posible sea el bloqueo)
            jdbc.update("DELETE FROM ausencia_profesional");
            bloqueo = idCreado(bloquear(como("admin"), draA, diciembre(9, 9, 0), diciembre(9, 12, 0)));
            aviso.reiniciar();
        }

        @Test
        @DisplayName("Reasignar a otro profesional el turno de un bloqueo")
        void reasignarAOtroProfesionalElTurnoDeUnBloqueo() throws Exception {
            String numeroOriginal = jdbc.queryForObject("SELECT codigo_reserva FROM turno WHERE id = ?",
                    String.class, turno);

            MvcResult resultado = reasignarPorBloqueo(como("recepcion"), draA, bloqueo, turno, destino(drB, null));

            assertThat(status(resultado)).isEqualTo(200);
            assertThat(estadoEnLaBase(turno)).isEqualTo("CANCELADO");
            JsonNode nuevo = cuerpo(resultado);
            assertThat(nuevo.path("profesionalId").asLong()).isEqualTo(drB);
            assertThat(nuevo.path("inicio").asText()).isEqualTo(diciembre(9, 10, 0));
            assertThat(nuevo.path("fin").asText()).isEqualTo(diciembre(9, 10, 30));
            assertThat(nuevo.path("numeroReserva").asText()).isNotEqualTo(numeroOriginal);
            assertThat(pacienteDe(nuevo.path("turnoId").asLong())).isEqualTo(pacienteUno);
            assertThat(aviso.pedidos()).containsExactly(nuevo.path("turnoId").asLong());
        }

        @Test
        @DisplayName("Turno que no está afectado por el bloqueo")
        void turnoQueNoEstaAfectadoPorElBloqueo() throws Exception {
            long noAfectado = turnoCreado(pacienteDos, draA, sillon1, diciembre(9, 12, 30)).path("id").asLong();

            MvcResult resultado = reasignarPorBloqueo(como("recepcion"), draA, bloqueo, noAfectado,
                    destino(drB, null));

            assertThat(status(resultado)).isEqualTo(409);
            assertThat(codigo(resultado)).isEqualTo("TURNO_NO_AFECTADO");
            assertThat(estadoEnLaBase(noAfectado)).isEqualTo("RESERVADO");
        }

        @Test
        @DisplayName("El nuevo horario también cae en el bloqueo")
        void elNuevoHorarioTambienCaeEnElBloqueo() throws Exception {
            MvcResult resultado = reasignarPorBloqueo(como("admin"), draA, bloqueo, turno,
                    destino(null, diciembre(9, 11, 0)));

            assertThat(status(resultado)).isEqualTo(409);
            assertThat(codigo(resultado)).isEqualTo("AGENDA_BLOQUEADA");
            assertThat(estadoEnLaBase(turno)).isEqualTo("RESERVADO");
            assertThat(aviso.pedidos()).isEmpty();
        }

        @Test
        @DisplayName("Bloqueo inexistente")
        void bloqueoInexistente() throws Exception {
            MvcResult resultado = reasignarPorBloqueo(como("recepcion"), draA, 9999L, turno, destino(drB, null));

            assertThat(status(resultado)).isEqualTo(404);
            assertThat(codigo(resultado)).isEqualTo("RECURSO_NO_ENCONTRADO");
            assertThat(estadoEnLaBase(turno)).isEqualTo("RESERVADO");
        }

        @Test
        @DisplayName("El odontólogo intenta reasignar el turno de su bloqueo")
        void elOdontologoIntentaReasignarElTurnoDeSuBloqueo() throws Exception {
            MvcResult resultado = reasignarPorBloqueo(como("ana.ejemplo"), draA, bloqueo, turno, destino(drB, null));

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(estadoEnLaBase(turno)).isEqualTo("RESERVADO");
        }

        private MvcResult reasignarPorBloqueo(Navegador navegador, long profesional, long idBloqueo, long turnoId,
                                              Map<String, Object> destino) throws Exception {
            return navegador.post(bloqueos(profesional) + "/" + idBloqueo + "/turnos/" + turnoId + "/reasignar",
                    json.writeValueAsString(destino)).andReturn();
        }
    }

    private MvcResult reasignar(Navegador navegador, long profesional, long ausencia, long turnoId,
                                Map<String, Object> destino) throws Exception {
        return navegador.post(ausencias(profesional) + "/" + ausencia + "/turnos/" + turnoId + "/reasignar",
                json.writeValueAsString(destino)).andReturn();
    }

    private static Map<String, Object> destino(Long profesional, String inicio) {
        Map<String, Object> destino = new LinkedHashMap<>();
        if (profesional != null) {
            destino.put("profesionalId", profesional);
        }
        if (inicio != null) {
            destino.put("inicio", inicio);
        }
        return destino;
    }

    private void habilitar(long profesional, long prestacion) {
        // La base común ya habilita a todos para "Consulta" (change validar-habilitacion-profesional).
        jdbc.update("INSERT INTO profesional_prestacion(profesional_id, prestacion_id) VALUES (?, ?) "
                + "ON CONFLICT DO NOTHING", profesional, prestacion);
    }

    private long turnoDePrestacion(long profesional, long prestacion, String inicio) throws Exception {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("pacienteId", pacienteUno);
        pedido.put("profesionalId", profesional);
        pedido.put("sillonId", sillon2);
        pedido.put("prestacionId", prestacion);
        pedido.put("inicio", inicio);
        MvcResult resultado = como("recepcion").post(TURNOS, json.writeValueAsString(pedido)).andReturn();
        assertThat(status(resultado)).as(resultado.getResponse().getContentAsString()).isEqualTo(201);
        return cuerpo(resultado).path("id").asLong();
    }

    private long pacienteDe(long turnoId) {
        return jdbc.queryForObject("SELECT paciente_id FROM turno WHERE id = ?", Long.class, turnoId);
    }

    /** Doble del aviso: registra los turnos pedidos y, si se le indica, falla como un envío caído. */
    static class AvisoDoble implements AvisoReasignacion {

        private final List<Long> pedidos = new CopyOnWriteArrayList<>();
        private volatile boolean fallar;

        @Override
        public void avisar(Long turnoId) {
            if (fallar) {
                throw new IllegalStateException("envío simulado caído");
            }
            pedidos.add(turnoId);
        }

        List<Long> pedidos() {
            return pedidos;
        }

        void fallarDesdeAhora() {
            fallar = true;
        }

        void reiniciar() {
            pedidos.clear();
            fallar = false;
        }
    }

    @TestConfiguration
    static class ConAvisoDoble {
        @Bean
        @Primary
        AvisoDoble avisoDoble() {
            return new AvisoDoble();
        }
    }
}
