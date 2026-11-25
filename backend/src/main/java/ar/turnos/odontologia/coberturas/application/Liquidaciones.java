package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.domain.EstadoLiquidacion;
import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion;
import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion.CodigoCubierto;
import ar.turnos.odontologia.coberturas.domain.Liquidacion;
import ar.turnos.odontologia.coberturas.domain.Liquidacion.Hitos;
import ar.turnos.odontologia.coberturas.domain.Periodo;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Puerto de salida: persistencia de las liquidaciones (C-20, design D1). Cada operación es una sentencia explícita que
 * coincide con lo que permiten los triggers de V14; las operaciones sobre una liquidación existente empiezan con
 * {@link #bloquear}, así se aplican de a una (design D4).
 */
public interface Liquidaciones {

    boolean existe(Long obraSocialId, Periodo periodo);

    /** Inserta la cabecera en {@code BORRADOR} sin líneas y devuelve su id. */
    Long crear(Long obraSocialId, Periodo periodo, Hitos hitos);

    /** La liquidación con sus líneas, tomando el bloqueo de escritura de su cabecera hasta el fin de la transacción. */
    Optional<Liquidacion> bloquear(Long id);

    /** La liquidación con sus líneas, sin bloqueo. */
    Optional<Liquidacion> cargar(Long id);

    /** Inserta la línea (con su foto completa) y devuelve su id. */
    Long insertarLinea(Long liquidacionId, Long obraSocialId, LineaLiquidacion linea);

    /** Reemplaza la foto, el código y el monto de una línea de un borrador. */
    void actualizarLinea(LineaLiquidacion linea);

    void borrarLinea(Long lineaId);

    /** Guarda lo cobrado, el resultado, el motivo y quién y cuándo de una línea de una liquidación presentada. */
    void guardarConciliacion(LineaLiquidacion linea);

    /** Registra que el turno se quitó a mano de la liquidación. */
    void quitarTurno(Long liquidacionId, Long turnoId);

    /** Guarda el estado, los totales (calculados de las líneas) y los hitos de la cabecera. */
    void guardarCabecera(Liquidacion liquidacion);

    List<Resumen> listar(Long obraSocialId, EstadoLiquidacion estado);

    /**
     * Para cada línea sin código de la liquidación, los códigos activos de su prestación que su plan cubre hoy con más
     * de 0 %, ordenados por código. La clave es el id de la línea.
     */
    Map<Long, List<CodigoCubierto>> codigosDisponibles(Long liquidacionId);

    /** El código si está activo, es de la prestación de la línea y el plan de la línea lo cubre con más de 0 %. */
    Optional<CodigoCubierto> codigoDisponible(Long lineaId, Long nomencladorId);

    /** Una fila del listado: sin datos de pacientes. */
    record Resumen(Long id, Long obraSocialId, String obraSocial, String periodo, String estado, int cantidadLineas,
                   BigDecimal totalSolicitado, BigDecimal totalCobrado) {}
}
