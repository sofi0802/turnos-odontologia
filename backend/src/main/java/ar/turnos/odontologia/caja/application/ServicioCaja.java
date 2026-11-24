package ar.turnos.odontologia.caja.application;

import ar.turnos.odontologia.caja.application.Cajas.CajaFila;
import ar.turnos.odontologia.caja.application.Vistas.CajaVista;
import ar.turnos.odontologia.caja.application.Vistas.CobroVista;
import ar.turnos.odontologia.caja.domain.Arqueo;
import ar.turnos.odontologia.caja.domain.Dinero;
import ar.turnos.odontologia.caja.domain.MedioPago;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de la caja diaria (RN-CA-02, RN-CA-06): abrir, consultar, cerrar con arqueo e historial. Los logs llevan
 * ids y usuarios del personal, nunca importes (design D8).
 */
public class ServicioCaja {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioCaja.class);
    private static final int MAXIMO_DIAS_HISTORIAL = 92;

    private final Cajas cajas;
    private final Cobros cobros;
    private final Clock reloj;
    private final ZoneId zona;

    public ServicioCaja(Cajas cajas, Cobros cobros, Clock reloj, ZoneId zona) {
        this.cajas = cajas;
        this.cobros = cobros;
        this.reloj = reloj;
        this.zona = zona;
    }

    @Transactional
    public CajaVista abrir(BigDecimal montoInicial, String usuario) {
        Dinero monto = Dinero.de(montoInicial);
        Optional<CajaFila> abierta = cajas.abierta();
        if (abierta.isPresent()) {
            throw yaAbierta(abierta.get().id());
        }
        long id = cajas.abrir(LocalDate.now(reloj.withZone(zona)), monto.valor(), usuario, reloj.instant())
                .orElseThrow(() -> yaAbierta(cajas.abierta().map(CajaFila::id).orElse(null)));
        LOG.info("Caja {} abierta por {}", id, usuario);
        return vista(cajas.porId(id).orElseThrow(), true);
    }

    @Transactional(readOnly = true)
    public Optional<CajaVista> abierta() {
        return cajas.abierta().map(c -> vista(c, true));
    }

    @Transactional
    public CajaVista cerrar(long cajaId, BigDecimal efectivoContado, String motivo, String usuario) {
        Dinero contado = Dinero.de(efectivoContado);
        CajaFila caja = cajas.bloquearParaCerrar(cajaId)
                .orElseThrow(() -> new CajaException(CodigoCaja.RECURSO_NO_ENCONTRADO, "La caja no existe."));
        if (caja.cerrada()) {
            throw new CajaException(CodigoCaja.CAJA_CERRADA, "La caja ya está cerrada.");
        }
        Map<MedioPago, BigDecimal> totales = totalesDe(caja.id());
        Arqueo arqueo = Arqueo.de(esperado(caja, totales), contado, motivo);
        cajas.cerrar(caja.id(), usuario, reloj.instant(), arqueo);
        LOG.info("Caja {} cerrada por {}", caja.id(), usuario);
        return vista(cajas.porId(caja.id()).orElseThrow(), false);
    }

    @Transactional(readOnly = true)
    public List<CajaVista> historial(LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde)) {
            throw new PeriodoInvalidoException("La fecha final tiene que ser igual o posterior a la inicial.");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAXIMO_DIAS_HISTORIAL) {
            throw new PeriodoInvalidoException("El período no puede superar los " + MAXIMO_DIAS_HISTORIAL + " días.");
        }
        List<CajaFila> filas = cajas.entre(desde, hasta);
        Map<Long, Map<MedioPago, BigDecimal>> totales = cajas.totales(filas.stream().map(CajaFila::id).toList());
        return filas.stream().map(c -> vista(c, completar(totales.get(c.id())), List.of())).toList();
    }

    private static CajaException yaAbierta(Long cajaId) {
        return new CajaException(CodigoCaja.CAJA_YA_ABIERTA, "Ya hay una caja abierta. Cerrala antes de abrir otra.",
                cajaId);
    }

    private Map<MedioPago, BigDecimal> totalesDe(long cajaId) {
        return completar(cajas.totales(List.of(cajaId)).get(cajaId));
    }

    private static Map<MedioPago, BigDecimal> completar(Map<MedioPago, BigDecimal> parciales) {
        Map<MedioPago, BigDecimal> totales = new EnumMap<>(MedioPago.class);
        for (MedioPago medio : MedioPago.values()) {
            BigDecimal valor = parciales == null ? null : parciales.get(medio);
            totales.put(medio, valor == null ? Dinero.CERO.valor() : valor);
        }
        return totales;
    }

    private static Dinero esperado(CajaFila caja, Map<MedioPago, BigDecimal> totales) {
        return Dinero.de(caja.montoInicial()).mas(Dinero.de(totales.get(MedioPago.EFECTIVO)));
    }

    private CajaVista vista(CajaFila caja, boolean conCobros) {
        return vista(caja, totalesDe(caja.id()), conCobros ? cobros.deLaCaja(caja.id()) : List.of());
    }

    private static CajaVista vista(CajaFila caja, Map<MedioPago, BigDecimal> totales, List<CobroVista> cobros) {
        return new CajaVista(caja.id(), caja.fecha(), caja.montoInicial(), caja.abiertaPor(), caja.abiertaEn(),
                caja.cerradaPor(), caja.cerradaEn(), totales, esperado(caja, totales).valor(), caja.efectivoContado(),
                caja.diferencia(), caja.motivoDiferencia(), cobros);
    }
}
