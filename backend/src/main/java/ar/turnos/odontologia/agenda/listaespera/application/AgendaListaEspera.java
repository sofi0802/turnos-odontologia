package ar.turnos.odontologia.agenda.listaespera.application;

import java.time.Instant;

/**
 * Lo que la lista de espera le pide a la agenda (design D3, D4): las mismas reglas y la misma asignación de sillón
 * que la reserva online, sin duplicarlas. Lo implementa un adaptador sobre {@code reserva}.
 */
public interface AgendaListaEspera {

    /**
     * Valida la prestación y el profesional elegido ({@code null} = "sin preferencia").
     *
     * @throws ar.turnos.odontologia.agenda.listaespera.domain.ListaEsperaException {@code RECURSO_NO_ENCONTRADO},
     *     {@code RECURSO_INACTIVO} o {@code PROFESIONAL_NO_HABILITADO}
     */
    void validarPreferencia(Long prestacionId, Long profesionalId);

    /** Si un turno de esa prestación, con ese profesional y ese inicio, cumple hoy todas las reglas de la agenda. */
    boolean hayLugar(Long prestacionId, Long profesionalId, Instant inicio);

    /**
     * Crea el turno {@code RESERVADO} como la reserva online, con un sillón libre y el actor dado.
     *
     * @throws ar.turnos.odontologia.agenda.listaespera.domain.ListaEsperaException {@code OFERTA_NO_DISPONIBLE} si
     *     el horario ya no cumple las reglas
     */
    TurnoReservado reservar(Long pacienteId, Long prestacionId, Long profesionalId, Instant inicio, String actor);

    record TurnoReservado(Long turnoId, String numeroReserva, Instant inicio, String profesionalNombre) {}
}
