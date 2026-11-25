package ar.turnos.odontologia.coberturas.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Una línea de liquidación: un turno atendido con la foto de lo que se le pide a la obra social (RN-LQ-01, RN-LQ-03 y
 * DD-15). Lleva el código del nomenclador, el porcentaje y el monto solicitado, o ninguno de los tres si la prestación
 * tiene varios códigos y el administrador todavía no eligió uno (PA-38). La conciliación (RN-LQ-06) se completa
 * después, con la liquidación presentada. Sin Spring ni JPA.
 */
public record LineaLiquidacion(
        Long id, Long turnoId, Long pacienteId, Long planId, Long prestacionId, LocalDate fecha,
        String pacienteNombre, String pacienteDni, String numeroAfiliado, String planNombre, String prestacionNombre,
        BigDecimal precio, OrigenPrecio origenPrecio, boolean codigoElegido,
        Long nomencladorId, String codigo, Integer porcentaje, BigDecimal montoSolicitado,
        BigDecimal montoCobrado, ResultadoConciliacion resultado, String motivoRechazo,
        OffsetDateTime conciliadaEn, String conciliadaPor) {

    /** De dónde sale el precio de la línea: el arancel vigente al generar o el cargo de caja del turno (C-22). */
    public enum OrigenPrecio { ARANCEL, CARGO }

    /** Por qué un turno que parecía candidato no genera una línea. */
    public enum Descarte {
        /** Cumple las condiciones pero la prestación no tiene arancel ni cargo: se informa en la respuesta. */
        SIN_ARANCEL,
        /** Ningún código que el plan cubra con más de 0 %, cargo sin cobertura o monto que redondea a cero. */
        NO_LIQUIDABLE
    }

    /** Un código activo de la prestación con el porcentaje que cubre el plan (0 si el plan no lo cubre). */
    public record CodigoCubierto(Long nomencladorId, String codigo, int porcentaje) {
    }

    /**
     * Un turno atendido de un afiliado a la obra social, con lo que hace falta para armar su línea. {@code arancel}
     * es nulo si la prestación no tiene precio; {@code codigos} son los códigos activos de la prestación.
     */
    public record Candidato(Long turnoId, Long pacienteId, Long planId, Long prestacionId, LocalDate fecha,
                            String pacienteNombre, String pacienteDni, String numeroAfiliado, String planNombre,
                            String prestacionNombre, BigDecimal arancel, List<CodigoCubierto> codigos) {
    }

    /** O la línea o el motivo por el que no se genera. */
    public record Evaluacion(LineaLiquidacion linea, Descarte descarte) {
    }

    /**
     * Reglas de design D4, paso 3. Primero los códigos (si el plan no cubre ninguno con más de 0 %, el turno no se
     * liquida), después el precio (cargo de caja o arancel; sin ninguno de los dos, se informa como sin arancel).
     * Una prestación con un solo código activo usa ese; con varios, la línea queda sin código salvo que el
     * administrador ya hubiera elegido uno que el plan siga cubriendo.
     *
     * @param codigoElegidoId el código que el administrador eligió en una generación anterior, o {@code null}
     */
    public static Evaluacion desde(Candidato candidato, CargoTurno cargo, Long codigoElegidoId) {
        if (cargo != null && cargo.porcentaje() == 0) {
            return descartada(Descarte.NO_LIQUIDABLE);
        }
        List<CodigoCubierto> cubiertos = candidato.codigos().stream().filter(c -> c.porcentaje() > 0).toList();
        if (cubiertos.isEmpty()) {
            return descartada(Descarte.NO_LIQUIDABLE);
        }
        BigDecimal precio;
        OrigenPrecio origen;
        if (cargo != null) {
            precio = cargo.precio().setScale(2);
            origen = OrigenPrecio.CARGO;
        } else if (candidato.arancel() != null) {
            precio = candidato.arancel().setScale(2);
            origen = OrigenPrecio.ARANCEL;
        } else {
            return descartada(Descarte.SIN_ARANCEL);
        }
        CodigoCubierto codigo = null;
        boolean elegido = false;
        if (candidato.codigos().size() == 1) {
            codigo = cubiertos.get(0);
        } else if (codigoElegidoId != null) {
            codigo = cubiertos.stream().filter(c -> c.nomencladorId().equals(codigoElegidoId)).findFirst().orElse(null);
            elegido = codigo != null;
        }
        LineaLiquidacion linea = new LineaLiquidacion(null, candidato.turnoId(), candidato.pacienteId(),
                candidato.planId(), candidato.prestacionId(), candidato.fecha(), candidato.pacienteNombre(),
                candidato.pacienteDni(), candidato.numeroAfiliado(), candidato.planNombre(),
                candidato.prestacionNombre(), precio, origen, false, null, null, null, null, null, null, null, null,
                null);
        if (codigo == null) {
            return new Evaluacion(linea, null);
        }
        LineaLiquidacion conCodigo = linea.conCodigo(codigo, cargo == null ? null : cargo.porcentaje(), elegido);
        return conCodigo == null ? descartada(Descarte.NO_LIQUIDABLE) : new Evaluacion(conCodigo, null);
    }

    private static Evaluacion descartada(Descarte descarte) {
        return new Evaluacion(null, descarte);
    }

    /**
     * La misma línea con un código y su monto, o {@code null} si el monto redondea a cero. El porcentaje es el del
     * cargo si lo hay y, si no, el que el plan cubre para el código (el monto usa {@link CalculoItem}, igual que los
     * presupuestos y la {@code CHECK} de V14).
     */
    public LineaLiquidacion conCodigo(CodigoCubierto codigoCubierto, Integer porcentajeDelCargo, boolean elegido) {
        int pct = porcentajeDelCargo != null ? porcentajeDelCargo : codigoCubierto.porcentaje();
        BigDecimal monto = CalculoItem.de(Precio.guardado(precio), 1, Porcentaje.guardado(pct)).cobertura();
        if (monto.signum() <= 0) {
            return null;
        }
        return new LineaLiquidacion(id, turnoId, pacienteId, planId, prestacionId, fecha, pacienteNombre, pacienteDni,
                numeroAfiliado, planNombre, prestacionNombre, precio, origenPrecio, elegido,
                codigoCubierto.nomencladorId(), codigoCubierto.codigo(), pct, monto, null, null, null, null, null);
    }

    public LineaLiquidacion conId(Long nuevoId) {
        return new LineaLiquidacion(nuevoId, turnoId, pacienteId, planId, prestacionId, fecha, pacienteNombre,
                pacienteDni, numeroAfiliado, planNombre, prestacionNombre, precio, origenPrecio, codigoElegido,
                nomencladorId, codigo, porcentaje, montoSolicitado, montoCobrado, resultado, motivoRechazo,
                conciliadaEn, conciliadaPor);
    }

    /** Registra o corrige la conciliación; el monto solicitado y la foto no cambian. */
    public LineaLiquidacion conciliada(Conciliacion conciliacion, OffsetDateTime cuando, String actor) {
        Objects.requireNonNull(montoSolicitado, "montoSolicitado");
        return new LineaLiquidacion(id, turnoId, pacienteId, planId, prestacionId, fecha, pacienteNombre, pacienteDni,
                numeroAfiliado, planNombre, prestacionNombre, precio, origenPrecio, codigoElegido, nomencladorId,
                codigo, porcentaje, montoSolicitado, conciliacion.montoCobrado(), conciliacion.resultado(),
                conciliacion.motivo(), cuando, actor);
    }

    public boolean tieneCodigo() {
        return nomencladorId != null;
    }

    public boolean estaConciliada() {
        return resultado != null;
    }
}
