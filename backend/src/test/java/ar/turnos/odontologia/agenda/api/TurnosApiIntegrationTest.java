package ar.turnos.odontologia.agenda.api;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.agenda.IntegracionAgendaBase;
import ar.turnos.odontologia.shared.security.Navegador;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Un test por escenario del spec {@code agenda-api} (C-06), contra PostgreSQL real. */
@Tag("integration")
class TurnosApiIntegrationTest extends IntegracionAgendaBase {

    // ---------- Requirement: Crear un turno por la API ----------

    @Test
    @DisplayName("Recepción crea un turno")
    void recepcionCreaUnTurno() throws Exception {
        // GIVEN el usuario recepcion con sesión iniciada AND una agenda sin turnos
        Navegador recepcion = como("recepcion");

        // WHEN pide POST /api/v1/turnos para "Paciente Uno" con la Dra. A, el Sillón 1, "Consulta" e inicio 10:00
        MvcResult resultado = crearTurno(recepcion, pacienteUno, draA, sillon1, "2026-12-01T10:00:00-03:00");

        // THEN la respuesta es 201
        assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
        // AND el cuerpo tiene estado RESERVADO, fin 2026-12-01T10:30:00-03:00 y un numeroReserva
        JsonNode turno = cuerpo(resultado);
        assertThat(turno.get("estado").asText()).isEqualTo("RESERVADO");
        assertThat(turno.get("fin").asText()).isEqualTo("2026-12-01T10:30:00-03:00");
        assertThat(turno.get("numeroReserva").asText()).matches("[A-Z2-9]{4}-[A-Z2-9]{4}");
        assertThat(turno.get("pacienteId").asLong()).isEqualTo(pacienteUno);
    }

    // ---------- Requirement: Rechazos de negocio como 409 ----------

    @Test
    @DisplayName("Turno que choca con la agenda del profesional")
    void turnoQueChocaConElProfesional() throws Exception {
        // GIVEN recepcion con sesión AND un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
        turnoCreado(pacienteDos, draA, sillon1, hora(10, 0));

        // WHEN pide POST /api/v1/turnos con la Dra. A, el Sillón 2 e inicio a las 10:15
        MvcResult resultado = crearTurno(como("recepcion"), pacienteUno, draA, sillon2, hora(10, 15));

        // THEN la respuesta es 409 con el código SOLAPAMIENTO_PROFESIONAL
        assertProblema(resultado, 409, "SOLAPAMIENTO_PROFESIONAL");
    }

    @Test
    @DisplayName("Turno contiguo sin margen")
    void turnoContiguoSinMargen() throws Exception {
        // GIVEN recepcion con sesión AND un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
        turnoCreado(pacienteDos, draA, sillon1, hora(10, 0));

        // WHEN pide POST /api/v1/turnos con el Dr. B, el Sillón 1 e inicio a las 10:30
        MvcResult resultado = crearTurno(como("recepcion"), pacienteUno, drB, sillon1, hora(10, 30));

        // THEN la respuesta es 409 con el código MARGEN_INSUFICIENTE
        assertProblema(resultado, 409, "MARGEN_INSUFICIENTE");
    }

    @Test
    @DisplayName("Turno con un profesional no habilitado")
    void turnoConProfesionalNoHabilitado() throws Exception {
        // GIVEN recepcion con sesión AND que la Dra. A no está habilitada para "Tratamiento de conducto"
        long conducto = jdbc.queryForObject("INSERT INTO prestacion(nombre, duracion_minutos) "
                + "VALUES ('Tratamiento de conducto', 30) RETURNING id", Long.class);
        jdbc.update("INSERT INTO profesional_prestacion(profesional_id, prestacion_id) VALUES (?, ?)", drB, conducto);

        // WHEN pide POST /api/v1/turnos con la Dra. A, el Sillón 1, "Tratamiento de conducto" e inicio a las 10:00
        MvcResult resultado = como("recepcion").post(TURNOS, json.writeValueAsString(Map.of(
                "pacienteId", pacienteUno, "profesionalId", draA, "sillonId", sillon1,
                "prestacionId", conducto, "inicio", hora(10, 0))))
                .andReturn();

        // THEN la respuesta es 409 con el código PROFESIONAL_NO_HABILITADO
        assertProblema(resultado, 409, "PROFESIONAL_NO_HABILITADO");
        // AND no se crea ningún turno
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno", Long.class)).isZero();
    }

    // ---------- Requirement: Datos de entrada inválidos como 422 ----------

