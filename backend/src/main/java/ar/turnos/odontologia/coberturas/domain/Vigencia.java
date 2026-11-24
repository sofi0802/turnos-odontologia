package ar.turnos.odontologia.coberturas.domain;

import java.time.LocalDate;

/**
 * Vigencia de una afiliación en días completos e inclusivos; {@code hasta} nula es una vigencia abierta (RN-OS-05).
 */
public record Vigencia(LocalDate desde, LocalDate hasta) {

    public Vigencia {
        if (desde == null) {
            throw CoberturaException.datosInvalidos("vigenteDesde", "Falta la fecha de inicio de la vigencia");
        }
        if (hasta != null && hasta.isBefore(desde)) {
            throw new CoberturaException(CodigoCobertura.VIGENCIA_INVALIDA,
                    "La fecha de fin no puede ser anterior a la de inicio");
        }
    }

    public boolean estaAbierta() {
        return hasta == null;
    }

    public boolean contiene(LocalDate fecha) {
        return !fecha.isBefore(desde) && (hasta == null || !fecha.isAfter(hasta));
    }

    /** {@code true} si las dos vigencias comparten al menos un día. */
    public boolean seSuperponeCon(Vigencia otra) {
        boolean estaTerminaAntes = hasta != null && hasta.isBefore(otra.desde);
        boolean otraTerminaAntes = otra.hasta != null && otra.hasta.isBefore(desde);
        return !estaTerminaAntes && !otraTerminaAntes;
    }

    /** Fija el fin de una vigencia abierta; una ya cerrada es {@code AFILIACION_CERRADA}. */
    public Vigencia cerrarEn(LocalDate fin) {
        if (!estaAbierta()) {
            throw new CoberturaException(CodigoCobertura.AFILIACION_CERRADA, "La afiliación ya está cerrada");
        }
        return new Vigencia(desde, fin);
    }
}
