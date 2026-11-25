package ar.turnos.odontologia.agenda.listaespera;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnos.odontologia.reserva.IntegracionReservaBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * C-15, tarea 1.3: las restricciones de la migración V9 son la última garantía. Los índices únicos parciales
 * rechazan una segunda inscripción activa por paciente y prestación, y una segunda oferta pendiente por horario.
 */
class MigracionListaEsperaIntegrationTest extends IntegracionReservaBase {

    private long turnoLiberado;

    @BeforeEach
    void turnoCancelado() {
        turnoLiberado = turnoCancelado(pacienteDos, draA, sillon1, 3, 10, 0);
    }

    private long inscripcion(long paciente, String estado) {
        return id("INSERT INTO inscripcion_lista_espera(paciente_id, prestacion_id, profesional_id, desde, hasta, "
                + "estado, inscripta_en, inscripta_por) VALUES (?,?,?,DATE '2026-12-01', DATE '2026-12-15',?,now(),"
                + "'test') RETURNING id", paciente, consulta, draA, estado);
    }

    private void oferta(long inscripcion, String huella) {
        jdbc.update("INSERT INTO oferta_lista_espera(inscripcion_id, turno_liberado_id, profesional_id, inicio, "
                + "vence_en, estado, token_hash, ofrecida_en) VALUES (?,?,?,now() + interval '2 days', "
                + "now() + interval '2 hours','PENDIENTE',?,now())", inscripcion, turnoLiberado, draA, huella);
    }

    @Test
    void unaSegundaInscripcionActivaSeRechaza() {
        inscripcion(pacienteUno, "ACTIVA");

        assertThatThrownBy(() -> inscripcion(pacienteUno, "ACTIVA"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Una cerrada no cuenta.
        inscripcion(pacienteUno, "BAJA");
        assertThat(id("SELECT count(*) FROM inscripcion_lista_espera WHERE paciente_id = ?", pacienteUno)).isEqualTo(2);
    }

    @Test
    void unaSegundaOfertaPendienteParaElMismoHorarioSeRechaza() {
        oferta(inscripcion(pacienteUno, "ACTIVA"), "huella-1");
        long otra = inscripcion(pacienteDos, "ACTIVA");

        assertThatThrownBy(() -> oferta(otra, "huella-2")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void elRangoNoPuedeSuperarLos60Dias() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO inscripcion_lista_espera(paciente_id, prestacion_id, desde, "
                + "hasta, estado, inscripta_en, inscripta_por) VALUES (?,?,DATE '2026-12-01', DATE '2027-02-15', "
                + "'ACTIVA', now(), 'test')", pacienteUno, consulta))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
