package ar.turnos.odontologia.coberturas.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AfiliacionJpaRepository extends JpaRepository<AfiliacionEntity, Long> {

    List<AfiliacionEntity> findByPacienteIdOrderByVigenteDesdeDescIdDesc(Long pacienteId);

    Optional<AfiliacionEntity> findByIdAndPacienteId(Long id, Long pacienteId);

    @Query("select count(a) > 0 from AfiliacionEntity a where a.obraSocialId = :obraSocialId "
            + "and upper(a.numeroAfiliado) = upper(:numero) and a.pacienteId <> :pacienteId")
    boolean numeroEnUsoPorOtro(@Param("obraSocialId") Long obraSocialId, @Param("numero") String numero,
                               @Param("pacienteId") Long pacienteId);
}
