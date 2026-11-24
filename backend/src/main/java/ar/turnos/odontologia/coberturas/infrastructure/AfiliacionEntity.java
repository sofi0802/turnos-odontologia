package ar.turnos.odontologia.coberturas.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** Afiliación de un paciente (V12). Sin {@code toString}: el número de afiliado no debe terminar en un log. */
@Entity
@Table(name = "afiliacion_paciente")
class AfiliacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Long pacienteId;

    private Long planId;
    private Long obraSocialId;
    private String numeroAfiliado;
    private LocalDate vigenteDesde;
    private LocalDate vigenteHasta;

    protected AfiliacionEntity() {}

    AfiliacionEntity(Long pacienteId) {
        this.pacienteId = pacienteId;
    }

    void reemplazar(Long planId, Long obraSocialId, String numeroAfiliado, LocalDate vigenteDesde,
                    LocalDate vigenteHasta) {
        this.planId = planId;
        this.obraSocialId = obraSocialId;
        this.numeroAfiliado = numeroAfiliado;
        this.vigenteDesde = vigenteDesde;
        this.vigenteHasta = vigenteHasta;
    }

    Long id() {
        return id;
    }

    Long pacienteId() {
        return pacienteId;
    }

    Long planId() {
        return planId;
    }

    Long obraSocialId() {
        return obraSocialId;
    }

    String numeroAfiliado() {
        return numeroAfiliado;
    }

    LocalDate vigenteDesde() {
        return vigenteDesde;
    }

    LocalDate vigenteHasta() {
        return vigenteHasta;
    }
}
