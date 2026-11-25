package ar.turnos.odontologia.coberturas.domain;

import java.math.BigDecimal;

/**
 * Lo que se registra al conciliar una línea (RN-LQ-06): el monto cobrado, el resultado que se deriva de él y, solo si
 * la línea queda rechazada, el motivo (1 a 200 caracteres sin los espacios de los extremos). Los mensajes de error
 * son fijos: no repiten el importe ni el motivo recibidos.
 */
public record Conciliacion(BigDecimal montoCobrado, ResultadoConciliacion resultado, String motivo) {

    static final int MOTIVO_MAXIMO = 200;

    public static Conciliacion de(BigDecimal cobrado, String motivoRecibido, BigDecimal solicitado) {
        MontoCobrado monto = MontoCobrado.de(cobrado, solicitado);
        ResultadoConciliacion resultado = ResultadoConciliacion.de(monto.valor(), solicitado);
        String motivo = motivoRecibido == null || motivoRecibido.isBlank() ? null : motivoRecibido.strip();
        if (resultado == ResultadoConciliacion.RECHAZADA) {
            if (motivo == null) {
                throw new CoberturaException(CodigoCobertura.MOTIVO_REQUERIDO, "motivo",
                        "Una línea rechazada necesita un motivo");
            }
            if (motivo.length() > MOTIVO_MAXIMO) {
                throw CoberturaException.datosInvalidos("motivo", "El motivo es demasiado largo");
            }
        } else if (motivo != null) {
            throw CoberturaException.datosInvalidos("motivo", "Solo una línea rechazada lleva motivo");
        }
        return new Conciliacion(monto.valor(), resultado, motivo);
    }
}
