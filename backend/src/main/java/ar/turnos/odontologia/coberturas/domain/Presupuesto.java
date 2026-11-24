package ar.turnos.odontologia.coberturas.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Presupuesto de un paciente: una foto inmutable del cálculo (DD-15, RN-PR-04). Nace {@code PENDIENTE} con la fecha de
 * hoy y vence a los 30 días; los totales son la suma de sus ítems. Solo cambia de estado, y eso se decide acá
 * ({@link EstadoPresupuesto}) y se aplica en la base con un {@code UPDATE} condicional. Sin Spring ni JPA.
 */
public final class Presupuesto {

    public static final int DIAS_DE_VIGENCIA = 30;
    public static final int MAXIMO_DE_ITEMS = 50;
    public static final int MAXIMA_CANTIDAD = 99;

    /** La afiliación usada con los nombres del momento de emitir (foto). */
    public record CoberturaUsada(Long afiliacionId, String obraSocial, String plan, String numeroAfiliado) {}

    /** Un ítem con su cálculo; el código del nomenclador y su id van juntos o ninguno (ítem particular, 0 %). */
    public record Item(Long prestacionId, String prestacionNombre, Long nomencladorId, String codigoNomenclador,
                       PiezaCara piezaCara, int cantidad, Precio precioUnitario, Porcentaje porcentaje,
                       CalculoItem calculo) {

        public static Item de(Long prestacionId, String prestacionNombre, Long nomencladorId, String codigoNomenclador,
                              PiezaCara piezaCara, int cantidad, Precio precioUnitario, Porcentaje porcentaje) {
            if (cantidad < 1 || cantidad > MAXIMA_CANTIDAD) {
                throw CoberturaException.datosInvalidos("cantidad", "La cantidad no es válida");
            }
            if ((nomencladorId == null) != (codigoNomenclador == null)
                    || (nomencladorId == null && porcentaje.valor() != 0)) {
                throw new IllegalArgumentException("Sin código del nomenclador el ítem es particular");
            }
            CalculoItem calculo = CalculoItem.de(precioUnitario, cantidad, porcentaje);
            if (calculo.importe().compareTo(Precio.MAXIMO) > 0) {
                throw CoberturaException.datosInvalidos("items", "Los importes superan el máximo");
            }
            return new Item(prestacionId, prestacionNombre, nomencladorId, codigoNomenclador, piezaCara, cantidad,
                    precioUnitario, porcentaje, calculo);
        }
    }

    private final Long id;
    private final Long pacienteId;
    private final Long profesionalId;
    private final LocalDate fecha;
    private final LocalDate venceEl;
    private final CoberturaUsada cobertura;
    private final EstadoPresupuesto estado;
    private final List<Item> items;
    private final Instant creadoEn;
    private final BigDecimal totalPrecio;
    private final BigDecimal totalCobertura;
    private final BigDecimal totalCopago;

    private Presupuesto(Long id, Long pacienteId, Long profesionalId, LocalDate fecha, LocalDate venceEl,
                        CoberturaUsada cobertura, EstadoPresupuesto estado, List<Item> items, Instant creadoEn) {
        this.id = id;
        this.pacienteId = pacienteId;
        this.profesionalId = profesionalId;
        this.fecha = fecha;
        this.venceEl = venceEl;
        this.cobertura = cobertura;
        this.estado = estado;
        this.items = List.copyOf(items);
        this.creadoEn = creadoEn;
        this.totalPrecio = suma(items, i -> i.calculo().importe());
        this.totalCobertura = suma(items, i -> i.calculo().cobertura());
        this.totalCopago = suma(items, i -> i.calculo().copago());
    }

    /** Un presupuesto nuevo: {@code PENDIENTE}, de {@code hoy}, que vence a los 30 días. */
    public static Presupuesto emitir(Long pacienteId, Long profesionalId, LocalDate hoy, CoberturaUsada cobertura,
                                     List<Item> items, Instant creadoEn) {
        if (items.isEmpty() || items.size() > MAXIMO_DE_ITEMS) {
            throw CoberturaException.datosInvalidos("items", "Un presupuesto lleva de 1 a 50 ítems");
        }
        Presupuesto nuevo = new Presupuesto(null, pacienteId, profesionalId, hoy, hoy.plusDays(DIAS_DE_VIGENCIA),
                cobertura, EstadoPresupuesto.PENDIENTE, items, creadoEn);
        if (nuevo.totalPrecio.compareTo(Precio.MAXIMO) > 0) {
            throw CoberturaException.datosInvalidos("items", "Los totales superan el máximo");
        }
        return nuevo;
    }

    /** Un presupuesto ya guardado, tal como está en la base. */
    public static Presupuesto reconstruir(Long id, Long pacienteId, Long profesionalId, LocalDate fecha,
                                          LocalDate venceEl, CoberturaUsada cobertura, EstadoPresupuesto estado,
                                          List<Item> items, Instant creadoEn) {
        return new Presupuesto(id, pacienteId, profesionalId, fecha, venceEl, cobertura, estado, items, creadoEn);
    }

    private static BigDecimal suma(List<Item> items, Function<Item, BigDecimal> campo) {
        return items.stream().map(campo).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    public boolean particular() {
        return cobertura == null;
    }

    public boolean vencido(LocalDate hoy) {
        return estado.vencido(venceEl, hoy);
    }

    /** El estado tras aceptar: {@code PRESUPUESTO_CERRADO} si ya no está pendiente, {@code PRESUPUESTO_VENCIDO} si venció. */
    public EstadoPresupuesto aceptar(LocalDate hoy) {
        return estado.pasarA(EstadoPresupuesto.ACEPTADO, venceEl, hoy);
    }

    /** El estado tras rechazar: un pendiente se rechaza aunque esté vencido. */
    public EstadoPresupuesto rechazar(LocalDate hoy) {
        return estado.pasarA(EstadoPresupuesto.RECHAZADO, venceEl, hoy);
    }

    public Long id() { return id; }
    public Long pacienteId() { return pacienteId; }
    public Long profesionalId() { return profesionalId; }
    public LocalDate fecha() { return fecha; }
    public LocalDate venceEl() { return venceEl; }
    public CoberturaUsada cobertura() { return cobertura; }
    public EstadoPresupuesto estado() { return estado; }
    public List<Item> items() { return items; }
    public Instant creadoEn() { return creadoEn; }
    public BigDecimal totalPrecio() { return totalPrecio; }
    public BigDecimal totalCobertura() { return totalCobertura; }
    public BigDecimal totalCopago() { return totalCopago; }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof Presupuesto p && id != null && Objects.equals(id, p.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
