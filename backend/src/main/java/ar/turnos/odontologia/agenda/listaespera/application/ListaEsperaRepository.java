package ar.turnos.odontologia.agenda.listaespera.application;

import ar.turnos.odontologia.agenda.listaespera.domain.EstadoInscripcion;
import ar.turnos.odontologia.agenda.listaespera.domain.EstadoOferta;
import ar.turnos.odontologia.agenda.listaespera.domain.Inscripcion;
import ar.turnos.odontologia.agenda.listaespera.domain.Oferta;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistencia de la lista de espera (C-15). Las lecturas "para actualizar" bloquean la fila hasta el commit. */
public interface ListaEsperaRepository {

    // ---------- inscripciones ----------

    boolean existeActiva(Long pacienteId, Long prestacionId);

    /** @throws InscripcionDuplicadaException si otra inscripción activa ganó la carrera (índice único parcial) */
    Inscripcion insertar(Inscripcion inscripcion, String actor);

    Optional<Inscripcion> inscripcion(Long id);

    Optional<Inscripcion> inscripcionParaActualizar(Long id);

    void cerrarInscripcion(Long id, EstadoInscripcion estado, Instant ahora);

    /** Las inscripciones activas cuyo último día es anterior a {@code hoy} pasan a {@code VENCIDA}. */
    int vencerInscripciones(LocalDate hoy, Instant ahora);

    /** Todas las activas, en orden de llegada, con lo que muestra la recepción. */
    List<FilaInscripcion> activas();

    /** Las activas de un paciente, en orden de llegada. */
    List<FilaInscripcion> activasDe(Long pacienteId);

    /**
     * Inscripciones activas que podrían recibir el horario (RN-LE-03, condiciones 1, 2 y 4), en orden de llegada:
     * la fecha en su rango, ese profesional o "sin preferencia", sin oferta pendiente, sin una oferta anterior para el
     * mismo horario y con consentimiento de comunicaciones. Bloquea las filas y saltea las ya bloqueadas.
     */
    List<Inscripcion> candidatas(Long profesionalId, LocalDate fecha, Long turnoLiberadoId, int limite);

    // ---------- ofertas ----------

    boolean hayPendientePara(Long turnoLiberadoId);

    /** @return la oferta guardada, o vacía si un índice único parcial la rechazó (otra oferta ganó la carrera) */
    Optional<Oferta> insertarOferta(Oferta oferta, String huellaToken, Instant ahora);

    Optional<Oferta> ofertaParaActualizar(String huellaToken);

    Optional<Oferta> ofertaPendienteParaActualizar(Long inscripcionId);

    Optional<VistaOferta> vista(String huellaToken);

    /** Cambia el estado y la fecha de respuesta, sin condiciones: el llamador ya bloqueó y verificó la oferta. */
    void responder(Long ofertaId, EstadoOferta estado, Instant ahora);

    void registrarTurnoCreado(Long ofertaId, Long turnoId);

    /** Marca {@code VENCIDA} cada oferta pendiente con vencimiento cumplido y las devuelve. */
    List<Oferta> vencerOfertas(Instant ahora);

    /** Ofertas pendientes y vigentes cuyo aviso todavía no salió, con menos de {@code maximoIntentos}. */
    List<AvisoPendiente> avisosSinEnviar(Instant ahora, int maximoIntentos);

    /** Guarda el resultado de un intento de aviso; con {@code huellaNueva} reemplaza el token del link. */
    void registrarAviso(Long ofertaId, boolean enviado, String huellaNueva);

    // ---------- tipos ----------

    /** Una fila de la lista: el paciente se muestra a la recepción y al propio paciente, nunca a otros. */
    record FilaInscripcion(Long id, Long pacienteId, String pacienteNombre, String pacienteDni, Long prestacionId,
                           String prestacionNombre, Long profesionalId, String profesionalNombre, LocalDate desde,
                           LocalDate hasta, Instant inscriptaEn, Instant ofertaInicio, Instant ofertaVenceEn) {}

    /** Lo que ve el paciente que abre el link: sin la prestación (RN-LE-08). */
    record VistaOferta(Instant inicio, String profesionalNombre, Instant venceEn, EstadoOferta estado) {}

    /** Una oferta cuyo aviso hay que (re)enviar. */
    record AvisoPendiente(Long ofertaId, Long pacienteId, Instant inicio, Instant venceEn) {}

    /** Otra inscripción activa del mismo paciente y prestación ganó la carrera. */
    class InscripcionDuplicadaException extends RuntimeException {
        public InscripcionDuplicadaException() {
            super("Ya hay una inscripción activa para esa prestación");
        }
    }
}
