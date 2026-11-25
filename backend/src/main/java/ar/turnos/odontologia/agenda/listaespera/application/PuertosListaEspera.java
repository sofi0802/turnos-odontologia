package ar.turnos.odontologia.agenda.listaespera.application;

import ar.turnos.odontologia.agenda.listaespera.domain.PlazoOferta;
import java.time.Instant;
import java.util.Optional;

/** Puertos chicos de la lista de espera (C-15). */
public final class PuertosListaEspera {

    private PuertosListaEspera() {}

    /** El consentimiento de comunicaciones del paciente (RN-NO-03), o vacío si el paciente no existe. */
    public interface Pacientes {
        Optional<Boolean> consentimiento(Long pacienteId);
    }

    /** El plazo para aceptar (RN-LE-05), guardado en {@code configuracion}. */
    public interface PlazoVigente {
        PlazoOferta actual();

        void guardar(PlazoOferta plazo);
    }

    /** Tokens de 256 bits en base64url y su huella SHA-256, como la confirmación de C-08 (design D5). */
    public interface Tokens {
        String generar();

        String huella(String token);
    }

    /**
     * Aviso de la oferta por el canal del paciente (RN-LE-08): lo implementa {@code notificaciones}. Sin la prestación
     * ni nombres; el token solo viaja en el link.
     *
     * @return {@code true} si salió por algún canal
     */
    public interface AvisoOferta {
        boolean avisar(Long pacienteId, Instant inicio, Instant venceEn, String token);
    }
}
