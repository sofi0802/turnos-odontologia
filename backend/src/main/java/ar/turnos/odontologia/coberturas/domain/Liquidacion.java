package ar.turnos.odontologia.coberturas.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Liquidación mensual de una obra social con sus líneas (RN-LQ-01 a RN-LQ-06). Es inmutable: cada transición
 * (presentar, conciliar, cerrar, quitar una línea) devuelve una liquidación nueva o lanza {@link CoberturaException}
 * con un código estable. La base repite estas reglas con triggers (V14), así una escritura directa tampoco las
 * saltea. Sin Spring ni JPA.
 */
public record Liquidacion(Long id, Long obraSocialId, Periodo periodo, EstadoLiquidacion estado,
                          List<LineaLiquidacion> lineas, Hitos hitos) {

    public Liquidacion {
        lineas = List.copyOf(lineas);
    }

    /** Quién y cuándo generó, presentó y cerró la liquidación. */
    public record Hitos(OffsetDateTime generadaEn, String generadaPor, OffsetDateTime presentadaEn,
                        String presentadaPor, OffsetDateTime cerradaEn, String cerradaPor) {

        public static Hitos generada(OffsetDateTime cuando, String actor) {
            return new Hitos(cuando, actor, null, null, null, null);
        }

        Hitos presentada(OffsetDateTime cuando, String actor) {
            return new Hitos(generadaEn, generadaPor, cuando, actor, null, null);
        }

        Hitos cerrada(OffsetDateTime cuando, String actor) {
            return new Hitos(generadaEn, generadaPor, presentadaEn, presentadaPor, cuando, actor);
        }
    }

    /**
     * Los totales del detalle (design D8). {@code diferencia} es lo solicitado de las líneas ya conciliadas menos lo
     * cobrado; {@code lineasPendientes} son las líneas sin resultado.
     */
    public record Totales(BigDecimal totalSolicitado, BigDecimal totalCobrado, BigDecimal solicitadoConciliado,
                          BigDecimal diferencia, int lineasPendientes) {
    }

    public Totales totales() {
        BigDecimal solicitado = BigDecimal.ZERO.setScale(2);
        BigDecimal cobrado = BigDecimal.ZERO.setScale(2);
        BigDecimal solicitadoConciliado = BigDecimal.ZERO.setScale(2);
        int pendientes = 0;
        for (LineaLiquidacion linea : lineas) {
            if (linea.montoSolicitado() != null) {
                solicitado = solicitado.add(linea.montoSolicitado());
            }
            if (linea.estaConciliada()) {
                cobrado = cobrado.add(linea.montoCobrado());
                solicitadoConciliado = solicitadoConciliado.add(linea.montoSolicitado());
            } else {
                pendientes++;
            }
        }
        return new Totales(solicitado, cobrado, solicitadoConciliado, solicitadoConciliado.subtract(cobrado),
                pendientes);
    }

    public LineaLiquidacion linea(Long lineaId) {
        return lineas.stream().filter(l -> lineaId.equals(l.id())).findFirst()
                .orElseThrow(() -> CoberturaException.noEncontrado("No existe la línea de la liquidación"));
    }

    /** Las líneas, el código y los turnos solo cambian en {@code BORRADOR}. */
    public void exigirBorrador() {
        switch (estado) {
            case PRESENTADA -> throw new CoberturaException(CodigoCobertura.LIQUIDACION_PRESENTADA,
                    "La liquidación ya está presentada");
            case CERRADA -> throw new CoberturaException(CodigoCobertura.LIQUIDACION_CERRADA,
                    "La liquidación está cerrada");
            case BORRADOR -> { }
        }
    }

    /** Lo cobrado se registra solo con la liquidación {@code PRESENTADA}. */
    private void exigirPresentada() {
        switch (estado) {
            case BORRADOR -> throw new CoberturaException(CodigoCobertura.LIQUIDACION_NO_PRESENTADA,
                    "La liquidación todavía no está presentada");
            case CERRADA -> throw new CoberturaException(CodigoCobertura.LIQUIDACION_CERRADA,
                    "La liquidación está cerrada");
            case PRESENTADA -> { }
        }
    }

    /** {@code BORRADOR} a {@code PRESENTADA}: con al menos una línea y todas con código. */
    public Liquidacion presentar(OffsetDateTime cuando, String actor) {
        exigirBorrador();
        if (lineas.isEmpty()) {
            throw new CoberturaException(CodigoCobertura.LIQUIDACION_VACIA, "La liquidación no tiene líneas");
        }
        if (lineas.stream().anyMatch(l -> !l.tieneCodigo())) {
            throw new CoberturaException(CodigoCobertura.LINEA_SIN_CODIGO, "Hay líneas sin código del nomenclador");
        }
        return new Liquidacion(id, obraSocialId, periodo, EstadoLiquidacion.PRESENTADA, lineas,
                hitos.presentada(cuando, actor));
    }

    /** Registra o corrige lo cobrado de una línea; los montos solicitados no cambian. */
    public Liquidacion conciliar(Long lineaId, BigDecimal cobrado, String motivo, OffsetDateTime cuando, String actor) {
        exigirPresentada();
        LineaLiquidacion linea = linea(lineaId);
        Conciliacion conciliacion = Conciliacion.de(cobrado, motivo, linea.montoSolicitado());
        return conLineas(lineas.stream()
                .map(l -> l.id().equals(lineaId) ? l.conciliada(conciliacion, cuando, actor) : l).toList());
    }

    /** {@code PRESENTADA} a {@code CERRADA}: todas las líneas con resultado. */
    public Liquidacion cerrar(OffsetDateTime cuando, String actor) {
        exigirPresentada();
        if (lineas.stream().anyMatch(l -> !l.estaConciliada())) {
            throw new CoberturaException(CodigoCobertura.CONCILIACION_INCOMPLETA,
                    "Quedan líneas sin conciliar");
        }
        return new Liquidacion(id, obraSocialId, periodo, EstadoLiquidacion.CERRADA, lineas,
                hitos.cerrada(cuando, actor));
    }

    /** Saca una línea de un borrador. */
    public Liquidacion sinLinea(Long lineaId) {
        exigirBorrador();
        linea(lineaId);
        List<LineaLiquidacion> restantes = new ArrayList<>(lineas);
        restantes.removeIf(l -> lineaId.equals(l.id()));
        return conLineas(restantes);
    }

    public Liquidacion conLineas(List<LineaLiquidacion> nuevas) {
        return new Liquidacion(id, obraSocialId, periodo, estado, nuevas, hitos);
    }

    public Liquidacion conHitos(Hitos nuevos) {
        return new Liquidacion(id, obraSocialId, periodo, estado, lineas, nuevos);
    }
}
