package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.application.VistasLiquidacion.LiquidacionVista;
import ar.turnos.odontologia.coberturas.application.VistasLiquidacion.ResumenVista;
import ar.turnos.odontologia.coberturas.domain.EstadoLiquidacion;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Casos de uso de liquidaciones (C-20, design D4). Delega en {@link OperacionesLiquidaciones}, que corre cada operación
 * en su transacción, y la reintenta (hasta {@value #INTENTOS} veces, operación entera) en dos casos:
 * <ul>
 *   <li>PostgreSQL elige a la transacción como víctima de un bloqueo mutuo (dos generaciones que insertan líneas en
 *       distinto orden);</li>
 *   <li>dos generaciones de obras sociales distintas se disputan un turno (posible solo si se corrigió una afiliación
 *       entre medio) y la segunda choca con {@code liquidacion_linea_turno_unico}: en el reintento, el candidato ya
 *       no incluye el turno que tomó la otra.</li>
 * </ul>
 * Cualquier otro error, incluida {@code liquidacion_periodo_unico} (dos generaciones de la misma liquidación), sale tal
 * cual: la API lo traduce a {@code 409 LIQUIDACION_EXISTENTE}.
 */
@Service
public class ServicioLiquidaciones {

    static final int INTENTOS = 3;
    private static final String TURNO_UNICO = "liquidacion_linea_turno_unico";

    private final OperacionesLiquidaciones operaciones;

    public ServicioLiquidaciones(OperacionesLiquidaciones operaciones) {
        this.operaciones = operaciones;
    }

    public LiquidacionVista generar(Long obraSocialId, String periodo, String actor) {
        return conReintento(() -> operaciones.generar(obraSocialId, periodo, actor));
    }

    public LiquidacionVista presentar(Long liquidacionId, String actor) {
        return conReintento(() -> operaciones.presentar(liquidacionId, actor));
    }

    public List<ResumenVista> listar(Long obraSocialId, EstadoLiquidacion estado) {
        return operaciones.listar(obraSocialId, estado);
    }

    public LiquidacionVista conciliar(Long liquidacionId, Long lineaId, BigDecimal montoCobrado, String motivo,
                                      String actor) {
        return conReintento(() -> operaciones.conciliar(liquidacionId, lineaId, montoCobrado, motivo, actor));
    }

    public LiquidacionVista cerrar(Long liquidacionId, String actor) {
        return conReintento(() -> operaciones.cerrar(liquidacionId, actor));
    }

    public OperacionesLiquidaciones.ArchivoCsv csv(Long liquidacionId) {
        return operaciones.csv(liquidacionId);
    }

    public LiquidacionVista obtener(Long liquidacionId) {
        return operaciones.obtener(liquidacionId);
    }

    public LiquidacionVista regenerar(Long liquidacionId, String actor) {
        return conReintento(() -> operaciones.regenerar(liquidacionId, actor));
    }

    public void quitarLinea(Long liquidacionId, Long lineaId) {
        conReintento(() -> {
            operaciones.quitarLinea(liquidacionId, lineaId);
            return null;
        });
    }

    public LiquidacionVista elegirCodigo(Long liquidacionId, Long lineaId, Long nomencladorId) {
        return conReintento(() -> operaciones.elegirCodigo(liquidacionId, lineaId, nomencladorId));
    }

    static <T> T conReintento(Supplier<T> operacion) {
        for (int intento = 1; ; intento++) {
            try {
                return operacion.get();
            } catch (PessimisticLockingFailureException e) {
                if (intento == INTENTOS) {
                    throw e;
                }
            } catch (DataIntegrityViolationException e) {
                if (intento == INTENTOS || !esTurnoDisputado(e)) {
                    throw e;
                }
            }
        }
    }

    private static boolean esTurnoDisputado(DataIntegrityViolationException e) {
        String mensaje = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
        return mensaje != null && mensaje.contains(TURNO_UNICO);
    }
}
