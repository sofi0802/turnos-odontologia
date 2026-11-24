package ar.turnos.odontologia.agenda.application;

import ar.turnos.odontologia.agenda.domain.EstadoTurno;
import java.time.Instant;
import java.util.List;

/** Puerto de lectura: la agenda del personal. No pasa por el dominio porque no decide nada. */
public interface ConsultaAgenda {

    /**
     * Turnos no cancelados (activos, {@code ATENDIDO} y {@code AUSENTE}, C-43) cuyo intervalo se cruza con
     * {@code [desde, hasta)}, ordenados por inicio. {@code profesionalId} y {@code sillonId} son filtros opcionales
     * (nulos = sin filtro).
     */
    List<TurnoEnAgenda> turnosDeAgenda(Instant desde, Instant hasta, Long profesionalId, Long sillonId);

    /** Turno tal como lo ve el personal en la agenda: sin número de reserva ni DNI. */
    record TurnoEnAgenda(
            Long id,
            Long pacienteId,
            String pacienteNombre,
            String pacienteApellido,
            Long profesionalId,
            Long sillonId,
            Long prestacionId,
            Instant inicio,
            Instant fin,
            EstadoTurno estado,
            boolean esSobreturno,
            Instant llegadaEn) {}
}
