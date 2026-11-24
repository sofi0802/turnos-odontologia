package ar.turnos.odontologia.coberturas.infrastructure;

import ar.turnos.odontologia.coberturas.domain.Presupuesto;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.Immutable;

/**
 * Ítem de un presupuesto emitido (V13), con la foto del precio, el porcentaje y los nombres. {@code @Immutable} y sin
 * {@code toString}: pieza, cara e importes son datos de salud y de dinero (RN-PR-07). {@code pieza} y
 * {@code porcentaje_cobertura} son {@code SMALLINT}, por eso {@code Short}.
 */
@Entity
@Table(name = "presupuesto_item")
@Immutable
class PresupuestoItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long prestacionId;
    private String prestacionNombre;
    private Long nomencladorId;
    private String codigoNomenclador;
    private Short pieza;
    private String cara;
    private Integer cantidad;
    private BigDecimal precioUnitario;
    private Short porcentajeCobertura;
    private BigDecimal importe;
    private BigDecimal cobertura;
    private BigDecimal copago;

    protected PresupuestoItemEntity() {}

    PresupuestoItemEntity(Presupuesto.Item item) {
        this.prestacionId = item.prestacionId();
        this.prestacionNombre = item.prestacionNombre();
        this.nomencladorId = item.nomencladorId();
        this.codigoNomenclador = item.codigoNomenclador();
        this.pieza = item.piezaCara().pieza() == null ? null : item.piezaCara().pieza().shortValue();
        this.cara = item.piezaCara().cara();
        this.cantidad = item.cantidad();
        this.precioUnitario = item.precioUnitario().valor();
        this.porcentajeCobertura = (short) item.porcentaje().valor();
        this.importe = item.calculo().importe();
        this.cobertura = item.calculo().cobertura();
        this.copago = item.calculo().copago();
    }
}
