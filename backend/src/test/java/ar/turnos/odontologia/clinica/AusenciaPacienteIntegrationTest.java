package ar.turnos.odontologia.clinica;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Un test por escenario de la ausencia del paciente: delta de {@code agenda-turnos} (RN-AG-17) y de
 * {@code auditoria-turnos} (C-13). Los turnos se insertan por JDBC y el reloj se mueve por escenario.
 */
class AusenciaPacienteIntegrationTest extends IntegracionClinicaBase {

    @Test
    @DisplayName("La ausencia libera el resto del horario")
    void laAusenciaLiberaElRestoDelHorario() throws Exception {
        reloj.ahoraEs(diciembre(1, 10, 10));
        long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");

        MvcResult resultado = marcarAusencia(como("ana.ejemplo"), pacienteUno, turno);

        assertThat(status(resultado)).isEqualTo(200);
        assertThat(cuerpo(resultado).path("estado").asText()).isEqualTo("AUSENTE");
        assertThat(estadoDelTurno(turno)).isEqualTo("AUSENTE");
        // se puede crear un turno de "Consulta" con la Dra. A en el Sillón 1 a las 10:15
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("pacienteId", pacienteDos);
        pedido.put("profesionalId", draA);
        pedido.put("sillonId", sillon1);
        pedido.put("prestacionId", consulta);
        pedido.put("inicio", iso(diciembre(1, 10, 15)));
        MvcResult nuevo = como("recepcion").post("/api/v1/turnos", json.writeValueAsString(pedido)).andReturn();
        assertThat(status(nuevo)).as(texto(nuevo)).isEqualTo(201);
    }

    @Test
    @DisplayName("Ausencia en un turno que ya terminó")
    void ausenciaEnUnTurnoQueYaTermino() throws Exception {
        reloj.ahoraEs(diciembre(1, 11, 0));
        long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");

        assertThat(status(marcarAusencia(como("ana.ejemplo"), pacienteUno, turno))).isEqualTo(200);
        assertThat(estadoDelTurno(turno)).isEqualTo("AUSENTE");
    }

    @Test
    @DisplayName("Ausencia antes del inicio")
    void ausenciaAntesDelInicio() throws Exception {
        reloj.ahoraEs(diciembre(1, 9, 50));
        long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");

        MvcResult resultado = marcarAusencia(como("ana.ejemplo"), pacienteUno, turno);

        assertThat(status(resultado)).isEqualTo(409);
        assertThat(codigo(resultado)).isEqualTo("TURNO_NO_INICIADO");
        assertThat(estadoDelTurno(turno)).isEqualTo("RESERVADO");
    }

    @Test
    @DisplayName("Ausencia en un turno cancelado")
    void ausenciaEnUnTurnoCancelado() throws Exception {
        reloj.ahoraEs(diciembre(1, 10, 10));
        long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "CANCELADO");

        MvcResult resultado = marcarAusencia(como("ana.ejemplo"), pacienteUno, turno);

        assertThat(status(resultado)).isEqualTo(409);
        assertThat(codigo(resultado)).isEqualTo("TURNO_NO_ACTIVO");
    }

    @Test
    @DisplayName("Otro odontólogo marca la ausencia")
    void otroOdontologoMarcaLaAusencia() throws Exception {
        reloj.ahoraEs(diciembre(1, 10, 10));
        long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");

        MvcResult resultado = marcarAusencia(como("bruno.prueba"), pacienteUno, turno);

        assertThat(status(resultado)).isEqualTo(403);
        assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
        assertThat(estadoDelTurno(turno)).isEqualTo("RESERVADO");
    }

    @Test
    @DisplayName("La recepción marca la ausencia")
    void laRecepcionMarcaLaAusencia() throws Exception {
        reloj.ahoraEs(diciembre(1, 10, 10));
        long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");

        MvcResult resultado = marcarAusencia(como("recepcion"), pacienteUno, turno);

        assertThat(status(resultado)).isEqualTo(403);
        assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
        assertThat(estadoDelTurno(turno)).isEqualTo("RESERVADO");
        // el administrador tampoco
        assertThat(status(marcarAusencia(como("admin"), pacienteUno, turno))).isEqualTo(403);
    }

    @Test
    @DisplayName("Turno de otro paciente")
    void turnoDeOtroPaciente() throws Exception {
        reloj.ahoraEs(diciembre(1, 10, 10));
        long turno = turno(pacienteDos, draA, diciembre(1, 10, 0), "RESERVADO");

        MvcResult resultado = marcarAusencia(como("ana.ejemplo"), pacienteUno, turno);

        assertThat(status(resultado)).isEqualTo(404);
        assertThat(codigo(resultado)).isEqualTo("TURNO_INEXISTENTE");
        assertThat(estadoDelTurno(turno)).isEqualTo("RESERVADO");
    }

    @Test
    @DisplayName("Se registra la ausencia")
    void seRegistraLaAusencia() throws Exception {
        reloj.ahoraEs(diciembre(1, 10, 10));
        long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");

        marcarAusencia(como("ana.ejemplo"), pacienteUno, turno);

        Map<String, Object> registro = jdbc.queryForMap(
                "SELECT accion, actor, turno_relacionado_id FROM turno_auditoria WHERE turno_id = ?", turno);
        assertThat(registro.get("accion")).isEqualTo("AUSENTE");
        assertThat(registro.get("actor")).isEqualTo("ana.ejemplo");
        assertThat(registro.get("turno_relacionado_id")).isNull();
    }

    @Test
    @DisplayName("Ausencia rechazada sin registro")
    void ausenciaRechazadaSinRegistro() throws Exception {
        reloj.ahoraEs(diciembre(1, 9, 50));
        long turno = turno(pacienteUno, draA, diciembre(1, 10, 0), "RESERVADO");

        MvcResult resultado = marcarAusencia(como("ana.ejemplo"), pacienteUno, turno);

        assertThat(codigo(resultado)).isEqualTo("TURNO_NO_INICIADO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno_auditoria WHERE turno_id = ? AND accion = 'AUSENTE'",
                Integer.class, turno)).isZero();
    }
}
