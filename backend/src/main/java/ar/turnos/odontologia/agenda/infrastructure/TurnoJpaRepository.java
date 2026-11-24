package ar.turnos.odontologia.agenda.infrastructure;

import ar.turnos.odontologia.shared.persistence.TurnoEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TurnoJpaRepository extends JpaRepository<TurnoEntity, Long> {

    /** Turnos del profesional o del sillón que se cruzan con {@code [desde, hasta)}, en cualquier estado. */
    @Query("""
            select t from TurnoEntity t
            where (t.profesional.id = :profesionalId or t.sillon.id = :sillonId)
              and t.inicio < :hasta and t.fin > :desde
            """)
    List<TurnoEntity> queIntersecan(@Param("profesionalId") Long profesionalId, @Param("sillonId") Long sillonId,
                                    @Param("desde") Instant desde, @Param("hasta") Instant hasta);

    /** Bloquea la fila hasta el fin de la transacción: dos cancelaciones simultáneas se serializan. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TurnoEntity t join fetch t.paciente where t.id = :id")
    Optional<TurnoEntity> paraModificar(@Param("id") Long id);

    /** Turnos no cancelados del período, con el paciente, para la agenda del personal (C-43, design D7). */
    @Query("""
            select t from TurnoEntity t join fetch t.paciente
            where t.estado <> 'CANCELADO'
              and t.inicio < :hasta and t.fin > :desde
            order by t.inicio, t.id
            """)
    List<TurnoEntity> deAgendaDelPeriodo(@Param("desde") Instant desde, @Param("hasta") Instant hasta);

    boolean existsByCodigoReserva(String codigoReserva);
}
