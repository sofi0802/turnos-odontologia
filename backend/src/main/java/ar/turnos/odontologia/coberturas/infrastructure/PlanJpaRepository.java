package ar.turnos.odontologia.coberturas.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PlanJpaRepository extends JpaRepository<PlanEntity, Long> {

    @Query("select p from PlanEntity p order by lower(p.nombre), p.id")
    List<PlanEntity> todosPorNombre();

    @Query("select p from PlanEntity p where p.obraSocialId = :obraSocialId order by lower(p.nombre), p.id")
    List<PlanEntity> deObraSocial(@Param("obraSocialId") Long obraSocialId);

    Optional<PlanEntity> findByIdAndObraSocialId(Long id, Long obraSocialId);

    /** {@code excluirId} es -1 cuando no se excluye ninguno. */
    @Query("select count(p) > 0 from PlanEntity p where p.obraSocialId = :obraSocialId "
            + "and lower(p.nombre) = lower(:nombre) and p.id <> :excluirId")
    boolean existeNombre(@Param("obraSocialId") Long obraSocialId, @Param("nombre") String nombre,
                         @Param("excluirId") Long excluirId);
}
