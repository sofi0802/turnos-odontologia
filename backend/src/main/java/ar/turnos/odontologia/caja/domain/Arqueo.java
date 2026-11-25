package ar.turnos.odontologia.caja.domain;

import java.math.BigDecimal;

/**
 * Arqueo al cerrar la caja (RN-CA-06): diferencia = contado menos esperado. Con diferencia distinta de cero el motivo
 * es obligatorio; con diferencia cero no se guarda motivo.
 */
public record Arqueo(Dinero esperado, Dinero contado, BigDecimal diferencia, String motivo) {

    public static Arqueo de(Dinero esperado, Dinero contado, String motivo) {
        BigDecimal diferencia = contado.menos(esperado);
        String limpio = motivo == null || motivo.isBlank() ? null : motivo.strip();
        if (diferencia.signum() != 0 && limpio == null) {
            throw new MotivoObligatorioException("Escribí el motivo de la diferencia.");
        }
        return new Arqueo(esperado, contado, diferencia, diferencia.signum() == 0 ? null : limpio);
    }
}
