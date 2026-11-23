package ar.turnos.odontologia.configuracion.infrastructure;

import ar.turnos.odontologia.shared.persistence.ProfesionalEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfesionalRepository extends JpaRepository<ProfesionalEntity, Long> {

    boolean existsByMatricula(String matricula);

    boolean existsByMatriculaAndIdNot(String matricula, Long id);

    /** Si otro profesional, activo o inactivo, tiene ese sillón como habitual (RN-RO-09). */
    boolean existsBySillonHabitualIdAndIdNot(Long sillonId, Long profesionalId);

    List<ProfesionalEntity> findAllByOrderByApellidoAscNombreAsc();

    @Query("""
            select count(p) > 0 from ProfesionalEntity p join p.especialidades e
            where p.id = :profesionalId and e.id = :prestacionId""")
    boolean estaHabilitado(@Param("profesionalId") Long profesionalId,
                           @Param("prestacionId") Long prestacionId);
}
