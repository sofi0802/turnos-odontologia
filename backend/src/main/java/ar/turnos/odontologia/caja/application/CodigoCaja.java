package ar.turnos.odontologia.caja.application;

import org.springframework.http.HttpStatus;

/** Códigos estables de los rechazos de la caja (design D7). */
public enum CodigoCaja {
    CAJA_YA_ABIERTA(HttpStatus.CONFLICT),
    CAJA_CERRADA(HttpStatus.CONFLICT),
    SIN_CAJA_ABIERTA(HttpStatus.CONFLICT),
    IMPORTE_SUPERA_SALDO(HttpStatus.CONFLICT),
    COBRO_ANULADO(HttpStatus.CONFLICT),
    /** C-23: el cobro de una seña no tiene caja y no se anula desde la caja (RN-SE-08). */
    COBRO_SIN_CAJA(HttpStatus.CONFLICT),
    RECURSO_NO_ENCONTRADO(HttpStatus.NOT_FOUND);

    private final HttpStatus estado;

    CodigoCaja(HttpStatus estado) {
        this.estado = estado;
    }

    public HttpStatus estado() {
        return estado;
    }
}
