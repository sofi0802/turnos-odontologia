package ar.turnos.odontologia.clinica.infrastructure;

import org.springframework.data.repository.Repository;

/**
 * Solo inserción: extiende {@link Repository} y no {@code JpaRepository}, así no expone borrados ni otra cosa
 * que guardar (design D3). Las lecturas, con el nombre del profesional, las hace {@link RegistroClinicoJpa}.
 */
interface OdontogramaRegistroJpaRepository extends Repository<OdontogramaRegistroEntity, Long> {

    <S extends OdontogramaRegistroEntity> Iterable<S> saveAll(Iterable<S> registros);
}