    @Test
    @DisplayName("Falta el inicio")
    void faltaElInicio() throws Exception {
        // GIVEN recepcion con sesión
        // WHEN pide POST /api/v1/turnos sin inicio
        MvcResult resultado = como("recepcion").post(TURNOS, json.writeValueAsString(Map.of(
                "pacienteId", pacienteUno, "profesionalId", draA, "sillonId", sillon1, "prestacionId", consulta)))
                .andReturn();

        // THEN la respuesta es 422 con el código DATOS_INVALIDOS AND la lista de errores incluye el campo inicio
        assertProblema(resultado, 422, "DATOS_INVALIDOS");
        assertThat(camposConError(resultado)).contains("inicio");
    }

    @Test
    @DisplayName("Profesional inexistente")
    void profesionalInexistente() throws Exception {
        // GIVEN recepcion con sesión
        // WHEN pide POST /api/v1/turnos con un profesionalId que no existe
        MvcResult resultado = crearTurno(como("recepcion"), pacienteUno, 999_999L, sillon1, hora(10, 0));

        // THEN la respuesta es 422 con el código DATOS_INVALIDOS AND la lista de errores incluye profesionalId
        assertProblema(resultado, 422, "DATOS_INVALIDOS");
        assertThat(camposConError(resultado)).contains("profesionalId");
    }

    // ---------- Requirement: Turno inexistente ----------

    @Test
    @DisplayName("Cancelar un turno que no existe")
    void cancelarUnTurnoQueNoExiste() throws Exception {
        // GIVEN recepcion con sesión
        // WHEN pide POST /api/v1/turnos/999999/cancelar con un DNI y un número de reserva
        MvcResult resultado = cancelar(como("recepcion"), 999_999L, DNI_PACIENTE_UNO, "K7M2-QX9P");

        // THEN la respuesta es 404 con el código TURNO_INEXISTENTE
        assertProblema(resultado, 404, "TURNO_INEXISTENTE");
    }

    // ---------- Requirement: Consultar la agenda ----------

    @Test
    @DisplayName("La agenda muestra solo los turnos activos")
    void laAgendaMuestraSoloLosActivos() throws Exception {
        // GIVEN ana.ejemplo con sesión AND un turno activo a las 10:00 y uno CANCELADO a las 11:00, con la Dra. A
        JsonNode activo = turnoCreado(pacienteUno, draA, sillon1, hora(10, 0));
        JsonNode aCancelar = turnoCreado(pacienteDos, draA, sillon1, hora(11, 0));
        cancelar(como("recepcion"), aCancelar.get("id").asLong(), "99000002", aCancelar.get("numeroReserva").asText());

        // WHEN pide GET /api/v1/turnos desde las 00:00 hasta las 23:59 del 2026-12-01
        MvcResult resultado = como("ana.ejemplo").get(agendaDelDia(null)).andReturn();

        // THEN la respuesta es 200 con un solo turno, el de las 10:00
        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        JsonNode turnos = cuerpo(resultado);
        assertThat(turnos).hasSize(1);
        assertThat(turnos.get(0).get("id").asLong()).isEqualTo(activo.get("id").asLong());
        assertThat(turnos.get(0).get("pacienteNombre").asText()).isEqualTo("Paciente");
        // AND el turno no incluye el número de reserva
        assertThat(turnos.get(0).has("numeroReserva")).isFalse();
        assertThat(resultado.getResponse().getContentAsString())
                .doesNotContain(activo.get("numeroReserva").asText(), DNI_PACIENTE_UNO);
    }

    @Test
    @DisplayName("Filtro por profesional")
    void filtroPorProfesional() throws Exception {
        // GIVEN recepcion con sesión AND un turno activo a las 10:00 con la Dra. A y otro a las 10:00 con el Dr. B
        JsonNode deLaDraA = turnoCreado(pacienteUno, draA, sillon1, hora(10, 0));
        turnoCreado(pacienteDos, drB, sillon2, hora(10, 0));

        // WHEN pide GET /api/v1/turnos del 2026-12-01 con profesionalId de la Dra. A
        MvcResult resultado = como("recepcion").get(agendaDelDia(draA)).andReturn();

        // THEN la respuesta es 200 con un solo turno, el de la Dra. A
        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        JsonNode turnos = cuerpo(resultado);
        assertThat(turnos).hasSize(1);
        assertThat(turnos.get(0).get("id").asLong()).isEqualTo(deLaDraA.get("id").asLong());
    }

