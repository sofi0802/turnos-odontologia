package ar.turnos.odontologia.coberturas.domain;

import java.time.LocalDate;

/**
 * Estado guardado de un presupuesto (RN-PR-05). "Vencido" no es un estado: se calcula con la fecha de hoy en Buenos
 * Aires, solo para un presupuesto pendiente (design D5).
 */
public enum EstadoPresupuesto {
    PENDIENTE, ACEPTADO, RECHAZADO;

    /** Un pendiente cuyo vencimiento es anterior a {@code hoy}; el día del vencimiento todavía no está vencido. */
    public boolean vencido(LocalDate venceEl, LocalDate hoy) {
        return this == PENDIENTE && venceEl.isBefore(hoy);
    }

    /**
     * El estado al que pasa un presupuesto en este estado: solo un pendiente cambia; uno vencido se rechaza pero no se
     * acepta. Si no puede, {@code PRESUPUESTO_CERRADO} o {@code PRESUPUESTO_VENCIDO}.
     */
    public EstadoPresupuesto pasarA(EstadoPresupuesto destino, LocalDate venceEl, LocalDate hoy) {
        if (destino == PENDIENTE) {
            throw new IllegalArgumentException("Un presupuesto no vuelve a PENDIENTE");
        }
        if (this != PENDIENTE) {
            throw new CoberturaException(CodigoCobertura.PRESUPUESTO_CERRADO, "El presupuesto ya no está pendiente");
        }
        if (destino == ACEPTADO && vencido(venceEl, hoy)) {
            throw new CoberturaException(CodigoCobertura.PRESUPUESTO_VENCIDO, "El presupuesto está vencido");
        }
        return destino;
    }
}
