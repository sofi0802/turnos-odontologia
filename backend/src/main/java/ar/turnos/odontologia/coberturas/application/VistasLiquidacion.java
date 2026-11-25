package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion;
import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion.CodigoCubierto;
import ar.turnos.odontologia.coberturas.domain.Liquidacion;
import ar.turnos.odontologia.coberturas.domain.Liquidacion.Totales;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Datos que devuelven los casos de uso de liquidaciones: records, nunca entidades JPA (regla dura 9). El detalle agrupa
 * las líneas por plan con su subtotal y trae los totales de la conciliación (C-20, design D8 y D9). Los nombres, DNI y
 * números de afiliado salen solo del detalle, nunca del listado (RN-LQ-08).
 */
public final class VistasLiquidacion {

    private VistasLiquidacion() {
    }

    public record ObraSocialRef(Long id, String nombre) {}

    public record PacienteRef(Long id, String nombre, String dni) {}

    public record PrestacionRef(Long id, String nombre) {}

    public record CodigoDisponible(Long nomencladorId, String codigo, int porcentaje) {}

    public record LineaVista(Long id, Long turnoId, LocalDate fecha, PacienteRef paciente, String numeroAfiliado,
                             PrestacionRef prestacion, BigDecimal precio, String origenPrecio, Long nomencladorId,
                             String codigo, Integer porcentaje, BigDecimal montoSolicitado, boolean codigoElegido,
                             List<CodigoDisponible> codigosDisponibles, BigDecimal montoCobrado, String resultado,
                             String motivoRechazo, OffsetDateTime conciliadaEn, String conciliadaPor) {}

    public record GrupoPlan(Long planId, String plan, BigDecimal subtotal, List<LineaVista> lineas) {}

    /**
     * El detalle de una liquidación. {@code turnosSinArancel} solo viaja en la respuesta de generar y de regenerar
     * (es un número, nunca identifica turnos).
     */
    public record LiquidacionVista(Long id, ObraSocialRef obraSocial, String periodo, String estado,
                                   OffsetDateTime generadaEn, String generadaPor, OffsetDateTime presentadaEn,
                                   String presentadaPor, OffsetDateTime cerradaEn, String cerradaPor,
                                   BigDecimal totalSolicitado, BigDecimal totalCobrado,
                                   BigDecimal solicitadoConciliado, BigDecimal diferencia, int lineasPendientes,
                                   int cantidadLineas, List<GrupoPlan> grupos,
                                   @JsonInclude(JsonInclude.Include.NON_NULL) Integer turnosSinArancel) {

        public LiquidacionVista conTurnosSinArancel(int cantidad) {
            return new LiquidacionVista(id, obraSocial, periodo, estado, generadaEn, generadaPor, presentadaEn,
                    presentadaPor, cerradaEn, cerradaPor, totalSolicitado, totalCobrado, solicitadoConciliado,
                    diferencia, lineasPendientes, cantidadLineas, grupos, cantidad);
        }
    }

    /** Una fila del listado: sin pacientes ni líneas. */
    public record ResumenVista(Long id, ObraSocialRef obraSocial, String periodo, String estado, int cantidadLineas,
                               BigDecimal totalSolicitado, BigDecimal totalCobrado) {
        static ResumenVista de(Liquidaciones.Resumen r) {
            return new ResumenVista(r.id(), new ObraSocialRef(r.obraSocialId(), r.obraSocial()), r.periodo(),
                    r.estado(), r.cantidadLineas(), r.totalSolicitado(), r.totalCobrado());
        }
    }

    static LiquidacionVista de(Liquidacion liquidacion, String obraSocial,
                               Map<Long, List<CodigoCubierto>> disponibles) {
        Totales totales = liquidacion.totales();
        Map<Long, List<LineaVista>> porPlan = new LinkedHashMap<>();
        Map<Long, String> nombres = new LinkedHashMap<>();
        Map<Long, BigDecimal> subtotales = new LinkedHashMap<>();
        List<LineaLiquidacion> ordenadas = new ArrayList<>(liquidacion.lineas());
        ordenadas.sort(Comparator.comparing(LineaLiquidacion::planNombre)
                .thenComparing(LineaLiquidacion::planId)
                .thenComparing(LineaLiquidacion::fecha)
                .thenComparing(LineaLiquidacion::turnoId));
        for (LineaLiquidacion l : ordenadas) {
            porPlan.computeIfAbsent(l.planId(), k -> new ArrayList<>()).add(linea(l, disponibles));
            nombres.putIfAbsent(l.planId(), l.planNombre());
            subtotales.merge(l.planId(), l.montoSolicitado() == null ? BigDecimal.ZERO.setScale(2) : l.montoSolicitado(),
                    BigDecimal::add);
        }
        List<GrupoPlan> grupos = porPlan.entrySet().stream()
                .map(e -> new GrupoPlan(e.getKey(), nombres.get(e.getKey()), subtotales.get(e.getKey()), e.getValue()))
                .toList();
        return new LiquidacionVista(liquidacion.id(), new ObraSocialRef(liquidacion.obraSocialId(), obraSocial),
                liquidacion.periodo().toString(), liquidacion.estado().name(), liquidacion.hitos().generadaEn(),
                liquidacion.hitos().generadaPor(), liquidacion.hitos().presentadaEn(),
                liquidacion.hitos().presentadaPor(), liquidacion.hitos().cerradaEn(), liquidacion.hitos().cerradaPor(),
                totales.totalSolicitado(), totales.totalCobrado(), totales.solicitadoConciliado(),
                totales.diferencia(), totales.lineasPendientes(), liquidacion.lineas().size(), grupos, null);
    }

    private static LineaVista linea(LineaLiquidacion l, Map<Long, List<CodigoCubierto>> disponibles) {
        List<CodigoDisponible> alternativas = l.tieneCodigo() ? null
                : disponibles.getOrDefault(l.id(), List.of()).stream()
                        .map(c -> new CodigoDisponible(c.nomencladorId(), c.codigo(), c.porcentaje())).toList();
        return new LineaVista(l.id(), l.turnoId(), l.fecha(), new PacienteRef(l.pacienteId(), l.pacienteNombre(),
                l.pacienteDni()), l.numeroAfiliado(), new PrestacionRef(l.prestacionId(), l.prestacionNombre()),
                l.precio(), l.origenPrecio().name(), l.nomencladorId(), l.codigo(), l.porcentaje(),
                l.montoSolicitado(), l.codigoElegido(), alternativas, l.montoCobrado(),
                l.resultado() == null ? null : l.resultado().name(), l.motivoRechazo(), l.conciliadaEn(),
                l.conciliadaPor());
    }
}
