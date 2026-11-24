package ar.turnos.odontologia.coberturas.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Obra social o prepaga (V12). Solo la usa el adaptador: la API nunca la recibe. */
@Entity
@Table(name = "obra_social")
class ObraSocialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String sigla;
    private String tipo;
    private boolean activa;

    protected ObraSocialEntity() {}

    ObraSocialEntity(String nombre, String sigla, String tipo, boolean activa) {
        reemplazar(nombre, sigla, tipo, activa);
    }

    void reemplazar(String nombre, String sigla, String tipo, boolean activa) {
        this.nombre = nombre;
        this.sigla = sigla;
        this.tipo = tipo;
        this.activa = activa;
    }

    Long id() {
        return id;
    }

    String nombre() {
        return nombre;
    }

    String sigla() {
        return sigla;
    }

    String tipo() {
        return tipo;
    }

    boolean activa() {
        return activa;
    }
}
