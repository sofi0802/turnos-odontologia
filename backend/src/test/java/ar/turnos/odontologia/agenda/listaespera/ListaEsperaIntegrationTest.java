package ar.turnos.odontologia.agenda.listaespera;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.agenda.listaespera.infrastructure.ServicioListaEspera;
import ar.turnos.odontologia.notificaciones.application.HuellaToken;
import ar.turnos.odontologia.reserva.IntegracionReservaBase;
import ar.turnos.odontologia.shared.security.Navegador;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * C-15: escenarios del spec {@code lista-espera} de punta a punta, con PostgreSQL real, sesión y CSRF como el
 * frontend, el reloj fijo del 2026-12-01 a las 08:00 y los avisos capturados. La tarea programada está apagada: los
 * vencimientos se procesan a mano. "Limpieza" del spec es "Consulta" (30 min) de la base; "Paciente Tres" no tiene
 * cuenta y es el dueño de los turnos que se cancelan.
 */
@Import(AvisosDeOfertaCapturados.class)
class ListaEsperaIntegrationTest extends IntegracionReservaBase {

    private static final String PACIENTE = "/api/v1/paciente/lista-espera";
    private static final String PERSONAL = "/api/v1/lista-espera";
    private static final String PLAZO = "/api/v1/configuracion/lista-espera";
    private static final String OFERTAS = "/api/v1/public/ofertas/";
    private static final String DNI_TRES = "99000003";

    @Autowired AvisosDeOfertaCapturados.Registro avisos;
    @Autowired ServicioListaEspera servicio;

    private long pacienteTres;
    private long control;
    private long cirugia;

    @BeforeEach
    void datosDeLaListaDeEspera() {
        avisos.reiniciar();
        jdbc.update("INSERT INTO configuracion (clave, valor) VALUES ('lista_espera_plazo_horas', '2') "
                + "ON CONFLICT (clave) DO UPDATE SET valor = '2'");
        for (int dia = 1; dia <= 5; dia++) {
            franja(draA, dia, "08:00", "13:00");
            franja(drB, dia, "08:00", "18:00");
        }
        control = id("INSERT INTO prestacion(nombre, duracion_minutos) VALUES ('Control', 30) RETURNING id");
        cirugia = id("INSERT INTO prestacion(nombre, duracion_minutos) VALUES ('Cirugía', 60) RETURNING id");
        jdbc.update("INSERT INTO profesional_prestacion(profesional_id, prestacion_id) VALUES (?,?),(?,?)",
                draA, control, draA, cirugia);
        jdbc.update("UPDATE paciente SET consentimiento_comunicaciones = TRUE, consentimiento_fecha = now()");
        pacienteTres = crearPacienteSinCuenta("Paciente", "Tres", DNI_TRES, "paciente.tres@example.com");
        crearPersonal("admin", "ADMIN", clave);
        jdbc.update("INSERT INTO usuario(username, password_hash, rol, profesional_id) VALUES ('ana',?,'ODONTOLOGO',?)",
                passwordEncoder.encode(clave), draA);
    }

    // ---------- auxiliares ----------

    private static Map<String, Object> pedido(long prestacion, Long profesional, int desde, int hasta) {
        Map<String, Object> pedido = new HashMap<>();
        pedido.put("prestacionId", prestacion);
        pedido.put("profesionalId", profesional);
        pedido.put("desde", fecha(desde));
        pedido.put("hasta", fecha(hasta));
        return pedido;
    }

    private MvcResult anotarme(Navegador paciente, long prestacion, Long profesional, int desde, int hasta)
            throws Exception {
        return paciente.post(PACIENTE, cuerpoJson(pedido(prestacion, profesional, desde, hasta))).andReturn();
    }

    private MvcResult anotar(long paciente, long prestacion, Long profesional, int desde, int hasta) throws Exception {
        Map<String, Object> pedido = pedido(prestacion, profesional, desde, hasta);
        pedido.put("pacienteId", paciente);
        return comoRecepcion().post(PERSONAL, cuerpoJson(pedido)).andReturn();
    }

