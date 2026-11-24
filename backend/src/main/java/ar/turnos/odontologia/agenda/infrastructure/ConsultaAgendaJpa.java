package ar.turnos.odontologia.agenda.infrastructure;

import ar.turnos.odontologia.agenda.application.ConsultaAgenda;
import ar.turnos.odontologia.agenda.domain.EstadoTurno;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Adaptador JPA de {@link ConsultaAgenda}. Los filtros por profesional y sillón se aplican en memoria: un
 * consultorio chico tiene pocas decenas de turnos por día, y así la consulta no depende de parámetros nulos.
 */
@Repository
class ConsultaAgendaJpa implements ConsultaAgenda {

    private final TurnoJpaRepository turnos;

    ConsultaAgendaJpa(TurnoJpaRepository turnos) {
        this.turnos = turnos;
    }

    @Override
    public List<TurnoEnAgenda> turnosDeAgenda(Instant desde, Instant hasta, Long profesionalId, Long sillonId) {
        return turnos.deAgendaDelPeriodo(desde, hasta).stream()
                .filter(t -> profesionalId == null || profesionalId.equals(t.getProfesional().getId()))
                .filter(t -> sillonId == null || sillonId.equals(t.getSillon().getId()))
                .map(t -> new TurnoEnAgenda(t.getId(), t.getPaciente().getId(), t.getPaciente().getNombre(),
                        t.getPaciente().getApellido(), t.getProfesional().getId(), t.getSillon().getId(),
                        t.getPrestacion().getId(), t.getInicio(), t.getFin(), EstadoTurno.valueOf(t.getEstado()),
                        t.isEsSobreturno(), t.getLlegadaEn()))
                .toList();
    }
}
