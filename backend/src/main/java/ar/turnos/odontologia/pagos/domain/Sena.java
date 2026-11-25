package ar.turnos.odontologia.pagos.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Una seña tal como está guardada (KB 04, design D8), con el estado de su turno.
 *
 * @param estadoTurno el estado del turno vigente de la seña ({@code RESERVADO}, {@code CANCELADO}, …)
 * @param aReintegrar lo que hay que devolver; {@code reintegrado} es lo ya devuelto
 */
public record Sena(long id, long turnoId, long pacienteId, int porcentaje, BigDecimal copagoBase, BigDecimal importe,
                   EstadoSena estado, Instant venceEn, Instant pagadaEn, String referencia, String pagoExternoId,
                   String linkPago, Long cobroId, BigDecimal aReintegrar, BigDecimal reintegrado, Causa causa,
                   String estadoTurno) {

    public BigDecimal pendienteDeReintegro() {
        return aReintegrar.subtract(reintegrado);
    }

    public boolean turnoActivo() {
        return "RESERVADO".equals(estadoTurno) || "CONFIRMADO".equals(estadoTurno);
    }
}
