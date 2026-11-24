package ar.turnos.odontologia.coberturas.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NomencladorJpaRepository extends JpaRepository<NomencladorEntity, Long> {

    @Query("select n from NomencladorEntity n order by upper(n.codigo), n.id")
    List<NomencladorEntity> todosPorCodigo();

    /** {@code excluirId} es -1 cuando no se excluye ninguno. */
    @Query("select count(n) > 0 from NomencladorEntity n where upper(n.codigo) = upper(:codigo) and n.id <> :excluirId")
    boolean existeCodigo(@Param("codigo") String codigo, @Param("excluirId") Long excluirId);
}
