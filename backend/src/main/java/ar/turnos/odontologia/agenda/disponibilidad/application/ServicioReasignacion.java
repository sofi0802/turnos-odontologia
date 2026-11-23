package ar.turnos.odontologia.agenda.disponibilidad.application;

import ar.turnos.odontologia.agenda.application.ReasignarTurnoCommand;
import ar.turnos.odontologia.agenda.application.TurnoRepository;
import ar.turnos.odontologia.agenda.disponibilidad.application.RegistroDisponibilidad.Clase;
import ar.turnos.odontologia.agenda.disponibilidad.application.RegistroDisponibilidad.Periodo;
import ar.turnos.odontologia.agenda.domain.CodigoRechazo;
import ar.turnos.odontologia.agenda.domain.Intervalo;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.agenda.domain.TurnoRechazadoException;
import ar.turnos.odontologia.agenda.infrastructure.ServicioAgenda;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reasignación de un turno afectado por una ausencia o licencia (RN-AG-16, design D10) o por un bloqueo (RN-AG-19,
 * design D16). Verifica lo propio del período, delega la reasignación en la agenda (transacción y auditoría) y, ya
 * confirmada, pide el aviso al paciente. Un aviso que falla no deshace nada.
 */
@Service
public class ServicioReasignacion {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioReasignacion.class);
    private static final AvisoReasignacion INACTIVO = new AvisoReasignacionInactivo();

    private final RegistroDisponibilidad registro;
    private final TurnoRepository turnos;
    private final ServicioAgenda agenda;
    private final ObjectProvider<AvisoReasignacion> avisos;
    private final TransactionTemplate lectura;

    /**
     * @param avisos el adaptador de {@code notificaciones}, si existe; si no, se usa uno inactivo. Se resuelve en
     *     cada llamada para no depender del orden en que Spring registra los beans de cada módulo.
     */
    public ServicioReasignacion(RegistroDisponibilidad registro, TurnoRepository turnos, ServicioAgenda agenda,
                                ObjectProvider<AvisoReasignacion> avisos, PlatformTransactionManager transacciones) {
        this.registro = registro;
        this.turnos = turnos;
        this.agenda = agenda;
        this.avisos = avisos;
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setReadOnly(true);
    }

    /**
     * @return el turno nuevo
     * @param clase si el período citado es una ausencia o un bloqueo (design D16); el resto del flujo es el mismo
     * @throws RecursoNoEncontradoException si no existe el profesional o el período no es suyo (404)
     * @throws TurnoRechazadoException con {@code TURNO_NO_AFECTADO} si el turno no es del profesional o no se cruza
     *     con el período, o con el rechazo de la agenda para el turno nuevo (409)
     */
    public Turno reasignar(Long profesionalId, Clase clase, Long periodoId, Long turnoId, Long nuevoProfesionalId,
                           Instant nuevoInicio, Long nuevoSillonId, String actor) {
        lectura.executeWithoutResult(estado -> verificarAfectado(profesionalId, clase, periodoId, turnoId));
        Turno nuevo = agenda.reasignar(
                new ReasignarTurnoCommand(turnoId, nuevoProfesionalId, nuevoInicio, nuevoSillonId), actor);
        pedirAviso(nuevo.id());
        return nuevo;
    }

    private void verificarAfectado(Long profesionalId, Clase clase, Long periodoId, Long turnoId) {
        if (!registro.existeProfesional(profesionalId)) {
            throw new RecursoNoEncontradoException("el profesional");
        }
        String nombre = clase == Clase.BLOQUEO ? "el bloqueo" : "la ausencia";
        Periodo periodoCitado = registro.periodo(profesionalId, clase, periodoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(nombre));
        Intervalo periodo = new Intervalo(periodoCitado.periodo().inicio(), periodoCitado.periodo().fin());
        boolean afectado = turnos.buscarQueIntersecan(profesionalId, null, periodo).stream()
                .anyMatch(t -> t.id().equals(turnoId) && t.profesionalId().equals(profesionalId)
                        && periodoCitado.periodo().seCruzaCon(t.intervalo()));
        if (!afectado) {
            throw new TurnoRechazadoException(CodigoRechazo.TURNO_NO_AFECTADO,
                    "El turno no es del profesional o no se cruza con " + nombre);
        }
    }

    private void pedirAviso(Long turnoId) {
        try {
            avisos.getIfAvailable(() -> INACTIVO).avisar(turnoId);
        } catch (RuntimeException e) {
            // sin datos del paciente: solo el id del turno y el tipo de error
            LOG.warn("No se pudo pedir el aviso de reasignación del turno {}: {}", turnoId,
                    e.getClass().getSimpleName());
        }
    }
}
