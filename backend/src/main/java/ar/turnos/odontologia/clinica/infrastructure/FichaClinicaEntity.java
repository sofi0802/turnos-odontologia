package ar.turnos.odontologia.clinica.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Ficha básica (V8). Sin {@code toString}: los textos son datos de salud y no deben terminar en un log. */
@Entity
@Table(name = "ficha_clinica")
class FichaClinicaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Long pacienteId;

    private String antecedentes;
    private String alergias;
    private String medicacion;
    private String actualizadoPor;
    private Instant actualizadoEn;

    protected FichaClinicaEntity() {}

    FichaClinicaEntity(Long pacienteId) {
        this.pacienteId = pacienteId;
    }

    void reemplazar(String antecedentes, String alergias, String medicacion, String actualizadoPor,
                    Instant actualizadoEn) {
        this.antecedentes = antecedentes;
        this.alergias = alergias;
        this.medicacion = medicacion;
        this.actualizadoPor = actualizadoPor;
        this.actualizadoEn = actualizadoEn;
    }

    Long pacienteId() {
        return pacienteId;
    }

    String antecedentes() {
        return antecedentes;
    }

    String alergias() {
        return alergias;
    }

    String medicacion() {
        return medicacion;
    }

    String actualizadoPor() {
        return actualizadoPor;
    }

    Instant actualizadoEn() {
        return actualizadoEn;
    }
}
