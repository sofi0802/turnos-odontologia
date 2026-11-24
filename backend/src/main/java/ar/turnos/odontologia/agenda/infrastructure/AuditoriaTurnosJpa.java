package ar.turnos.odontologia.agenda.infrastructure;

import ar.turnos.odontologia.agenda.application.AuditoriaTurnos;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import org.springframework.stereotype.Repository;

/** Adaptador JPA de {@link AuditoriaTurnos}: se guarda en la misma transacción que la operación. */
@Repository
class AuditoriaTurnosJpa implements AuditoriaTurnos {

    private final EntityManager em;
    private final Clock reloj;

    AuditoriaTurnosJpa(EntityManager em, Clock reloj) {
        this.em = em;
        this.reloj = reloj;
    }

    @Override
    public void registrar(Long turnoId, Accion accion, String actor, Long turnoRelacionadoId) {
        em.persist(new TurnoAuditoriaEntity(turnoId, accion.name(), actor, reloj.instant(), turnoRelacionadoId));
    }

    @Override
    public boolean registrada(Long turnoId, Accion accion) {
        return !em.createQuery("select a.id from TurnoAuditoriaEntity a where a.turnoId = :turno and a.accion = :accion",
                        Long.class)
                .setParameter("turno", turnoId)
                .setParameter("accion", accion.name())
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
    }
}
