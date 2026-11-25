package ar.turnos.odontologia.caja.application;

import ar.turnos.odontologia.caja.application.Cargos.CargoBloqueado;
import ar.turnos.odontologia.caja.application.Cobros.CobroBloqueado;
import ar.turnos.odontologia.caja.application.TurnosACobrar.TurnoACobrar;
import ar.turnos.odontologia.caja.application.Vistas.CargoVista;
import ar.turnos.odontologia.caja.application.Vistas.CobroRegistrado;
import ar.turnos.odontologia.caja.application.Vistas.CobroVista;
import ar.turnos.odontologia.caja.application.Vistas.CuentaCorrienteVista;
import ar.turnos.odontologia.caja.application.Vistas.PendientesVista;
import ar.turnos.odontologia.caja.domain.CalculoCargo;
import ar.turnos.odontologia.caja.domain.Dinero;
import ar.turnos.odontologia.caja.domain.MedioPago;
import ar.turnos.odontologia.caja.domain.MotivoObligatorioException;
import ar.turnos.odontologia.caja.domain.Tarifa;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cargos, cobros, anulaciones y cuenta corriente (RN-CA-03 a RN-CA-05, RN-CA-07). Los logs llevan ids, el medio y el
 * usuario del personal, nunca importes ni datos del paciente (RN-CA-08, design D8).
 */