    @Test
    @DisplayName("Falta el período")
    void faltaElPeriodo() throws Exception {
        // GIVEN recepcion con sesión
        // WHEN pide GET /api/v1/turnos sin desde
        MvcResult resultado = como("recepcion").get(TURNOS + "?hasta=2026-12-01T23:59:00-03:00").andReturn();

        // THEN la respuesta es 422 con el código DATOS_INVALIDOS
        assertProblema(resultado, 422, "DATOS_INVALIDOS");
    }

    // ---------- Requirement: Cancelar y reprogramar por la API ----------

    @Test
    @DisplayName("Recepción cancela un turno")
    void recepcionCancelaUnTurno() throws Exception {
        // GIVEN recepcion con sesión AND un turno activo de "Paciente Uno" a las 10:00 con la Dra. A en el Sillón 1
        Navegador recepcion = como("recepcion");
        JsonNode turno = turnoCreado(pacienteUno, draA, sillon1, hora(10, 0));

        // WHEN pide POST /api/v1/turnos/{id}/cancelar con el DNI 99000001 y el número de reserva del turno
        MvcResult resultado = cancelar(recepcion, turno.get("id").asLong(), DNI_PACIENTE_UNO,
                turno.get("numeroReserva").asText());

        // THEN la respuesta es 200 con estado CANCELADO
        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(cuerpo(resultado).get("estado").asText()).isEqualTo("CANCELADO");
        // AND un POST /api/v1/turnos con la Dra. A, el Sillón 1 e inicio a las 10:00 responde 201
        assertThat(crearTurno(recepcion, pacienteDos, draA, sillon1, hora(10, 0)).getResponse().getStatus())
                .isEqualTo(201);
    }

    @Test
    @DisplayName("Recepción reprograma un turno")
    void recepcionReprogramaUnTurno() throws Exception {
        // GIVEN recepcion con sesión AND un turno activo de "Paciente Uno" a las 10:00 con la Dra. A en el Sillón 1
        JsonNode original = turnoCreado(pacienteUno, draA, sillon1, hora(10, 0));

        // WHEN pide POST /api/v1/turnos/{id}/reprogramar con el DNI, el número de reserva e inicio a las 11:00
        MvcResult resultado = reprogramar(como("recepcion"), original.get("id").asLong(),
                original.get("numeroReserva").asText(), hora(11, 0), null, null);

        // THEN la respuesta es 200 con un turno de 11:00 a 11:30 en estado RESERVADO y un número de reserva nuevo
        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        JsonNode nuevo = cuerpo(resultado);
        assertThat(nuevo.get("inicio").asText()).isEqualTo(hora(11, 0));
        assertThat(nuevo.get("fin").asText()).isEqualTo(hora(11, 30));
        assertThat(nuevo.get("estado").asText()).isEqualTo("RESERVADO");
        assertThat(nuevo.get("numeroReserva").asText()).isNotEqualTo(original.get("numeroReserva").asText());
        // AND el turno original queda en estado CANCELADO
        assertThat(estadoEnLaBase(original.get("id").asLong())).isEqualTo("CANCELADO");
    }

    @Test
    @DisplayName("Reprogramación rechazada deja el turno intacto")
    void reprogramacionRechazadaDejaElTurnoIntacto() throws Exception {
        // GIVEN recepcion con sesión AND un turno activo de "Paciente Uno" a las 10:00 con la Dra. A en el Sillón 1
        JsonNode original = turnoCreado(pacienteUno, draA, sillon1, hora(10, 0));
        // AND otro turno activo a las 11:00 con el Dr. B en el Sillón 1
        turnoCreado(pacienteDos, drB, sillon1, hora(11, 0));
        int turnosAntes = jdbc.queryForObject("SELECT count(*) FROM turno", Integer.class);

        // WHEN pide POST /api/v1/turnos/{id}/reprogramar del primero a las 11:10 con la Dra. A en el Sillón 1
        MvcResult resultado = reprogramar(como("recepcion"), original.get("id").asLong(),
                original.get("numeroReserva").asText(), hora(11, 10), draA, sillon1);

        // THEN la respuesta es 409 con el código SOLAPAMIENTO_SILLON
        assertProblema(resultado, 409, "SOLAPAMIENTO_SILLON");
        // AND el turno original sigue en estado RESERVADO a las 10:00
        assertThat(estadoEnLaBase(original.get("id").asLong())).isEqualTo("RESERVADO");
        assertThat(jdbc.queryForObject("SELECT inicio = ?::timestamptz FROM turno WHERE id = ?", Boolean.class,
                hora(10, 0), original.get("id").asLong())).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno", Integer.class)).isEqualTo(turnosAntes);
    }

