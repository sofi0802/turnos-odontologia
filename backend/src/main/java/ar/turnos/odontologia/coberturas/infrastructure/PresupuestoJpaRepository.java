package ar.turnos.odontologia.coberturas.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

interface PresupuestoJpaRepository extends JpaRepository<PresupuestoEntity, Long> {
}