    /** Anota a un paciente con el reloj en una hora del 2026-12-01, para fijar el orden de llegada. */
    private void anotarALas(int hora, long paciente, long prestacion, Long profesional) throws Exception {
        reloj.fijarEn(1, hora, 0);
        assertThat(anotar(paciente, prestacion, profesional, 1, 15).getResponse().getStatus()).isEqualTo(201);
        reloj.fijarEn(1, 8, 0);
    }

    /** Turno activo de "Paciente Tres" que la recepción cancela después. */
    private long turnoDeTres(long profesional, long sillon, int dia, int hora, int minuto) {
        return turnoActivo(pacienteTres, profesional, sillon, dia, hora, minuto);
    }

    private MvcResult cancelar(long turno) throws Exception {
        return comoRecepcion().post("/api/v1/turnos/" + turno + "/cancelar",
                cuerpoJson(Map.of("dni", DNI_TRES, "numeroReserva", numeroDe(turno)))).andReturn();
    }

    private List<Map<String, Object>> ofertas() {
        return jdbc.queryForList("SELECT o.id, o.turno_liberado_id, o.estado, o.inicio, o.vence_en, o.token_hash, "
                + "i.paciente_id, i.estado AS inscripcion_estado FROM oferta_lista_espera o "
                + "JOIN inscripcion_lista_espera i ON i.id = o.inscripcion_id ORDER BY o.id");
    }

    private List<Map<String, Object>> pendientes() {
        return ofertas().stream().filter(o -> "PENDIENTE".equals(o.get("estado"))).toList();
    }

    private String estadoInscripcionDe(long paciente) {
        return jdbc.queryForObject("SELECT estado FROM inscripcion_lista_espera WHERE paciente_id = ? "
                + "ORDER BY id DESC LIMIT 1", String.class, paciente);
    }

    private static Instant instante(int dia, int hora, int minuto) {
        return LocalDateTime.of(2026, 12, dia, hora, minuto).atZone(BUENOS_AIRES).toInstant();
    }

    private static Instant fechaHora(Object valor) {
        return valor instanceof java.sql.Timestamp ts ? ts.toInstant() : ((OffsetDateTime) valor).toInstant();
    }

    /** Uno anotado para Consulta con la Dra. A y oferta pendiente del 2026-12-03 a las 10:00; devuelve el token. */
    private String ofertaParaUno() throws Exception {
        anotarALas(7, pacienteUno, consulta, draA);
        cancelar(turnoDeTres(draA, sillon1, 3, 10, 0));
        assertThat(avisos.avisados).hasSize(1);
        return avisos.ultimo().token();
    }

    private MvcResult aceptar(String token) throws Exception {
        return sinSesion().post(OFERTAS + token + "/aceptar", null).andReturn();
    }

    private MvcResult rechazar(String token) throws Exception {
        return sinSesion().post(OFERTAS + token + "/rechazar", null).andReturn();
    }

