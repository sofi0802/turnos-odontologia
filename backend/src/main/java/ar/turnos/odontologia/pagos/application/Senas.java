package ar.turnos.odontologia.pagos.application;

import ar.turnos.odontologia.pagos.domain.EstadoSena;
import ar.turnos.odontologia.pagos.domain.Sena;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Puerto de persistencia de las señas (design D1, D8). Los métodos {@code …Bloqueada} toman la fila con
 * {@code FOR UPDATE} y tienen que llamarse dentro de una transacción.
 */
public interface Senas {

    enum Movimiento { NOTIFICACION, PAGO, REINTEGRO, REINTEGRO_FALLIDO, VENCIMIENTO, REPROGRAMACION }

    long crear(long turnoId, long pacienteId, int porcentaje, BigDecimal copagoBase, BigDecimal importe,
               Instant venceEn, String referencia, Instant ahora);

    Optional<Sena> porId(long id);

    Optional<Sena> porIdBloqueada(long id);

    Optional<Sena> porReferenciaBloqueada(String referencia);

    /** La seña no terminal (pendiente o pagada) de cada turno, o la última si no hay ninguna no terminal. */
    Map<Long, Sena> deTurnos(Collection<Long> turnoIds);

    /** Ids de las señas en esos estados, para la revisión periódica. */
    List<Long> idsEnEstados(Collection<EstadoSena> estados);

    /** Ids de las señas con reintegro pendiente. */
    List<Long> idsConReintegroPendiente();

    /**
     * Registra un movimiento. Con {@code idExterno}, si ya existe uno del mismo tipo y referencia externa no registra nada
     * y devuelve {@code false} (idempotencia, RN-SE-05 y RN-SE-09).
     */
    boolean registrarMovimiento(Long senaId, Movimiento tipo, String idExterno, BigDecimal importe, String detalle,
                                Instant ahora);

    void guardarLink(long id, String link);

    void marcarPagada(long id, String pagoExternoId, Instant pagadaEn);

    /** Registra un pago que llegó tarde: no cambia el estado, solo guarda el pago para poder reintegrarlo. */
    void registrarPagoTardio(long id, String pagoExternoId, Instant pagadaEn);

    void cambiarEstado(long id, EstadoSena estado);

    void fijarReintegro(long id, BigDecimal aReintegrar);

    void registrarReintegrado(long id, BigDecimal reintegrado);

    void moverATurno(long id, long turnoNuevo);

    void imputar(long id, long cobroId);
}
