package ar.turnos.odontologia.coberturas.infrastructure;

import ar.turnos.odontologia.coberturas.domain.Presupuesto;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Immutable;

/**
 * Presupuesto emitido (V13). {@code @Immutable}: la entidad solo inserta (con sus ítems en cascada); el estado cambia
 * por el {@code UPDATE} condicional de {@code PresupuestosJpa}, nunca desde la entidad. Sin {@code toString}: contiene
 * importes y el número de afiliado, que no deben terminar en un log (RN-PR-07).
 */
@Entity
@Table(name = "presupuesto")
@Immutable
class PresupuestoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long pacienteId;
    private Long profesionalId;
    private LocalDate fecha;
    private LocalDate venceEl;
    private Long afiliacionId;
    private String obraSocialNombre;
    private String planNombre;
    private String numeroAfiliado;
    private String estado;
    private BigDecimal totalPrecio;
    private BigDecimal totalCobertura;
    private BigDecimal totalCopago;
    private Instant creadoEn;

    @OneToMany(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "presupuesto_id", nullable = false, updatable = false)
    private List<PresupuestoItemEntity> items;

    protected PresupuestoEntity() {}

    PresupuestoEntity(Presupuesto p) {
        this.pacienteId = p.pacienteId();
        this.profesionalId = p.profesionalId();
        this.fecha = p.fecha();
        this.venceEl = p.venceEl();
        if (p.cobertura() != null) {
            this.afiliacionId = p.cobertura().afiliacionId();
            this.obraSocialNombre = p.cobertura().obraSocial();
            this.planNombre = p.cobertura().plan();
            this.numeroAfiliado = p.cobertura().numeroAfiliado();
        }
        this.estado = p.estado().name();
        this.totalPrecio = p.totalPrecio();
        this.totalCobertura = p.totalCobertura();
        this.totalCopago = p.totalCopago();
        this.creadoEn = p.creadoEn();
        this.items = new ArrayList<>(p.items().stream().map(PresupuestoItemEntity::new).toList());
    }

    Long id() {
        return id;
    }
}
