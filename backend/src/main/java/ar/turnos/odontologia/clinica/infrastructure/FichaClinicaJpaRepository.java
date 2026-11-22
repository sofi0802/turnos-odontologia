package ar.turnos.odontologia.clinica.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface FichaClinicaJpaRepository extends JpaRepository<FichaClinicaEntity, Long> {

    Optional<FichaClinicaEntity> findByPacienteId(Long pacienteId);
}