public class ServicioCobros {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioCobros.class);

    private final Cajas cajas;
    private final Cargos cargos;
    private final Cobros cobros;
    private final TurnosACobrar turnos;
    private final Clock reloj;

    public ServicioCobros(Cajas cajas, Cargos cargos, Cobros cobros, TurnosACobrar turnos, Clock reloj) {
        this.cajas = cajas;
        this.cargos = cargos;
        this.cobros = cobros;
        this.turnos = turnos;
        this.reloj = reloj;
    }

    /** Barrido de design D2: crea el cargo de cada turno atendido sin cargo y con arancel. Devuelve cuántos creó. */
    @Transactional
    public int generarCargos() {
        int creados = 0;
        for (TurnoACobrar turno : turnos.atendidosSinCargo()) {
            Tarifa tarifa = Tarifa.elegir(turno.precio(), turno.afiliacionId(), turno.codigosActivos(),
                    turno.porcentajesDelPlan());
            if (cargos.insertarSiNoExiste(turno.turnoId(), turno.pacienteId(), turno.prestacionId(), turno.fecha(),
                    CalculoCargo.calcular(tarifa), reloj.instant())) {
                creados++;
            }
        }
        return creados;
    }

    @Transactional
    public PendientesVista pendientes() {
        generarCargos();
        return new PendientesVista(cargos.pendientes(), cargos.sinPrecio());
    }

    @Transactional
    public CuentaCorrienteVista cuentaCorriente(long pacienteId) {
        String paciente = cargos.nombrePaciente(pacienteId)
                .orElseThrow(() -> new CajaException(CodigoCaja.RECURSO_NO_ENCONTRADO, "El paciente no existe."));
        generarCargos();
        List<CargoVista> sinCobros = cargos.delPaciente(pacienteId);
        Map<Long, List<CobroVista>> porCargo = cobros.deLosCargos(sinCobros.stream().map(CargoVista::id).toList())
                .stream().collect(Collectors.groupingBy(CobroVista::cargoId));
        List<CargoVista> conCobros = sinCobros.stream()
                .map(c -> new CargoVista(c.id(), c.fecha(), c.inicio(), c.prestacion(), c.precio(),
                        c.porcentajeCobertura(), c.cobertura(), c.copago(), c.pagado(), c.saldo(),
                        porCargo.getOrDefault(c.id(), List.of())))
                .toList();
        BigDecimal saldoTotal = conCobros.stream().map(CargoVista::saldo).reduce(Dinero.CERO.valor(), BigDecimal::add);
        return new CuentaCorrienteVista(pacienteId, paciente, conCobros, saldoTotal);
    }

    /** Cobro de design D4: la caja abierta con {@code FOR SHARE} y el cargo con {@code FOR UPDATE}, en ese orden. */
    @Transactional
    public CobroRegistrado cobrar(long cargoId, BigDecimal importe, MedioPago medio, String usuario) {
        Dinero monto = Dinero.positivo(importe);
        long cajaId = cajas.abiertaParaCobrar()
                .orElseThrow(() -> new CajaException(CodigoCaja.SIN_CAJA_ABIERTA,
                        "No hay una caja abierta. Abrí la caja para cobrar."))
                .id();
        CargoBloqueado cargo = cargos.bloquear(cargoId)
                .orElseThrow(() -> new CajaException(CodigoCaja.RECURSO_NO_ENCONTRADO, "El cargo no existe."));
        if (monto.valor().compareTo(cargo.saldo()) > 0) {
            throw new CajaException(CodigoCaja.IMPORTE_SUPERA_SALDO, "El importe supera el saldo del cargo.");
        }
        long cobroId = cobros.registrar(cargo.id(), cajaId, monto.valor(), medio, usuario, reloj.instant());
        LOG.info("Cobro {} registrado en la caja {} por {}, medio {}", cobroId, cajaId, usuario, medio);
        return new CobroRegistrado(cobros.porId(cobroId).orElseThrow(), cargo.saldo().subtract(monto.valor()));
    }

    /**
     * C-23 (RN-SE-08): la seña pagada como un cobro {@code MERCADO_PAGO} sin caja, con el cargo bloqueado. No supera
     * el saldo: la seña ya se recortó al copago antes de llamar.
     *
     * @return el id del cobro
     */
    @Transactional
    public long imputarSena(long cargoId, BigDecimal importe) {
        Dinero monto = Dinero.positivo(importe);
        CargoBloqueado cargo = cargos.bloquear(cargoId)
                .orElseThrow(() -> new CajaException(CodigoCaja.RECURSO_NO_ENCONTRADO, "El cargo no existe."));
        if (monto.valor().compareTo(cargo.saldo()) > 0) {
            throw new CajaException(CodigoCaja.IMPORTE_SUPERA_SALDO, "La seña supera el saldo del cargo.");
        }
        long cobroId = cobros.registrarSinCaja(cargo.id(), monto.valor(), "sistema", reloj.instant());
        LOG.info("Cobro {} de una seña imputado al cargo {}, medio MERCADO_PAGO", cobroId, cargo.id());
        return cobroId;
    }

    /** Anulación de design D4: el cobro con {@code FOR UPDATE} y su caja con {@code FOR SHARE}. */
    @Transactional
    public CobroVista anular(long cobroId, String motivo, String usuario) {
        if (motivo == null || motivo.isBlank()) {
            throw new MotivoObligatorioException("Escribí el motivo de la anulación.");
        }
        CobroBloqueado cobro = cobros.bloquear(cobroId)
                .orElseThrow(() -> new CajaException(CodigoCaja.RECURSO_NO_ENCONTRADO, "El cobro no existe."));
        if (cobro.cajaId() == null) {
            throw new CajaException(CodigoCaja.COBRO_SIN_CAJA,
                    "El cobro de una seña no tiene caja y no se anula desde la caja.");
        }
        boolean cajaCerrada = cajas.bloquearParaAnular(cobro.cajaId()).map(Cajas.CajaFila::cerrada).orElse(true);
        if (cajaCerrada) {
            throw new CajaException(CodigoCaja.CAJA_CERRADA, "La caja ya está cerrada.");
        }
        if (cobro.anulado()) {
            throw new CajaException(CodigoCaja.COBRO_ANULADO, "El cobro ya estaba anulado.");
        }
        cobros.anular(cobroId, usuario, reloj.instant(), motivo.strip());
        LOG.info("Cobro {} anulado por {}", cobroId, usuario);
        return cobros.porId(cobroId).orElseThrow();
    }
}
