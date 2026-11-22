package ar.turnos.odontologia.reserva.domain;

import ar.turnos.odontologia.agenda.domain.Agenda;
import ar.turnos.odontologia.agenda.domain.Intervalo;
import ar.turnos.odontologia.agenda.domain.NumeroReserva;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.agenda.domain.TurnoRechazadoException;
import ar.turnos.odontologia.agenda.domain.reglas.PeriodosProfesional;
import ar.turnos.odontologia.agenda.domain.reglas.ReglasDisponibilidad;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Decide qué sillones están libres para un profesional en un intervalo (design D2). No reescribe ninguna comparación:
 * pregunta a {@link ReglasDisponibilidad} (franjas, ausencias y bloqueos) y a {@link Agenda} (solapamientos y margen)
 * con un turno candidato, y trata su rechazo como "no está libre".
 */
public final class CalculadoraHuecos {

    /** Número descartable del turno candidato: nunca se guarda. */
    private static final NumeroReserva NUMERO_CANDIDATO = NumeroReserva.deSimbolos("AAAAAAAA");
    /** Paciente descartable del turno candidato: las reglas de conflicto no miran al paciente. */
    private static final Long PACIENTE_CANDIDATO = 0L;

    private final Agenda agenda;
    private final ZoneId zona;

    public CalculadoraHuecos(Agenda agenda, ZoneId zona) {
        this.agenda = Objects.requireNonNull(agenda, "agenda");
        this.zona = Objects.requireNonNull(zona, "zona");
    }

    /**
     * @param turnos los turnos del día del profesional y de los sillones, en cualquier estado
     * @param turnoIgnoradoId el turno original de una reprogramación, o {@code null}
     * @return los sillones libres, en el mismo orden de {@code sillonesEnOrden}; vacío si el profesional no está
     *     libre o no queda ningún sillón
     */
    public List<Long> sillonesLibres(Intervalo intervalo, Long profesionalId, Long prestacionId,
                                     PeriodosProfesional periodos, List<Long> sillonesEnOrden,
                                     Collection<Turno> turnos, Long turnoIgnoradoId) {
        try {
            ReglasDisponibilidad.verificar(intervalo, periodos, zona, true);
        } catch (TurnoRechazadoException e) {
            return List.of();
        }
        List<Turno> relevantes = turnos.stream()
                .filter(t -> turnoIgnoradoId == null || !turnoIgnoradoId.equals(t.id()))
                .toList();
        List<Long> libres = new ArrayList<>();
        for (Long sillonId : sillonesEnOrden) {
            Turno candidato = Turno.reservar(PACIENTE_CANDIDATO, profesionalId, sillonId, prestacionId, intervalo,
                    NUMERO_CANDIDATO);
            try {
                agenda.verificar(candidato, relevantes);
                libres.add(sillonId);
            } catch (TurnoRechazadoException e) {
                // el sillón no está libre con margen para este profesional
            }
        }
        return List.copyOf(libres);
    }
}
