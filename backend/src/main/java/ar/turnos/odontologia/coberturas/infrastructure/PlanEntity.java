package ar.turnos.odontologia.coberturas.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Plan de una obra social (V12). La obra social no se puede cambiar una vez creado el plan. */
@Entity
@Table(name = "plan")
class PlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Long obraSocialId;

    private String nombre;
    private boolean activo;

    protected PlanEntity() {}

    PlanEntity(Long obraSocialId, String nombre, boolean activo) {
        this.obraSocialId = obraSocialId;
        reemplazar(nombre, activo);
    }

    void reemplazar(String nombre, boolean activo) {
        this.nombre = nombre;
        this.activo = activo;
    }

    Long id() {
        return id;
    }

    Long obraSocialId() {
        return obraSocialId;
    }

    String nombre() {
        return nombre;
    }

    boolean activo() {
        return activo;
    }
}
