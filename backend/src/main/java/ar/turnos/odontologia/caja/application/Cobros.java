package ar.turnos.odontologia.caja.application;

import ar.turnos.odontologia.caja.application.Vistas.CobroVista;
import ar.turnos.odontologia.caja.domain.MedioPago;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Puerto de los cobros (design D4). Un cobro no se borra: se anula (RN-CA-07). */
public interface Cobros {

    long registrar(long cargoId, long cajaId, BigDecimal importe, MedioPago medio, String usuario, Instant ahora);

    /** El cobro con {@code FOR UPDATE}, para anularlo. */
    Optional<CobroBloqueado> bloquear(long cobroId);

    void anular(long cobroId, String usuario, Instant ahora, String motivo);

    Optional<CobroVista> porId(long cobroId);

    List<CobroVista> deLaCaja(long cajaId);

    List<CobroVista> deLosCargos(List<Long> cargoIds);

    record CobroBloqueado(long id, long cajaId, boolean anulado) {}
}