    // ---------- Requirement: Reservas simultáneas ----------

    @RepeatedTest(10)
    @DisplayName("Dos reservas al mismo tiempo para el mismo sillón")
    void dosReservasAlMismoTiempo() throws Exception {
        // GIVEN recepcion y ana.ejemplo con sesión iniciada AND una agenda sin turnos
        Navegador recepcion = como("recepcion");
        Navegador ana = como("ana.ejemplo");
        CountDownLatch largada = new CountDownLatch(1);

        // WHEN los dos piden al mismo tiempo POST /api/v1/turnos para el Sillón 1 a las 10:00 (Dra. A y Dr. B)
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            List<Future<MvcResult>> pedidos = new ArrayList<>();
            pedidos.add(hilos.submit(enLaLargada(largada, () -> crearTurno(recepcion, pacienteUno, draA, sillon1, hora(10, 0)))));
            pedidos.add(hilos.submit(enLaLargada(largada, () -> crearTurno(ana, pacienteDos, drB, sillon1, hora(10, 0)))));
            largada.countDown();

            List<Integer> estados = new ArrayList<>();
            List<String> codigos = new ArrayList<>();
            for (Future<MvcResult> pedido : pedidos) {
                MvcResult resultado = pedido.get();
                estados.add(resultado.getResponse().getStatus());
                if (resultado.getResponse().getStatus() == 409) {
                    codigos.add(cuerpo(resultado).get("codigo").asText());
                }
            }

            // THEN una respuesta es 201 AND la otra es 409 con el código SOLAPAMIENTO_SILLON
            assertThat(estados).containsExactlyInAnyOrder(201, 409);
            assertThat(codigos).containsExactly("SOLAPAMIENTO_SILLON");
            // AND la agenda tiene un solo turno activo a las 10:00 en el Sillón 1
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM turno WHERE sillon_id = ? AND estado IN ('RESERVADO','CONFIRMADO')",
                    Integer.class, sillon1)).isEqualTo(1);
        } finally {
            hilos.shutdownNow();
        }
    }

    // ---------- Requirement: Roles de la API de turnos ----------

    @Test
    @DisplayName("El odontólogo crea un turno")
    void elOdontologoCreaUnTurno() throws Exception {
        // GIVEN ana.ejemplo con sesión AND una agenda sin turnos
        // WHEN pide POST /api/v1/turnos con la Dra. A, el Sillón 1 e inicio a las 10:00
        MvcResult resultado = crearTurno(como("ana.ejemplo"), pacienteUno, draA, sillon1, hora(10, 0));

        // THEN la respuesta es 201
        assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("Un paciente intenta crear un turno por la API del personal")
    void unPacienteIntentaCrearUnTurno() throws Exception {
        // GIVEN "Paciente Uno" con sesión iniciada
        // WHEN pide POST /api/v1/turnos con la Dra. A, el Sillón 1 e inicio a las 10:00
        MvcResult resultado = crearTurno(como("paciente.uno@example.com"), pacienteUno, draA, sillon1, hora(10, 0));

        // THEN la respuesta es 403 con el código ACCESO_DENEGADO AND no se crea ningún turno
        assertProblema(resultado, 403, "ACCESO_DENEGADO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno", Integer.class)).isZero();
    }

    // ---------- Ayudas ----------

    private static <T> Callable<T> enLaLargada(CountDownLatch largada, Callable<T> pedido) {
        return () -> {
            largada.await();
            return pedido.call();
        };
    }

    private static String agendaDelDia(Long profesionalId) {
        String url = TURNOS + "?desde=" + hora(0, 0) + "&hasta=" + hora(23, 59);
        return profesionalId == null ? url : url + "&profesionalId=" + profesionalId;
    }

    private void assertProblema(MvcResult resultado, int estado, String codigo) throws Exception {
        assertThat(resultado.getResponse().getStatus())
                .as(resultado.getResponse().getContentAsString())
                .isEqualTo(estado);
        assertThat(cuerpo(resultado).get("codigo").asText()).isEqualTo(codigo);
    }

    private List<String> camposConError(MvcResult resultado) throws Exception {
        List<String> campos = new ArrayList<>();
        cuerpo(resultado).get("errores").forEach(e -> campos.add(e.get("campo").asText()));
        return campos;
    }
}
