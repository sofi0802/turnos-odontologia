package ar.turnos.odontologia.agenda.api;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.agenda.infrastructure.ServicioAgenda;
import ar.turnos.odontologia.clinica.IntegracionClinicaBase;
import ar.turnos.odontologia.shared.security.Navegador;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * C-43 registro-asistencia: un test por escenario de los deltas de {@code agenda-turnos}, {@code agenda-api},
 * {@code auditoria-turnos} y {@code ficha-clinica}. PostgreSQL real, sesión y CSRF de verdad, datos ficticios y el reloj
 * ajustable de la base de C-13 (arranca el 2026-12-01 a las 08:00). El turno de "Paciente Uno" es de 10:00 a 10:30
 * con la Dra. A en el Sillón 1 y un margen de 10 minutos. La tarea programada está apagada en el perfil de test: el
 * "proceso programado" de los escenarios es {@link ServicioAgenda#pasarAAtendidos()}.
 */
class AsistenciaIntegrationTest extends IntegracionClinicaBase {

    private static final String TURNOS = "/api/v1/turnos";

    @Autowired
    private ServicioAgenda servicio;

    // ---- agenda-turnos: Marcar la ausencia del paciente -----------------------------------------------------------

    @Nested
    class MarcarLaAusencia {

        @Test
        @DisplayName("La ausencia libera el resto del horario")
        void laAusenciaLiberaElRestoDelHorario() throws Exception {
            reloj.ahoraEs(diciembre(1, 10, 20));
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");

            MvcResult resultado = ausencia(como("recepcion"), turno);

            assertThat(status(resultado)).as(texto(resultado)).isEqualTo(200);
            assertThat(estadoDelTurno(turno)).isEqualTo("AUSENTE");
            MvcResult nuevo = crearTurno(pacienteDos, draA, diciembre(1, 10, 25));
            assertThat(status(nuevo)).as(texto(nuevo)).isEqualTo(201);
        }

        @Test
        @DisplayName("Ausencia en un turno que ya terminó")
        void ausenciaEnUnTurnoQueYaTermino() throws Exception {
            reloj.ahoraEs(diciembre(1, 11, 0));
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");

            MvcResult resultado = marcarAusencia(como("ana.ejemplo"), pacienteUno, turno);

            assertThat(status(resultado)).as(texto(resultado)).isEqualTo(200);
            assertThat(estadoDelTurno(turno)).isEqualTo("AUSENTE");
        }

        @Test
        @DisplayName("Un turno sin registro no pasa solo a ausente")
        void unTurnoSinRegistroNoPasaSoloAAusente() {
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");

            reloj.ahoraEs(diciembre(1, 12, 0));
            servicio.pasarAAtendidos();

            assertThat(estadoDelTurno(turno)).isEqualTo("RESERVADO");
        }
    }

    // ---- agenda-turnos: La ausencia solo después de la tolerancia -------------------------------------------------

    @Nested
    class Tolerancia {

        @Test
        @DisplayName("Ausencia antes del inicio")
        void ausenciaAntesDelInicio() throws Exception {
            reloj.ahoraEs(diciembre(1, 9, 50));
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");

            MvcResult resultado = marcarAusencia(como("ana.ejemplo"), pacienteUno, turno);

            assertThat(status(resultado)).isEqualTo(409);
            assertThat(codigo(resultado)).isEqualTo("AUSENCIA_ANTES_DE_TOLERANCIA");
            assertThat(estadoDelTurno(turno)).isEqualTo("RESERVADO");
        }

        @Test
        @DisplayName("Ausencia dentro de la tolerancia")
        void ausenciaDentroDeLaTolerancia() throws Exception {
            reloj.ahoraEs(diciembre(1, 10, 14));
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");

            MvcResult resultado = ausencia(como("recepcion"), turno);

            assertThat(status(resultado)).isEqualTo(409);
            assertThat(codigo(resultado)).isEqualTo("AUSENCIA_ANTES_DE_TOLERANCIA");
            assertThat(estadoDelTurno(turno)).isEqualTo("RESERVADO");
        }

        @Test
        @DisplayName("Ausencia al cumplirse la tolerancia")
        void ausenciaAlCumplirseLaTolerancia() throws Exception {
            reloj.ahoraEs(diciembre(1, 10, 15));
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");

            MvcResult resultado = ausencia(como("recepcion"), turno);

            assertThat(status(resultado)).as(texto(resultado)).isEqualTo(200);
            assertThat(estadoDelTurno(turno)).isEqualTo("AUSENTE");
        }
    }

    // ---- agenda-turnos: Quién marca la ausencia ------------------------------------------------------------------

    @Nested
    class QuienMarcaLaAusencia {

        @Test
        @DisplayName("Otro odontólogo marca la ausencia")
        void otroOdontologoMarcaLaAusencia() throws Exception {
            reloj.ahoraEs(diciembre(1, 10, 20));
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");

            MvcResult resultado = marcarAusencia(como("bruno.prueba"), pacienteUno, turno);

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(estadoDelTurno(turno)).isEqualTo("RESERVADO");
        }

        @Test
        @DisplayName("La recepción marca la ausencia desde la ficha clínica")
        void laRecepcionMarcaLaAusenciaDesdeLaFicha() throws Exception {
            reloj.ahoraEs(diciembre(1, 10, 20));
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");

            MvcResult resultado = marcarAusencia(como("recepcion"), pacienteUno, turno);

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(estadoDelTurno(turno)).isEqualTo("RESERVADO");
        }

        @Test
        @DisplayName("El administrador marca la ausencia")
        void elAdministradorMarcaLaAusencia() throws Exception {
            reloj.ahoraEs(diciembre(1, 10, 20));
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");

            MvcResult resultado = ausencia(como("admin"), turno);

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(estadoDelTurno(turno)).isEqualTo("RESERVADO");
        }

        @Test
        @DisplayName("Ausencia de un paciente que llegó")
        void ausenciaDeUnPacienteQueLlego() throws Exception {
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");
            reloj.ahoraEs(diciembre(1, 10, 5));
            assertThat(status(llegada(como("recepcion"), turno))).isEqualTo(200);

            reloj.ahoraEs(diciembre(1, 10, 20));
            MvcResult resultado = ausencia(como("recepcion"), turno);

            assertThat(status(resultado)).isEqualTo(409);
            assertThat(codigo(resultado)).isEqualTo("LLEGADA_REGISTRADA");
            assertThat(estadoDelTurno(turno)).isEqualTo("RESERVADO");
        }
    }

    // ---- agenda-turnos: Llegada del paciente y sus rechazos --------------------------------------------------------

    @Nested
    class Llegada {

        @Test
        @DisplayName("Llegada tarde")
        void llegadaTarde() throws Exception {
            reloj.ahoraEs(diciembre(1, 10, 12));
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");

            MvcResult resultado = llegada(como("recepcion"), turno);

            assertThat(status(resultado)).as(texto(resultado)).isEqualTo(200);
            assertThat(estadoDelTurno(turno)).isEqualTo("RESERVADO");
            assertThat(llegadaEn(turno)).isEqualTo(diciembre(1, 10, 12));
            MvcResult nuevo = crearTurno(pacienteDos, draA, diciembre(1, 10, 20));
            assertThat(status(nuevo)).isEqualTo(409);
            assertThat(codigo(nuevo)).isEqualTo("SOLAPAMIENTO_PROFESIONAL");
        }

        @Test
        @DisplayName("El administrador registra una llegada")
        void elAdministradorRegistraUnaLlegada() throws Exception {
            reloj.ahoraEs(diciembre(1, 10, 5));
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");

            MvcResult resultado = llegada(como("admin"), turno);

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(llegadaEn(turno)).isNull();
        }

        @Test
        @DisplayName("Llegada antes del día del turno")
        void llegadaAntesDelDiaDelTurno() throws Exception {
            reloj.ahoraEs(LocalDateTime.of(2026, 11, 30, 18, 0).atZone(BUENOS_AIRES).toInstant());
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");

            MvcResult resultado = llegada(como("recepcion"), turno);

            assertThat(status(resultado)).isEqualTo(409);
            assertThat(codigo(resultado)).isEqualTo("LLEGADA_ANTICIPADA");
            assertThat(llegadaEn(turno)).isNull();
        }

        @Test
        @DisplayName("Llegada a un turno ausente")
        void llegadaAUnTurnoAusente() throws Exception {
            reloj.ahoraEs(diciembre(1, 10, 20));
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "AUSENTE");

            MvcResult resultado = llegada(como("recepcion"), turno);

            assertThat(status(resultado)).isEqualTo(409);
            assertThat(codigo(resultado)).isEqualTo("TURNO_NO_ACTIVO");
            assertThat(estadoDelTurno(turno)).isEqualTo("AUSENTE");
        }

        @Test
        @DisplayName("Segunda llegada")
        void segundaLlegada() throws Exception {
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");
            reloj.ahoraEs(diciembre(1, 10, 5));
            llegada(como("recepcion"), turno);

            reloj.ahoraEs(diciembre(1, 10, 6));
            MvcResult resultado = llegada(como("recepcion"), turno);

            assertThat(status(resultado)).isEqualTo(409);
            assertThat(codigo(resultado)).isEqualTo("LLEGADA_REGISTRADA");
            assertThat(llegadaEn(turno)).isEqualTo(diciembre(1, 10, 5));
        }
    }

    // ---- agenda-turnos: Paso automático a atendido -------------------------------------------------------------

    @Nested
    class PasoAAtendido {

        @Test
        @DisplayName("El turno con llegada pasa a atendido")
        void elTurnoConLlegadaPasaAAtendido() throws Exception {
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");
            reloj.ahoraEs(diciembre(1, 10, 5));
            llegada(como("recepcion"), turno);

            reloj.ahoraEs(diciembre(1, 10, 41));
            servicio.pasarAAtendidos();

            assertThat(estadoDelTurno(turno)).isEqualTo("ATENDIDO");
        }

        @Test
        @DisplayName("El margen sigue reservado")
        void elMargenSigueReservado() throws Exception {
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");
            reloj.ahoraEs(diciembre(1, 10, 5));
            llegada(como("recepcion"), turno);

            reloj.ahoraEs(diciembre(1, 10, 35));
            servicio.pasarAAtendidos();

            assertThat(estadoDelTurno(turno)).isEqualTo("RESERVADO");
            MvcResult nuevo = crearTurno(pacienteDos, drB, diciembre(1, 10, 36));
            assertThat(status(nuevo)).isEqualTo(409);
            assertThat(codigo(nuevo)).isEqualTo("MARGEN_INSUFICIENTE");
        }

        @Test
        @DisplayName("Corrección de un turno sin registro")
        void correccionDeUnTurnoSinRegistro() throws Exception {
            reloj.ahoraEs(diciembre(1, 12, 0));
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");

            MvcResult resultado = llegada(como("recepcion"), turno);

            assertThat(status(resultado)).as(texto(resultado)).isEqualTo(200);
            assertThat(estadoDelTurno(turno)).isEqualTo("ATENDIDO");
        }
    }

    // ---- agenda-turnos: Deshacer la ausencia ----------------------------------------------------------------------

    @Nested
    class DeshacerLaAusencia {

        @Test
        @DisplayName("Deshacer con el horario libre")
        void deshacerConElHorarioLibre() throws Exception {
            reloj.ahoraEs(diciembre(1, 10, 22));
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "AUSENTE");
            auditar(turno, "CONFIRMAR", "paciente-token", diciembre(1, 8, 0));

            MvcResult resultado = deshacer(como("recepcion"), turno);

            assertThat(status(resultado)).as(texto(resultado)).isEqualTo(200);
            assertThat(cuerpo(resultado).path("estado").asText()).isEqualTo("CONFIRMADO");
            assertThat(estadoDelTurno(turno)).isEqualTo("CONFIRMADO");
        }

        @Test
        @DisplayName("El odontólogo intenta deshacer la ausencia")
        void elOdontologoIntentaDeshacer() throws Exception {
            reloj.ahoraEs(diciembre(1, 10, 22));
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "AUSENTE");

            MvcResult resultado = deshacer(como("ana.ejemplo"), turno);

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(estadoDelTurno(turno)).isEqualTo("AUSENTE");
        }

        @Test
        @DisplayName("Deshacer con el horario ocupado")
        void deshacerConElHorarioOcupado() throws Exception {
            reloj.ahoraEs(diciembre(1, 10, 22));
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "AUSENTE");
            sobreturnoDelDrB(diciembre(1, 10, 20), diciembre(1, 10, 40));

            MvcResult resultado = deshacer(como("recepcion"), turno);

            assertThat(status(resultado)).isEqualTo(409);
            assertThat(codigo(resultado)).isEqualTo("SOLAPAMIENTO_SILLON");
            assertThat(estadoDelTurno(turno)).isEqualTo("AUSENTE");
        }

        @Test
        @DisplayName("Deshacer después del fin")
        void deshacerDespuesDelFin() throws Exception {
            reloj.ahoraEs(diciembre(1, 10, 31));
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "AUSENTE");

            MvcResult resultado = deshacer(como("recepcion"), turno);

            assertThat(status(resultado)).isEqualTo(409);
            assertThat(codigo(resultado)).isEqualTo("TURNO_TERMINADO");
            assertThat(estadoDelTurno(turno)).isEqualTo("AUSENTE");
        }

        @Test
        @DisplayName("Deshacer un turno que no está ausente")
        void deshacerUnTurnoQueNoEstaAusente() throws Exception {
            reloj.ahoraEs(diciembre(1, 10, 22));
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");

            MvcResult resultado = deshacer(como("recepcion"), turno);

            assertThat(status(resultado)).isEqualTo(409);
            assertThat(codigo(resultado)).isEqualTo("TURNO_NO_AUSENTE");
        }
    }

    // ---- agenda-api ---------------------------------------------------------------------------------------------

    @Nested
    class Api {

        @Test
        @DisplayName("La agenda incluye los atendidos y los ausentes")
        void laAgendaIncluyeLosAtendidosYLosAusentes() throws Exception {
            reloj.ahoraEs(diciembre(1, 12, 0));
            long atendido = turno(pacienteUno, draA, diciembre(1, 9, 0), "ATENDIDO");
            jdbc.update("UPDATE turno SET llegada_en = ? WHERE id = ?", Timestamp.from(diciembre(1, 8, 55)), atendido);
            long ausente = turno(pacienteUno, draA, diciembre(1, 10, 0), "AUSENTE");
            turno(pacienteDos, draA, diciembre(1, 11, 0), "CANCELADO");

            MvcResult resultado = como("recepcion").get(TURNOS + "?desde=" + iso(diciembre(1, 0, 0)).replace("+", "%2B")
                    + "&hasta=" + iso(diciembre(1, 23, 59)).replace("+", "%2B")).andReturn();

            assertThat(status(resultado)).as(texto(resultado)).isEqualTo(200);
            JsonNode turnos = cuerpo(resultado);
            assertThat(turnos).hasSize(2);
            assertThat(turnos.get(0).path("id").asLong()).isEqualTo(atendido);
            assertThat(turnos.get(0).path("estado").asText()).isEqualTo("ATENDIDO");
            assertThat(turnos.get(0).path("llegadaEn").asText()).isEqualTo(iso(diciembre(1, 8, 55)));
            assertThat(turnos.get(1).path("id").asLong()).isEqualTo(ausente);
            assertThat(turnos.get(1).path("estado").asText()).isEqualTo("AUSENTE");
            assertThat(turnos.get(1).path("llegadaEn").isNull()).isTrue();
        }

        @Test
        @DisplayName("La recepción registra la llegada")
        void laRecepcionRegistraLaLlegada() throws Exception {
            reloj.ahoraEs(diciembre(1, 10, 5));
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");

            MvcResult resultado = llegada(como("recepcion"), turno);

            assertThat(status(resultado)).as(texto(resultado)).isEqualTo(200);
            assertThat(cuerpo(resultado).path("estado").asText()).isEqualTo("RESERVADO");
            assertThat(cuerpo(resultado).path("llegadaEn").asText()).isEqualTo(iso(diciembre(1, 10, 5)));
        }

        @Test
        @DisplayName("Ausencia antes de la tolerancia por la API")
        void ausenciaAntesDeLaToleranciaPorLaApi() throws Exception {
            reloj.ahoraEs(diciembre(1, 10, 10));
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");

            MvcResult resultado = ausencia(como("recepcion"), turno);

            assertThat(status(resultado)).isEqualTo(409);
            assertThat(codigo(resultado)).isEqualTo("AUSENCIA_ANTES_DE_TOLERANCIA");
        }

        @Test
        @DisplayName("El odontólogo usa la ausencia de la agenda")
        void elOdontologoUsaLaAusenciaDeLaAgenda() throws Exception {
            reloj.ahoraEs(diciembre(1, 10, 20));
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");

            MvcResult resultado = ausencia(como("ana.ejemplo"), turno);

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(estadoDelTurno(turno)).isEqualTo("RESERVADO");
        }

        @Test
        @DisplayName("Deshacer la ausencia de un turno inexistente")
        void deshacerLaAusenciaDeUnTurnoInexistente() throws Exception {
            MvcResult resultado = deshacer(como("recepcion"), 999999L);

            assertThat(status(resultado)).isEqualTo(404);
            assertThat(codigo(resultado)).isEqualTo("TURNO_INEXISTENTE");
        }
    }

    // ---- auditoria-turnos ---------------------------------------------------------------------------------------

    @Nested
    class Auditoria {

        @Test
        @DisplayName("Se registran la llegada y el paso a atendido")
        void seRegistranLaLlegadaYElPasoAAtendido() throws Exception {
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");
            reloj.ahoraEs(diciembre(1, 10, 5));
            llegada(como("recepcion"), turno);
            reloj.ahoraEs(diciembre(1, 10, 41));
            servicio.pasarAAtendidos();

            assertThat(registros(turno)).containsExactly(
                    Map.of("accion", "LLEGADA", "actor", "recepcion"),
                    Map.of("accion", "ATENDIDO", "actor", "sistema"));
        }

        @Test
        @DisplayName("Se registra el deshacer de la ausencia")
        void seRegistraElDeshacerDeLaAusencia() throws Exception {
            reloj.ahoraEs(diciembre(1, 10, 22));
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "AUSENTE");

            deshacer(como("recepcion"), turno);

            assertThat(registros(turno)).containsExactly(Map.of("accion", "DESHACER_AUSENCIA", "actor", "recepcion"));
        }

        @Test
        @DisplayName("Deshacer rechazado sin registro")
        void deshacerRechazadoSinRegistro() throws Exception {
            reloj.ahoraEs(diciembre(1, 10, 22));
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "AUSENTE");
            sobreturnoDelDrB(diciembre(1, 10, 20), diciembre(1, 10, 40));

            assertThat(status(deshacer(como("recepcion"), turno))).isEqualTo(409);

            assertThat(registros(turno)).isEmpty();
        }
    }

    // ---- ficha-clinica --------------------------------------------------------------------------------------------

    @Nested
    class Ficha {

        @Test
        @DisplayName("La ficha muestra la ausencia deshecha")
        void laFichaMuestraLaAusenciaDeshecha() throws Exception {
            reloj.ahoraEs(diciembre(1, 10, 22));
            long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "AUSENTE");
            assertThat(status(deshacer(como("recepcion"), turno))).isEqualTo(200);

            JsonNode ausencias = cuerpo(como("ana.ejemplo").get(ficha(pacienteUno)).andReturn()).path("ausencias");

            assertThat(ausencias).hasSize(1);
            assertThat(ausencias.get(0).path("turnoId").asLong()).isEqualTo(turno);
            assertThat(ausencias.get(0).path("inicio").asText()).isEqualTo(iso(diciembre(1, 10, 0)));
            assertThat(ausencias.get(0).path("profesional").asText()).isEqualTo("Ana Ejemplo");
            assertThat(ausencias.get(0).path("deshechaPor").asText()).isEqualTo("recepcion");
            assertThat(ausencias.get(0).path("deshechaEn").asText()).isEqualTo(iso(diciembre(1, 10, 22)));
        }

        @Test
        @DisplayName("Una ausencia vigente no aparece como deshecha")
        void unaAusenciaVigenteNoApareceComoDeshecha() throws Exception {
            turno(pacienteUno, draA, diciembre(1, 10, 0), "AUSENTE");

            JsonNode ausencias = cuerpo(como("ana.ejemplo").get(ficha(pacienteUno)).andReturn()).path("ausencias");

            assertThat(ausencias).hasSize(1);
            assertThat(ausencias.get(0).path("deshechaPor").isNull()).isTrue();
            assertThat(ausencias.get(0).path("deshechaEn").isNull()).isTrue();
        }
    }

    // ---- ayudas -------------------------------------------------------------------------------------------------

    private MvcResult llegada(Navegador navegador, long turno) throws Exception {
        return navegador.post(TURNOS + "/" + turno + "/llegada", null).andReturn();
    }

    private MvcResult ausencia(Navegador navegador, long turno) throws Exception {
        return navegador.post(TURNOS + "/" + turno + "/ausencia", null).andReturn();
    }

    private MvcResult deshacer(Navegador navegador, long turno) throws Exception {
        return navegador.post(TURNOS + "/" + turno + "/deshacer-ausencia", null).andReturn();
    }

    private MvcResult crearTurno(long paciente, long profesional, Instant inicio) throws Exception {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("pacienteId", paciente);
        pedido.put("profesionalId", profesional);
        pedido.put("sillonId", sillon1);
        pedido.put("prestacionId", consulta);
        pedido.put("inicio", iso(inicio));
        return como("recepcion").post(TURNOS, json.writeValueAsString(pedido)).andReturn();
    }

    private Instant llegadaEn(long turno) {
        Timestamp llegada = jdbc.queryForObject("SELECT llegada_en FROM turno WHERE id = ?", Timestamp.class, turno);
        return llegada == null ? null : llegada.toInstant();
    }

    /** Un sobreturno activo de "Paciente Dos" con el Dr. B en el Sillón 1 (no choca por profesional con la Dra. A). */
    private void sobreturnoDelDrB(Instant inicio, Instant fin) {
        jdbc.update("INSERT INTO turno(profesional_id, sillon_id, prestacion_id, paciente_id, inicio, fin, fin_con_margen,"
                        + " estado, es_sobreturno, motivo_sobreturno) VALUES (?,?,?,?,?,?,?,'RESERVADO',true,'Urgencia ficticia')",
                drB, sillon1, consulta, pacienteDos, Timestamp.from(inicio), Timestamp.from(fin),
                Timestamp.from(fin.plusSeconds(10 * 60)));
    }

    private void auditar(long turno, String accion, String actor, Instant fecha) {
        jdbc.update("INSERT INTO turno_auditoria(turno_id, accion, actor, fecha) VALUES (?,?,?,?)",
                turno, accion, actor, Timestamp.from(fecha));
    }

    private List<Map<String, Object>> registros(long turno) {
        return jdbc.queryForList("SELECT accion, actor FROM turno_auditoria WHERE turno_id = ? ORDER BY id", turno);
    }
}
