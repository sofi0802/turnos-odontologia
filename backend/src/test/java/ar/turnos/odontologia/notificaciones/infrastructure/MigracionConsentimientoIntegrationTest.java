package ar.turnos.odontologia.notificaciones.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnos.odontologia.notificaciones.IntegracionNotificacionesBase;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * C-17, tarea 2.1: las restricciones de la migración V11 son la última garantía del historial del consentimiento
 * (design D1). Origen, medio y usuario van juntos solo con {@code RECEPCION}, y el historial no se modifica ni se
 * borra.
 */
class MigracionConsentimientoIntegrationTest extends IntegracionNotificacionesBase {

    private long recepcionId() {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'recepcion'", Long.class);
    }

    private long evento(long paciente, boolean otorgado, String origen, String medio, Long usuario) {
        return jdbc.queryForObject("INSERT INTO consentimiento_comunicacion_evento(paciente_id, otorgado, "
                        + "registrado_en, origen, medio, usuario_id) VALUES (?,?,now(),?,?,?) RETURNING id",
                Long.class, paciente, otorgado, origen, medio, usuario);
    }

    @Test
    void unEventoDeLaRecepcionConMedioYUsuarioSeGuarda() {
        long id = evento(pacienteDos, true, "RECEPCION", "TELEFONO", recepcionId());

        assertThat(jdbc.queryForObject("SELECT medio FROM consentimiento_comunicacion_evento WHERE id = ?",
                String.class, id)).isEqualTo("TELEFONO");
    }

    @Test
    void unEventoDelPacienteSinMedioNiUsuarioSeGuarda() {
        evento(pacienteUno, true, "PACIENTE", null, null);
        evento(pacienteUno, false, "REGISTRO", null, null);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM consentimiento_comunicacion_evento WHERE paciente_id = ?",
                Long.class, pacienteUno)).isEqualTo(2);
    }

    @Test
    void unEventoDeLaRecepcionSinMedioSeRechaza() {
        long recepcion = recepcionId();

        assertThatThrownBy(() -> evento(pacienteDos, true, "RECEPCION", null, recepcion))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unEventoDelPacienteConMedioSeRechaza() {
        assertThatThrownBy(() -> evento(pacienteUno, true, "PACIENTE", "TELEFONO", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unOrigenDesconocidoSeRechaza() {
        assertThatThrownBy(() -> evento(pacienteUno, true, "EMAIL", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void elHistorialNoSeModificaNiSeBorra() {
        long id = evento(pacienteUno, true, "PACIENTE", null, null);

        assertThatThrownBy(() -> jdbc.update("UPDATE consentimiento_comunicacion_evento SET otorgado = false "
                + "WHERE id = ?", id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM consentimiento_comunicacion_evento WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT otorgado FROM consentimiento_comunicacion_evento WHERE id = ?",
                Boolean.class, id)).isTrue();
    }
}
