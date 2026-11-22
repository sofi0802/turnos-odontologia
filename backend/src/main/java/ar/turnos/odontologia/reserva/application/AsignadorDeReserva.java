package ar.turnos.odontologia.reserva.application;

import ar.turnos.odontologia.agenda.domain.CodigoRechazo;
import ar.turnos.odontologia.agenda.domain.Intervalo;
import ar.turnos.odontologia.agenda.domain.ReferenciaInexistenteException;
import ar.turnos.odontologia.agenda.domain.TurnoRechazadoException;
import ar.turnos.odontologia.reserva.application.BuscadorDeHuecos.Combinacion;
import ar.turnos.odontologia.reserva.application.CatalogoReserva.Prestacion;
import ar.turnos.odontologia.reserva.application.CatalogoReserva.Profesional;
import ar.turnos.odontologia.reserva.domain.ReservaRechazadaException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Validación y asignación que comparten la reserva y la reprogramación online (design D5, D6, D7): valida el pedido,
 * elige la combinación de profesional y sillón, la escribe por la agenda y, si otro paciente ganó la carrera, prueba
 * la siguiente antes de rechazar. Traduce los rechazos de la agenda para no revelar ausencias ni bloqueos.
 */
final class AsignadorDeReserva {

    /** El otro paciente ganó el horario entre el cálculo y la escritura: se prueba otra combinación. */
    private static final Set<CodigoRechazo> CARRERA = Set.of(CodigoRechazo.SOLAPAMIENTO_PROFESIONAL,
            CodigoRechazo.SOLAPAMIENTO_SILLON, CodigoRechazo.MARGEN_INSUFICIENTE);

    /** Motivos por los que un horario no se ofrece: se informan todos como {@code SIN_DISPONIBILIDAD}. */
    private static final Set<CodigoRechazo> NO_SE_OFRECE = Set.of(CodigoRechazo.PROFESIONAL_AUSENTE,
            CodigoRechazo.AGENDA_BLOQUEADA, CodigoRechazo.FUERA_DE_HORARIO, CodigoRechazo.SIN_SILLON_DISPONIBLE);

    /** Un pedido ya validado: lo que hace falta para calcular y escribir. */
    record Solicitud(Prestacion prestacion, Instant inicio, List<Profesional> candidatos, Long turnoIgnoradoId) {}

    private final CatalogoReserva catalogo;
    private final BuscadorDeHuecos buscador;
    private final Clock reloj;
    private final ZoneId zona;
    private final int maximoAlternativas;

    AsignadorDeReserva(CatalogoReserva catalogo, BuscadorDeHuecos buscador, Clock reloj, ZoneId zona,
                       int maximoAlternativas) {
        this.catalogo = Objects.requireNonNull(catalogo, "catalogo");
        this.buscador = Objects.requireNonNull(buscador, "buscador");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
        this.zona = Objects.requireNonNull(zona, "zona");
        this.maximoAlternativas = maximoAlternativas;
    }

    /**
     * @param profesionalId el profesional elegido, o {@code null} para "sin preferencia"
     * @param turnoIgnoradoId el turno original de una reprogramación, o {@code null}
     * @throws ReferenciaInexistenteException si la prestación o el profesional no existen
     * @throws ReservaRechazadaException {@code RECURSO_INACTIVO}, {@code PROFESIONAL_NO_HABILITADO} o
     *     {@code INICIO_EN_EL_PASADO}
     */
    Solicitud preparar(Long prestacionId, Long profesionalId, Instant inicio, Long turnoIgnoradoId) {
        Prestacion prestacion = catalogo.prestacion(prestacionId)
                .orElseThrow(() -> new ReferenciaInexistenteException("prestacionId"));
        Profesional elegido = profesionalId == null ? null : catalogo.profesional(profesionalId)
                .orElseThrow(() -> new ReferenciaInexistenteException("profesionalId"));

        if (!prestacion.activa()) {
            throw ReservaRechazadaException.de(CodigoRechazo.RECURSO_INACTIVO, "La prestación está inactiva");
        }
        if (elegido != null && !elegido.activo()) {
            throw ReservaRechazadaException.de(CodigoRechazo.RECURSO_INACTIVO, "El profesional está inactivo");
        }
        if (elegido != null && !catalogo.estaHabilitado(elegido.id(), prestacionId)) {
            throw ReservaRechazadaException.de(CodigoRechazo.PROFESIONAL_NO_HABILITADO,
                    "El profesional no está habilitado para la prestación");
        }
        if (inicio.isBefore(reloj.instant())) {
            throw ReservaRechazadaException.de(CodigoRechazo.INICIO_EN_EL_PASADO,
                    "No se puede reservar un turno en el pasado");
        }
        List<Profesional> candidatos = elegido != null ? List.of(elegido)
                : catalogo.profesionalesHabilitados(prestacionId);
        return new Solicitud(prestacion, inicio, candidatos, turnoIgnoradoId);
    }

    /**
     * Prueba las combinaciones libres de la más a la menos preferida hasta que la agenda acepta una.
     *
     * @param escribir la escritura por la agenda con la combinación elegida
     * @throws ReservaRechazadaException {@code SIN_DISPONIBILIDAD} (con alternativas) si no queda ninguna combinación
     */
    <T> T asignar(Solicitud solicitud, Function<Combinacion, T> escribir) {
        Set<Combinacion> descartadas = new HashSet<>();
        while (true) {
            List<Combinacion> combinaciones = buscador.combinaciones(solicitud.inicio(), solicitud.prestacion(),
                    solicitud.candidatos(), solicitud.turnoIgnoradoId()).stream()
                    .filter(c -> !descartadas.contains(c))
                    .toList();
            if (combinaciones.isEmpty()) {
                throw ReservaRechazadaException.sinDisponibilidad(alternativas(solicitud));
            }
            Combinacion elegida = combinaciones.get(0);
            try {
                return escribir.apply(elegida);
            } catch (TurnoRechazadoException e) {
                if (CARRERA.contains(e.codigo())) {
                    descartadas.add(elegida);
                } else if (NO_SE_OFRECE.contains(e.codigo())) {
                    throw ReservaRechazadaException.sinDisponibilidad(alternativas(solicitud));
                } else {
                    throw ReservaRechazadaException.de(e.codigo(), e.getMessage());
                }
            }
        }
    }

    /** Hasta {@code maximoAlternativas} horarios libres del mismo día posteriores al pedido, con el mismo criterio. */
    private List<Intervalo> alternativas(Solicitud solicitud) {
        return buscador.horariosLibres(solicitud.inicio().atZone(zona).toLocalDate(), solicitud.prestacion(),
                        solicitud.candidatos(), solicitud.turnoIgnoradoId()).stream()
                .filter(horario -> horario.inicio().isAfter(solicitud.inicio()))
                .limit(maximoAlternativas)
                .toList();
    }
}
