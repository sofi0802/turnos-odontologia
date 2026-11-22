package ar.turnos.odontologia.clinica.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Registro del odontograma (V8), de solo inserción: todas las columnas son {@code updatable = false} y no hay
 * setters. La base además rechaza {@code UPDATE} y {@code DELETE} con un trigger (design D3).
 */
@Entity
@Table(name = "odontograma_registro")
class OdontogramaRegistroEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Long pacienteId;

    @Column(updatable = false)
    private Short pieza;

    @Column(updatable = false)
    private String cara;

    @Column(updatable = false)
    private String estado;

    @Column(updatable = false)
    private Long profesionalId;

    @Column(updatable = false)
    private Instant registradoEn;

    protected OdontogramaRegistroEntity() {}

    OdontogramaRegistroEntity(Long pacienteId, int pieza, String cara, String estado, Long profesionalId,
                              Instant registradoEn) {
        this.pacienteId = pacienteId;
        this.pieza = (short) pieza;
        this.cara = cara;
        this.estado = estado;
        this.profesionalId = profesionalId;
        this.registradoEn = registradoEn;
    }
}
