package ar.turnos.odontologia.pagos.application;

import ar.turnos.odontologia.caja.application.TurnosACobrar;
import ar.turnos.odontologia.caja.domain.Tarifa;
import ar.turnos.odontologia.pagos.domain.CalculoSena;
import ar.turnos.odontologia.pagos.domain.CalculoSena.SenaCalculada;
import ar.turnos.odontologia.pagos.domain.ConfiguracionSena;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Crea la seña pendiente de un turno recién reservado online (RN-SE-02 a RN-SE-04, design D3). Primero la guarda y
 * después pide el link: si la pasarela no responde, la seña queda sin link y el paciente pide otro.
 */
public class CrearSena {

    /** Lo único que describe el pago para Mercado Pago (RN-SE-11). */
    public static final String DESCRIPCION = "Seña de turno";

    private static final Logger LOG = LoggerFactory.getLogger(CrearSena.class);
    private static final char[] ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final ConfiguracionSenas configuracion;
    private final TurnosACobrar turnos;
    private final Senas senas;
    private final PasarelaPago pasarela;
    private final Clock reloj;
    private final SecureRandom azar = new SecureRandom();

    public CrearSena(ConfiguracionSenas configuracion, TurnosACobrar turnos, Senas senas, PasarelaPago pasarela,
                     Clock reloj) {
        this.configuracion = configuracion;
        this.turnos = turnos;
        this.senas = senas;
        this.pasarela = pasarela;
        this.reloj = reloj;
    }

    /** @return el id de la seña creada, o vacío si el turno no pide seña */
    public Optional<Long> ejecutar(long turnoId, long pacienteId) {
        ConfiguracionSena actual = configuracion.leer();
        if (!actual.activas()) {
            return Optional.empty();
        }
        Tarifa tarifa = turnos.tarifaDe(turnoId)
                .map(t -> Tarifa.elegir(t.precio(), t.afiliacionId(), t.codigosActivos(), t.porcentajesDelPlan()))
                .orElse(null);
        Optional<SenaCalculada> calculada = CalculoSena.calcular(tarifa, actual.porcentaje());
        if (calculada.isEmpty()) {
            return Optional.empty();
        }
        Instant ahora = reloj.instant();
        Instant vence = ahora.plus(Duration.ofMinutes(actual.plazoMinutos()));
        String referencia = nuevaReferencia();
        SenaCalculada sena = calculada.get();
        long id = senas.crear(turnoId, pacienteId, sena.porcentaje(), sena.copagoBase(), sena.importe(), vence,
                referencia, ahora);
        try {
            senas.guardarLink(id, pasarela.crearLink(referencia, sena.importe(), DESCRIPCION, vence));
        } catch (PasarelaNoDisponibleException e) {
            LOG.warn("Seña {} creada sin link: la pasarela no respondió", id);
        }
        LOG.info("Seña {} creada, referencia {}", id, referencia);
        return Optional.of(id);
    }

    /** Referencia opaca y aleatoria: no contiene datos del paciente ni del turno (RN-SE-11). */
    private String nuevaReferencia() {
        StringBuilder referencia = new StringBuilder("S-");
        for (int i = 0; i < 16; i++) {
            referencia.append(ALFABETO[azar.nextInt(ALFABETO.length)]);
        }
        return referencia.toString();
    }
}