    private MvcResult ponerPlazo(Navegador navegador, int horas) throws Exception {
        return navegador.enviar(MockMvcRequestBuilders.put(PLAZO).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoJson(Map.of("plazoHoras", horas)))
                .header(Navegador.ENCABEZADO_CSRF, navegador.tokenCsrf())).andReturn();
    }

    private Navegador comoAdmin() throws Exception {
        return iniciarSesion("admin", clave);
    }

    // ---------- inscripción ----------

    @Nested
    class Inscripcion {

        @Test
        void elPacienteSeAnota() throws Exception {
            MvcResult resultado = anotarme(comoPacienteUno(), consulta, draA, 1, 15);

            assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
            JsonNode cuerpo = cuerpo(resultado);
            assertThat(cuerpo.get("estado").asText()).isEqualTo("ACTIVA");
            assertThat(cuerpo.get("prestacionNombre").asText()).isEqualTo("Consulta");
            assertThat(cuerpo.get("profesionalId").asLong()).isEqualTo(draA);
            assertThat(estadoInscripcionDe(pacienteUno)).isEqualTo("ACTIVA");
        }

        @Test
        void laRecepcionAnotaSinPreferencia() throws Exception {
            jdbc.update("DELETE FROM usuario WHERE paciente_id = ?", pacienteDos);

            MvcResult resultado = anotar(pacienteDos, consulta, null, 1, 10);

            assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
            JsonNode cuerpo = cuerpo(resultado);
            assertThat(cuerpo.get("estado").asText()).isEqualTo("ACTIVA");
            assertThat(cuerpo.get("profesionalId").isNull()).isTrue();
            assertThat(cuerpo.get("pacienteDni").asText()).isEqualTo(DNI_DOS);
        }

        @Test
        void rangoDeMasDe60Dias() throws Exception {
            Map<String, Object> pedido = pedido(consulta, draA, 1, 1);
            pedido.put("hasta", "2027-02-15");

            MvcResult resultado = comoPacienteUno().post(PACIENTE, cuerpoJson(pedido)).andReturn();

            assertThat(resultado.getResponse().getStatus()).isEqualTo(422);
            assertThat(cuerpo(resultado).get("codigo").asText()).isEqualTo("DATOS_INVALIDOS");
            assertThat(id("SELECT count(*) FROM inscripcion_lista_espera")).isZero();
        }

        @Test
        void rangoQueEmpiezaEnElPasado() throws Exception {
            Map<String, Object> pedido = pedido(consulta, draA, 5, 5);
            pedido.put("desde", "2026-11-28");

            MvcResult resultado = comoPacienteUno().post(PACIENTE, cuerpoJson(pedido)).andReturn();

            assertThat(resultado.getResponse().getStatus()).isEqualTo(422);
            assertThat(cuerpo(resultado).get("codigo").asText()).isEqualTo("DATOS_INVALIDOS");
        }

        @Test
        void segundaInscripcionParaLaMismaPrestacion() throws Exception {
            Navegador uno = comoPacienteUno();
            anotarme(uno, consulta, draA, 1, 15);

            MvcResult resultado = anotarme(uno, consulta, null, 2, 10);

            assertThat(resultado.getResponse().getStatus()).isEqualTo(409);
            assertThat(cuerpo(resultado).get("codigo").asText()).isEqualTo("YA_EN_LISTA_DE_ESPERA");
        }

        @Test
        void otraPrestacionSiSeAcepta() throws Exception {
            Navegador uno = comoPacienteUno();
            anotarme(uno, consulta, draA, 1, 15);

            assertThat(anotarme(uno, control, draA, 1, 15).getResponse().getStatus()).isEqualTo(201);
        }

        @Test
        void pacienteSinConsentimiento() throws Exception {
            jdbc.update("UPDATE paciente SET consentimiento_comunicaciones = FALSE WHERE id = ?", pacienteTres);

            MvcResult resultado = anotar(pacienteTres, consulta, draA, 1, 15);

            assertThat(resultado.getResponse().getStatus()).isEqualTo(409);
            assertThat(cuerpo(resultado).get("codigo").asText()).isEqualTo("SIN_CONSENTIMIENTO");
            assertThat(id("SELECT count(*) FROM inscripcion_lista_espera")).isZero();
        }

        @Test
        void profesionalNoHabilitado() throws Exception {
            MvcResult resultado = anotarme(comoPacienteUno(), conducto, draA, 1, 15);

            assertThat(resultado.getResponse().getStatus()).isEqualTo(409);
            assertThat(cuerpo(resultado).get("codigo").asText()).isEqualTo("PROFESIONAL_NO_HABILITADO");
        }
    }

    // ---------- ofertas ----------

    @Nested
    class Ofertas {

        @Test
        void seOfrecePorOrdenDeLlegada() throws Exception {
            anotarALas(6, pacienteUno, consulta, draA);
            anotarALas(7, pacienteDos, consulta, null);

            cancelar(turnoDeTres(draA, sillon1, 3, 10, 0));

            assertThat(pendientes()).hasSize(1);
            Map<String, Object> oferta = pendientes().get(0);
            assertThat(oferta.get("paciente_id")).isEqualTo(pacienteUno);
            assertThat(fechaHora(oferta.get("inicio"))).isEqualTo(instante(3, 10, 0));
            assertThat(avisos.avisados).extracting(AvisosDeOfertaCapturados.Avisado::pacienteId)
                    .containsExactly(pacienteUno);
        }

        @Test
        void unaPrestacionDistintaQueEntraEnElHorario() throws Exception {
            anotarALas(7, pacienteUno, consulta, drB);
            long turno = id("INSERT INTO turno(profesional_id, sillon_id, prestacion_id, paciente_id, inicio, fin, "
                    + "fin_con_margen, estado) VALUES (?,?,?,?,?,?,?, 'RESERVADO') RETURNING id", drB, sillon2,
                    conducto, pacienteTres, java.sql.Timestamp.from(instante(3, 10, 0)),
                    java.sql.Timestamp.from(instante(3, 10, 30)), java.sql.Timestamp.from(instante(3, 10, 40)));

            cancelar(turno);

            assertThat(pendientes()).singleElement().satisfies(oferta -> {
                assertThat(oferta.get("paciente_id")).isEqualTo(pacienteUno);
                assertThat(fechaHora(oferta.get("inicio"))).isEqualTo(instante(3, 10, 0));
            });
        }

        @Test
        void laPrestacionNoEntraEnElHorario() throws Exception {
            anotarALas(7, pacienteUno, cirugia, draA);
            long liberado = turnoDeTres(draA, sillon1, 3, 10, 0);
            turnoActivo(pacienteDos, draA, sillon1, 3, 10, 40);

            cancelar(liberado);

            assertThat(ofertas()).isEmpty();
            assertThat(avisos.avisados).isEmpty();
        }

        @Test
        void otroProfesionalElegido() throws Exception {
            anotarALas(7, pacienteUno, consulta, drB);

            cancelar(turnoDeTres(draA, sillon1, 3, 10, 0));

            assertThat(ofertas()).isEmpty();
        }

        @Test
        void fueraDelRangoDeFechas() throws Exception {
            assertThat(anotar(pacienteUno, consulta, draA, 1, 5).getResponse().getStatus()).isEqualTo(201);

            cancelar(turnoDeTres(draA, sillon1, 9, 10, 0));

            assertThat(ofertas()).isEmpty();
        }

        @Test
        void laReprogramacionLiberaElHorarioViejo() throws Exception {
            anotarALas(7, pacienteUno, consulta, draA);
            long turno = turnoDeTres(draA, sillon1, 3, 10, 0);

            MvcResult resultado = comoRecepcion().post("/api/v1/turnos/" + turno + "/reprogramar",
                    cuerpoJson(Map.of("dni", DNI_TRES, "numeroReserva", numeroDe(turno), "inicio", en(10, 10, 0))))
                    .andReturn();

            assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
            assertThat(pendientes()).singleElement().satisfies(oferta -> {
                assertThat(oferta.get("paciente_id")).isEqualTo(pacienteUno);
                assertThat(fechaHora(oferta.get("inicio"))).isEqualTo(instante(3, 10, 0));
            });
        }

        @Test
        void laAusenciaDelPacienteNoGeneraOfertas() throws Exception {
            anotarALas(7, pacienteUno, consulta, draA);
            long turno = turnoDeTres(draA, sillon1, 1, 8, 0);
            reloj.fijarEn(1, 8, 20);

            MvcResult resultado = comoRecepcion().post("/api/v1/turnos/" + turno + "/ausencia", null).andReturn();

            assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
            assertThat(ofertas()).isEmpty();
        }

        @Test
        void elPacienteConUnaOfertaPendienteSeSaltea() throws Exception {
            anotarALas(6, pacienteUno, consulta, draA);
            anotarALas(7, pacienteDos, consulta, draA);
            cancelar(turnoDeTres(draA, sillon1, 3, 10, 0));

            cancelar(turnoDeTres(draA, sillon1, 4, 10, 0));

            assertThat(pendientes()).extracting(o -> o.get("paciente_id")).containsExactly(pacienteUno, pacienteDos);
        }

        @Test
        void laCancelacionSeGuardaAunqueFalleElAviso() throws Exception {
            anotarALas(7, pacienteUno, consulta, draA);
            avisos.falla = true;
            long turno = turnoDeTres(draA, sillon1, 3, 10, 0);

            MvcResult resultado = cancelar(turno);

            assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
            assertThat(estadoDe(turno)).isEqualTo("CANCELADO");
            assertThat(pendientes()).hasSize(1);
            assertThat(id("SELECT count(*) FROM oferta_lista_espera WHERE aviso_enviado")).isZero();
        }

        @Test
        void elAvisoFallidoSeReintentaConUnTokenNuevo() throws Exception {
            anotarALas(7, pacienteUno, consulta, draA);
            avisos.falla = true;
            cancelar(turnoDeTres(draA, sillon1, 3, 10, 0));
            String huellaVieja = (String) pendientes().get(0).get("token_hash");
            avisos.falla = false;

            servicio.procesarVencimientos();

            assertThat(avisos.avisados).hasSize(1);
            String token = avisos.ultimo().token();
            assertThat(pendientes().get(0).get("token_hash")).isEqualTo(HuellaToken.de(token)).isNotEqualTo(huellaVieja);
            assertThat(id("SELECT count(*) FROM oferta_lista_espera WHERE aviso_enviado")).isEqualTo(1);
        }
    }

    // ---------- plazo ----------

    @Nested
    class Plazo {

        @Test
        void plazoPorDefecto() throws Exception {
            anotarALas(7, pacienteUno, consulta, drB);

            cancelar(turnoDeTres(drB, sillon2, 1, 15, 0));

            assertThat(fechaHora(pendientes().get(0).get("vence_en"))).isEqualTo(instante(1, 10, 0));
        }

        @Test
        void elInicioLlegaAntesQueElPlazo() throws Exception {
            anotarALas(7, pacienteUno, consulta, draA);
            long turno = turnoDeTres(draA, sillon1, 1, 10, 0);
            reloj.fijarEn(1, 9, 0);

            cancelar(turno);

            assertThat(fechaHora(pendientes().get(0).get("vence_en"))).isEqualTo(instante(1, 10, 0));
        }

        @Test
        void elAdministradorCambiaElPlazo() throws Exception {
            Navegador admin = comoAdmin();

            MvcResult resultado = ponerPlazo(admin, 4);

            assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
            assertThat(cuerpo(resultado).get("plazoHoras").asInt()).isEqualTo(4);
            assertThat(cuerpo(admin.get(PLAZO).andReturn()).get("plazoHoras").asInt()).isEqualTo(4);
            anotarALas(7, pacienteUno, consulta, draA);
            cancelar(turnoDeTres(draA, sillon1, 3, 10, 0));
            assertThat(fechaHora(pendientes().get(0).get("vence_en"))).isEqualTo(instante(1, 12, 0));
        }

        @Test
        void plazoFueraDeRango() throws Exception {
            Navegador admin = comoAdmin();

            for (int horas : new int[] {0, 25}) {
                MvcResult resultado = ponerPlazo(admin, horas);
                assertThat(resultado.getResponse().getStatus()).isEqualTo(422);
                assertThat(cuerpo(resultado).get("codigo").asText()).isEqualTo("PLAZO_INVALIDO");
            }
        }

        @Test
        void laRecepcionNoCambiaElPlazo() throws Exception {
            Navegador recepcion = comoRecepcion();

            MvcResult resultado = ponerPlazo(recepcion, 4);

            assertThat(resultado.getResponse().getStatus()).isEqualTo(403);
            assertThat(cuerpo(resultado).get("codigo").asText()).isEqualTo("ACCESO_DENEGADO");
            assertThat(cuerpo(recepcion.get(PLAZO).andReturn()).get("plazoHoras").asInt()).isEqualTo(2);
        }
    }

    // ---------- respuesta ----------

    @Nested
    class Respuesta {

        @Test
        void verUnaOfertaPendiente() throws Exception {
            String token = ofertaParaUno();

            MvcResult resultado = navegador().get(OFERTAS + token).andReturn();

            assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
            JsonNode cuerpo = cuerpo(resultado);
            assertThat(cuerpo.get("fecha").asText()).isEqualTo("2026-12-03");
            assertThat(cuerpo.get("hora").asText()).isEqualTo("10:00");
            assertThat(cuerpo.get("profesional").asText()).contains("Ana Ejemplo");
            assertThat(cuerpo.get("estado").asText()).isEqualTo("PENDIENTE");
            assertThat(cuerpo.has("venceEn")).isTrue();
            assertThat(texto(resultado)).doesNotContain("Consulta");
            assertThat(pendientes()).hasSize(1);
        }

        @Test
        void tokenInexistente() throws Exception {
            for (String token : List.of("nunca-se-emitio", "x".repeat(129))) {
                MvcResult resultado = navegador().get(OFERTAS + token).andReturn();
                assertThat(resultado.getResponse().getStatus()).isEqualTo(404);
                assertThat(cuerpo(resultado).get("codigo").asText()).isEqualTo("OFERTA_INEXISTENTE");
            }
        }

        @Test
        void aceptarATiempo() throws Exception {
            String token = ofertaParaUno();
            long ofertaId = (Long) pendientes().get(0).get("id");

            MvcResult resultado = aceptar(token);

            assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
            JsonNode cuerpo = cuerpo(resultado);
            assertThat(cuerpo.get("numeroReserva").asText()).isNotBlank();
            assertThat(cuerpo.get("fecha").asText()).isEqualTo("2026-12-03");
            assertThat(cuerpo.get("hora").asText()).isEqualTo("10:00");
            List<Map<String, Object>> turnos = jdbc.queryForList("SELECT id, estado, profesional_id, inicio, "
                    + "codigo_reserva FROM turno WHERE paciente_id = ?", pacienteUno);
            assertThat(turnos).singleElement().satisfies(turno -> {
                assertThat(turno.get("estado")).isEqualTo("RESERVADO");
                assertThat(turno.get("profesional_id")).isEqualTo(draA);
                assertThat(fechaHora(turno.get("inicio"))).isEqualTo(instante(3, 10, 0));
                assertThat(turno.get("codigo_reserva")).isEqualTo(cuerpo.get("numeroReserva").asText());
            });
            assertThat(estadoInscripcionDe(pacienteUno)).isEqualTo("ASIGNADA");
            assertThat(jdbc.queryForObject("SELECT estado FROM oferta_lista_espera WHERE id = ?", String.class,
                    ofertaId)).isEqualTo("ACEPTADA");
            assertThat(jdbc.queryForList("SELECT actor FROM turno_auditoria WHERE turno_id = ?", String.class,
                    turnos.get(0).get("id"))).contains("lista-espera:" + ofertaId);
        }

        @Test
        void elHorarioSeOcupoDuranteLaOferta() throws Exception {
            String token = ofertaParaUno();
            Map<String, Object> reserva = new LinkedHashMap<>();
            reserva.put("prestacionId", consulta);
            reserva.put("inicio", en(3, 10, 0));
            reserva.put("profesionalId", draA);
            assertThat(comoPacienteDos().post("/api/v1/paciente/reservas", cuerpoJson(reserva)).andReturn()
                    .getResponse().getStatus()).isEqualTo(201);

            MvcResult resultado = aceptar(token);

            assertThat(resultado.getResponse().getStatus()).isEqualTo(409);
            assertThat(cuerpo(resultado).get("codigo").asText()).isEqualTo("OFERTA_NO_DISPONIBLE");
            assertThat(estadoInscripcionDe(pacienteUno)).isEqualTo("ACTIVA");
            assertThat(pendientes()).isEmpty();
            assertThat(ofertas()).singleElement().extracting(o -> o.get("estado")).isEqualTo("NO_DISPONIBLE");
            assertThat(contarTurnosDe(pacienteUno)).isZero();
        }

        @Test
        void aceptarDespuesDelVencimiento() throws Exception {
            String token = ofertaParaUno();
            reloj.fijarEn(1, 10, 5);

            MvcResult resultado = aceptar(token);

            assertThat(resultado.getResponse().getStatus()).isEqualTo(409);
            assertThat(cuerpo(resultado).get("codigo").asText()).isEqualTo("OFERTA_VENCIDA");
            assertThat(contarTurnosDe(pacienteUno)).isZero();
            // Token y privacidad: el error no lleva el token ni la prestación
            assertThat(texto(resultado)).doesNotContain(token).doesNotContain("Consulta");
        }

        @Test
        void aceptarDosVeces() throws Exception {
            String token = ofertaParaUno();
            aceptar(token);

            MvcResult resultado = aceptar(token);

            assertThat(resultado.getResponse().getStatus()).isEqualTo(409);
            assertThat(cuerpo(resultado).get("codigo").asText()).isEqualTo("OFERTA_YA_RESPONDIDA");
            assertThat(contarTurnosDe(pacienteUno)).isEqualTo(1);
        }

        @Test
        void noMeInteresa() throws Exception {
            anotarALas(8, pacienteDos, consulta, null);
            String token = ofertaParaUno();

            MvcResult resultado = rechazar(token);

            assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
            assertThat(estadoInscripcionDe(pacienteUno)).isEqualTo("ACTIVA");
            assertThat(ofertas()).extracting(o -> o.get("paciente_id") + ":" + o.get("estado"))
                    .containsExactly(pacienteUno + ":RECHAZADA", pacienteDos + ":PENDIENTE");
        }

        @Test
        void rechazoSinNadieMasEnLaLista() throws Exception {
            String token = ofertaParaUno();

            rechazar(token);

            assertThat(pendientes()).isEmpty();
        }
    }

    // ---------- vencimientos ----------

    @Nested
    class Vencimientos {

        @Test
        void venceYPasaAlSiguiente() throws Exception {
            anotarALas(6, pacienteUno, consulta, drB);
            anotarALas(7, pacienteDos, consulta, null);
            cancelar(turnoDeTres(drB, sillon2, 1, 15, 0));
            reloj.fijarEn(1, 10, 1);

            servicio.procesarVencimientos();

            assertThat(ofertas()).extracting(o -> o.get("paciente_id") + ":" + o.get("estado"))
                    .containsExactly(pacienteUno + ":VENCIDA", pacienteDos + ":PENDIENTE");
            assertThat(estadoInscripcionDe(pacienteUno)).isEqualTo("ACTIVA");
        }

        @Test
        void venceCuandoElHorarioYaEmpezo() throws Exception {
            anotarALas(6, pacienteUno, consulta, drB);
            anotarALas(7, pacienteDos, consulta, null);
            cancelar(turnoDeTres(drB, sillon2, 1, 9, 0));
            reloj.fijarEn(1, 9, 1);

            servicio.procesarVencimientos();

            assertThat(ofertas()).singleElement().extracting(o -> o.get("estado")).isEqualTo("VENCIDA");
        }

        @Test
        void venceLaInscripcion() throws Exception {
            assertThat(anotar(pacienteUno, consulta, draA, 1, 5).getResponse().getStatus()).isEqualTo(201);
            reloj.fijarEn(6, 0, 30);

            servicio.procesarVencimientos();

            assertThat(estadoInscripcionDe(pacienteUno)).isEqualTo("VENCIDA");
        }
    }

    // ---------- consultar y dar de baja ----------

    @Nested
    class ConsultaYBaja {

        @Test
        void elPacienteVeSoloLasSuyas() throws Exception {
            anotarALas(6, pacienteUno, consulta, draA);
            anotarALas(7, pacienteDos, consulta, null);

            JsonNode lista = cuerpo(comoPacienteUno().get(PACIENTE).andReturn());

            assertThat(lista).hasSize(1);
            assertThat(lista.get(0).get("prestacionNombre").asText()).isEqualTo("Consulta");
            assertThat(lista.get(0).get("pacienteDni").isNull()).isTrue();
        }

        @Test
        void laRecepcionVeLaListaEnOrden() throws Exception {
            anotarALas(6, pacienteDos, consulta, null);
            anotarALas(7, pacienteUno, consulta, draA);

            JsonNode lista = cuerpo(comoRecepcion().get(PERSONAL).andReturn());

            assertThat(lista).hasSize(2);
            assertThat(lista.get(0).get("pacienteId").asLong()).isEqualTo(pacienteDos);
            assertThat(lista.get(0).get("pacienteNombre").asText()).isEqualTo("Paciente Dos");
            assertThat(lista.get(1).get("pacienteId").asLong()).isEqualTo(pacienteUno);
        }

        @Test
        void bajaConUnaOfertaPendiente() throws Exception {
            anotarALas(8, pacienteDos, consulta, null);
            ofertaParaUno();
            long inscripcion = id("SELECT id FROM inscripcion_lista_espera WHERE paciente_id = ?", pacienteUno);

            MvcResult resultado = comoPacienteUno().post(PACIENTE + "/" + inscripcion + "/baja", null).andReturn();

            assertThat(resultado.getResponse().getStatus()).isEqualTo(204);
            assertThat(estadoInscripcionDe(pacienteUno)).isEqualTo("BAJA");
            assertThat(ofertas()).extracting(o -> o.get("paciente_id") + ":" + o.get("estado"))
                    .containsExactly(pacienteUno + ":RECHAZADA", pacienteDos + ":PENDIENTE");
        }

        @Test
        void bajaDeUnaInscripcionAjena() throws Exception {
            anotarALas(7, pacienteDos, consulta, null);
            long ajena = id("SELECT id FROM inscripcion_lista_espera WHERE paciente_id = ?", pacienteDos);

            MvcResult resultado = comoPacienteUno().post(PACIENTE + "/" + ajena + "/baja", null).andReturn();

            assertThat(resultado.getResponse().getStatus()).isEqualTo(404);
            assertThat(cuerpo(resultado).get("codigo").asText()).isEqualTo("RECURSO_NO_ENCONTRADO");
            assertThat(estadoInscripcionDe(pacienteDos)).isEqualTo("ACTIVA");
        }

        @Test
        void laRecepcionDaDeBajaYUnaInscripcionYaCerradaNoSeRepite() throws Exception {
            anotarALas(7, pacienteDos, consulta, null);
            long inscripcion = id("SELECT id FROM inscripcion_lista_espera WHERE paciente_id = ?", pacienteDos);
            Navegador recepcion = comoRecepcion();

            assertThat(recepcion.post(PERSONAL + "/" + inscripcion + "/baja", null).andReturn().getResponse()
                    .getStatus()).isEqualTo(204);
            MvcResult otraVez = recepcion.post(PERSONAL + "/" + inscripcion + "/baja", null).andReturn();

            assertThat(otraVez.getResponse().getStatus()).isEqualTo(409);
            assertThat(cuerpo(otraVez).get("codigo").asText()).isEqualTo("INSCRIPCION_NO_ACTIVA");
        }
    }

    // ---------- acceso y privacidad ----------

    @Nested
    class AccesoYPrivacidad {

        @Test
        void elOdontologoNoAccede() throws Exception {
            Navegador ana = iniciarSesion("ana", clave);

            for (String ruta : List.of(PERSONAL, PLAZO)) {
                MvcResult resultado = ana.get(ruta).andReturn();
                assertThat(resultado.getResponse().getStatus()).isEqualTo(403);
                assertThat(cuerpo(resultado).get("codigo").asText()).isEqualTo("ACCESO_DENEGADO");
            }
        }

        @Test
        void elPacienteNoAccedeALaListaCompleta() throws Exception {
            MvcResult resultado = comoPacienteUno().get(PERSONAL).andReturn();

            assertThat(resultado.getResponse().getStatus()).isEqualTo(403);
            assertThat(cuerpo(resultado).get("codigo").asText()).isEqualTo("ACCESO_DENEGADO");
        }

        @Test
        void sinSesion() throws Exception {
            assertThat(navegador().get(PACIENTE).andReturn().getResponse().getStatus()).isEqualTo(401);
            assertThat(navegador().get(PERSONAL).andReturn().getResponse().getStatus()).isEqualTo(401);
        }

        @Test
        void escribirSinCsrfSeRechaza() throws Exception {
            MvcResult resultado = comoPacienteUno().postSinEncabezadoCsrf(PACIENTE,
                    cuerpoJson(pedido(consulta, draA, 1, 15))).andReturn();

            assertThat(resultado.getResponse().getStatus()).isEqualTo(403);
            assertThat(id("SELECT count(*) FROM inscripcion_lista_espera")).isZero();
        }

        @Test
        void elTokenNoSeGuardaEnClaro() throws Exception {
            String token = ofertaParaUno();

            String guardado = (String) pendientes().get(0).get("token_hash");

            assertThat(guardado).isEqualTo(HuellaToken.de(token)).isNotEqualTo(token);
            assertThat(id("SELECT count(*) FROM oferta_lista_espera WHERE token_hash = ?", token)).isZero();
        }
    }
}
