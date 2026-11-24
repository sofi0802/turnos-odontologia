package ar.turnos.odontologia.agenda.infrastructure;

import ar.turnos.odontologia.agenda.application.TurnoRepository;
import ar.turnos.odontologia.agenda.domain.CodigoRechazo;
import ar.turnos.odontologia.agenda.domain.EstadoTurno;
import ar.turnos.odontologia.agenda.domain.Intervalo;
import ar.turnos.odontologia.agenda.domain.Margen;
import ar.turnos.odontologia.agenda.domain.NumeroReserva;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.agenda.domain.TurnoRechazadoException;
import ar.turnos.odontologia.shared.persistence.PacienteEntity;
import ar.turnos.odontologia.shared.persistence.PrestacionEntity;
import ar.turnos.odontologia.shared.persistence.ProfesionalEntity;
import ar.turnos.odontologia.shared.persistence.SillonEntity;
import ar.turnos.odontologia.shared.persistence.TurnoEntity;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * Adaptador JPA de {@link TurnoRepository}. La restricción {@code EXCLUDE} de la base repite la verificación
 * del dominio (DD-03): si salta, se traduce al mismo código de rechazo, así una reserva simultánea nunca
 * termina en un error 500 (design D6).
 */
@Repository
class TurnoRepositoryJpa implements TurnoRepository {

    static final String EXCLUSION_PROFESIONAL = "turno_sin_solapamiento_profesional";
    static final String EXCLUSION_SILLON = "turno_sin_solapamiento_sillon";

    private final TurnoJpaRepository turnos;
    private final EntityManager em;
    private final Margen margen;

    TurnoRepositoryJpa(TurnoJpaRepository turnos, EntityManager em, Margen margen) {
        this.turnos = turnos;
        this.em = em;
        this.margen = margen;
    }

    @Override
    public List<Turno> buscarQueIntersecan(Long profesionalId, Long sillonId, Intervalo ventana) {
        return turnos.queIntersecan(profesionalId, sillonId, ventana.inicio(), ventana.fin()).stream()
                .map(TurnoRepositoryJpa::aDominio)
                .toList();
    }

    @Override
    public Turno guardar(Turno turno) {
        try {
            if (turno.id() == null) {
                TurnoEntity entidad = new TurnoEntity(
                        em.getReference(ProfesionalEntity.class, turno.profesionalId()),
                        em.getReference(SillonEntity.class, turno.sillonId()),
                        em.getReference(PrestacionEntity.class, turno.prestacionId()),
                        em.getReference(PacienteEntity.class, turno.pacienteId()),
                        turno.intervalo().inicio(),
                        turno.intervalo().fin(),
                        turno.intervalo().fin().plus(margen.valor()),
                        turno.numeroReserva().valor());
                if (turno.esSobreturno()) {
                    entidad.marcarSobreturno(turno.motivoSobreturno());
                }
                return turno.conId(turnos.saveAndFlush(entidad).getId());
            }
            TurnoEntity entidad = turnos.findById(turno.id())
                    .orElseThrow(() -> new IllegalStateException("No existe el turno " + turno.id()));
            entidad.cambiarEstado(turno.estado().name());
            entidad.cambiarLlegada(turno.llegadaEn());
            if (turno.extendido()) {
                entidad.actualizarExtension(turno.intervalo().fin(),
                        turno.intervalo().fin().plus(margen.valor()), turno.motivoExtension());
            }
            turnos.saveAndFlush(entidad);
            return turno;
        } catch (DataIntegrityViolationException e) {
            throw traducir(e);
        }
    }

    @Override
    public Optional<TurnoModificable> buscarParaModificar(Long turnoId) {
        return turnos.paraModificar(turnoId)
                .map(t -> new TurnoModificable(aDominio(t), t.getPaciente().getDni()));
    }

    /** Traduce la violación de un {@code EXCLUDE} al rechazo de la agenda; cualquier otra se vuelve a lanzar. */
    static RuntimeException traducir(DataIntegrityViolationException e) {
        String restriccion = nombreDeRestriccion(e);
        if (restriccion.contains(EXCLUSION_PROFESIONAL)) {
            return new TurnoRechazadoException(CodigoRechazo.SOLAPAMIENTO_PROFESIONAL,
                    "El profesional ya tiene un turno en ese horario");
        }
        if (restriccion.contains(EXCLUSION_SILLON)) {
            return new TurnoRechazadoException(CodigoRechazo.SOLAPAMIENTO_SILLON,
                    "El sillón ya está ocupado en ese horario");
        }
        return e;
    }

    private static String nombreDeRestriccion(Throwable e) {
        for (Throwable causa = e; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException cv && cv.getConstraintName() != null) {
                return cv.getConstraintName();
            }
        }
        return String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage());
    }

    static Turno aDominio(TurnoEntity t) {
        return new Turno(t.getId(), t.getPaciente().getId(), t.getProfesional().getId(), t.getSillon().getId(),
                t.getPrestacion().getId(), new Intervalo(t.getInicio(), t.getFin()),
                EstadoTurno.valueOf(t.getEstado()), new NumeroReserva(t.getCodigoReserva()),
                t.isEsSobreturno(), t.getMotivoSobreturno(), t.isExtendido(), t.getMotivoExtension(),
                t.getLlegadaEn());
    }
}
