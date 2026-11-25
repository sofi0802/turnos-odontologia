package ar.turnos.odontologia.pagos.infrastructure;

import ar.turnos.odontologia.pagos.application.CancelacionDelSistema;
import ar.turnos.odontologia.pagos.application.CrearSena;
import ar.turnos.odontologia.pagos.application.PasarelaNoDisponibleException;
import ar.turnos.odontologia.pagos.application.PasarelaPago;
import ar.turnos.odontologia.pagos.application.Senas;
import ar.turnos.odontologia.pagos.domain.EstadoSena;
import ar.turnos.odontologia.pagos.domain.Sena;
import ar.turnos.odontologia.reserva.application.SenasDeReserva;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

/** Adaptador del puerto {@link SenasDeReserva} de la reserva online (design D2, D3). */
class SenasParaReserva implements SenasDeReserva {

    private static final Logger LOG = LoggerFactory.getLogger(SenasParaReserva.class);

    private final CrearSena crear;
    private final Senas senas;
    private final PasarelaPago pasarela;
    private final CancelacionDelSistema cancelacion;

    SenasParaReserva(CrearSena crear, Senas senas, PasarelaPago pasarela, CancelacionDelSistema cancelacion) {
        this.crear = crear;
        this.senas = senas;
        this.pasarela = pasarela;
        this.cancelacion = cancelacion;
    }

    @Override
    public Optional<VistaSena> crearPara(long turnoId, long pacienteId) {
        Optional<Long> creada;
        try {
            creada = crear.ejecutar(turnoId, pacienteId);
        } catch (DataAccessException e) {
            // No queda un turno online sin la seña que pedía la configuración (design D3).
            LOG.error("No se pudo guardar la seña del turno {}; se cancela el turno", turnoId);
            cancelacion.cancelar(turnoId);
            throw new SenaNoDisponibleException("SENA_NO_DISPONIBLE",
                    "No pudimos registrar la seña. Probá reservar de nuevo en unos minutos.");
        }
        return creada.flatMap(senas::porId).map(SenasParaReserva::vista);
    }

    @Override
    public Map<Long, VistaSena> deTurnos(Collection<Long> turnoIds) {
        Map<Long, VistaSena> vistas = new LinkedHashMap<>();
        senas.deTurnos(turnoIds).forEach((turno, sena) -> vistas.put(turno, vista(sena)));
        return vistas;
    }

    @Override
    public String nuevoLink(long turnoId) {
        Sena sena = senas.deTurnos(List.of(turnoId)).get(turnoId);
        if (sena == null || sena.estado() != EstadoSena.PENDIENTE) {
            throw new SenaNoPendienteException();
        }
        String link;
        try {
            link = pasarela.crearLink(sena.referencia(), sena.importe(), CrearSena.DESCRIPCION, sena.venceEn());
        } catch (PasarelaNoDisponibleException e) {
            throw new SenaNoDisponibleException("PASARELA_NO_DISPONIBLE",
                    "Mercado Pago no respondió. Probá de nuevo en unos minutos.");
        }
        senas.guardarLink(sena.id(), link);
        return link;
    }

    private static VistaSena vista(Sena sena) {
        boolean pendiente = sena.estado() == EstadoSena.PENDIENTE;
        return new VistaSena(sena.estado().name(), sena.importe(), sena.venceEn(), pendiente ? sena.linkPago() : null);
    }
}
