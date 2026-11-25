package ar.turnos.odontologia.caja.domain;

/** Falta el motivo de una diferencia de arqueo o de una anulación (RN-CA-06, RN-CA-07). */
public class MotivoObligatorioException extends RuntimeException {

    public MotivoObligatorioException(String mensaje) {
        super(mensaje);
    }
}
