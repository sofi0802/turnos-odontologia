package ar.turnos.odontologia.coberturas.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ObraSocialJpaRepository extends JpaRepository<ObraSocialEntity, Long> {

    @Query("select o from ObraSocialEntity o order by lower(o.nombre), o.id")
    List<ObraSocialEntity> todasPorNombre();

    /** {@code excluirId} es -1 cuando no se excluye ninguna (un parámetro nulo no tiene tipo en PostgreSQL). */
    @Query("select count(o) > 0 from ObraSocialEntity o where lower(o.nombre) = lower(:nombre) and o.id <> :excluirId")
    boolean existeNombre(@Param("nombre") String nombre, @Param("excluirId") Long excluirId);
}
