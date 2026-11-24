package ar.turnos.odontologia.coberturas.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Código del nomenclador (V12), con la prestación del consultorio vinculada, si hay. */
@Entity
@Table(name = "nomenclador")
class NomencladorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String codigo;
    private String descripcion;
    private Long prestacionId;
    private boolean activo;

    protected NomencladorEntity() {}

    NomencladorEntity(String codigo, String descripcion, Long prestacionId, boolean activo) {
        reemplazar(codigo, descripcion, prestacionId, activo);
    }

    void reemplazar(String codigo, String descripcion, Long prestacionId, boolean activo) {
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.prestacionId = prestacionId;
        this.activo = activo;
    }

    Long id() {
        return id;
    }

    String codigo() {
        return codigo;
    }

    String descripcion() {
        return descripcion;
    }

    Long prestacionId() {
        return prestacionId;
    }

    boolean activo() {
        return activo;
    }
}
