package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.domain.CodigoNomenclador;
import ar.turnos.odontologia.coberturas.domain.ObraSocial;
import ar.turnos.odontologia.coberturas.domain.Plan;
import ar.turnos.odontologia.coberturas.application.LecturaCoberturas.AfiliacionDetalle;
import ar.turnos.odontologia.coberturas.domain.EstadoPresupuesto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Datos que devuelven los casos de uso del catálogo: records, nunca entidades JPA (regla dura 9). */
public final class Vistas {

    private Vistas() {
    }

    public record PlanVista(Long id, Long obraSocialId, String nombre, boolean activo) {
        static PlanVista de(Plan plan) {
            return new PlanVista(plan.id(), plan.obraSocialId(), plan.nombre(), plan.activo());
        }
    }

    public record ObraSocialVista(Long id, String nombre, String sigla, String tipo, boolean activa,
                                  List<PlanVista> planes) {
        static ObraSocialVista de(ObraSocial obra, List<Plan> planes) {
            return new ObraSocialVista(obra.id(), obra.nombre(), obra.sigla(), obra.tipo().name(), obra.activa(),
                    planes.stream().map(PlanVista::de).toList());
        }
    }

    public record PrestacionRef(Long id, String nombre) {}

    /** El porcentaje que cubre un plan para un código del nomenclador. */
    public record CoberturaCodigoVista(Long nomencladorId, String codigo, String descripcion, boolean activo,
                                       int porcentaje) {
        static CoberturaCodigoVista de(CoberturasPlan.CoberturaFila f) {
            return new CoberturaCodigoVista(f.nomencladorId(), f.codigo(), f.descripcion(), f.activo(), f.porcentaje());
        }
    }

    /** El precio particular de una prestación; sin precio cargado {@code precio} y {@code actualizadoEn} son nulos. */
    public record ArancelVista(PrestacionRef prestacion, boolean activa, BigDecimal precio, Instant actualizadoEn) {
        static ArancelVista de(Aranceles.ArancelFila f) {
            return new ArancelVista(new PrestacionRef(f.prestacionId(), f.prestacionNombre()), f.activa(), f.precio(),
                    f.actualizadoEn());
        }
    }

    public record CodigoVista(Long id, String codigo, String descripcion, PrestacionRef prestacion, boolean activo) {
        static CodigoVista de(CodigoNomenclador c, String nombrePrestacion) {
            return new CodigoVista(c.id(), c.codigo(), c.descripcion(),
                    c.prestacionId() == null ? null : new PrestacionRef(c.prestacionId(), nombrePrestacion),
                    c.activo());
        }
    }

    /** La cobertura de un paciente en una fecha: su afiliación vigente o {@code particular} (RN-OS-04). */
    public record CoberturaVista(boolean particular, AfiliacionVista afiliacion) {}

    public record ObraSocialResumen(Long id, String nombre, String sigla, String tipo) {}

    public record PlanResumen(Long id, String nombre) {}

    /** Una afiliación con su plan y su obra social; {@code vigente} es si lo es en la fecha de la consulta. */
    public record AfiliacionVista(Long id, ObraSocialResumen obraSocial, PlanResumen plan, String numeroAfiliado,
                                  LocalDate vigenteDesde, LocalDate vigenteHasta, boolean vigente) {
        static AfiliacionVista de(AfiliacionDetalle d, LocalDate fecha) {
            boolean vigente = !fecha.isBefore(d.vigenteDesde()) && (d.vigenteHasta() == null
                    || !fecha.isAfter(d.vigenteHasta()));
            return new AfiliacionVista(d.id(), new ObraSocialResumen(d.obraSocialId(), d.obraSocial(), d.sigla(),
                    d.tipo()), new PlanResumen(d.planId(), d.plan()), d.numeroAfiliado(), d.vigenteDesde(),
                    d.vigenteHasta(), vigente);
        }
    }

    // --- Presupuestos (C-19) ---

    public record ProfesionalRef(Long id, String nombre) {}

    /** La afiliación usada, con los nombres del momento de emitir (foto). */
    public record CoberturaUsadaVista(Long afiliacionId, String obraSocial, String plan, String numeroAfiliado) {}

    public record ItemPresupuestoVista(Long id, PrestacionRef prestacion, String codigoNomenclador, Integer pieza,
                                       String cara, int cantidad, BigDecimal precioUnitario, int porcentajeCobertura,
                                       BigDecimal importe, BigDecimal cobertura, BigDecimal copago) {}

    public record TotalesVista(BigDecimal precio, BigDecimal cobertura, BigDecimal copago) {}

    /**
     * Un presupuesto emitido. {@code estado} es el guardado y {@code vencido} se calcula con la fecha de hoy en Buenos
     * Aires (solo un pendiente puede estar vencido). {@code cobertura} es nula si es particular.
     */
    public record PresupuestoVista(Long id, LocalDate fecha, LocalDate venceEl, String estado, boolean vencido,
                                   ProfesionalRef profesional, boolean particular, CoberturaUsadaVista cobertura,
                                   List<ItemPresupuestoVista> items, TotalesVista totales, Instant creadoEn) {

        static PresupuestoVista de(LecturaPresupuestos.PresupuestoFila f, LocalDate hoy) {
            boolean vencido = EstadoPresupuesto.valueOf(f.estado()).vencido(f.venceEl(), hoy);
            CoberturaUsadaVista cobertura = f.afiliacionId() == null ? null
                    : new CoberturaUsadaVista(f.afiliacionId(), f.obraSocial(), f.plan(), f.numeroAfiliado());
            List<ItemPresupuestoVista> items = f.items().stream()
                    .map(i -> new ItemPresupuestoVista(i.id(), new PrestacionRef(i.prestacionId(), i.prestacion()),
                            i.codigoNomenclador(), i.pieza(), i.cara(), i.cantidad(), i.precioUnitario(),
                            i.porcentajeCobertura(), i.importe(), i.cobertura(), i.copago()))
                    .toList();
            return new PresupuestoVista(f.id(), f.fecha(), f.venceEl(), f.estado(), vencido,
                    new ProfesionalRef(f.profesionalId(), f.profesional()), cobertura == null, cobertura, items,
                    new TotalesVista(f.totalPrecio(), f.totalCobertura(), f.totalCopago()), f.creadoEn());
        }
    }

    /** Una pieza (y cara) del odontograma cuyo último estado es {@code A_TRATAR} o {@code CARIES}. */
    public record SugerenciaVista(int pieza, String cara, String estado) {}
}
