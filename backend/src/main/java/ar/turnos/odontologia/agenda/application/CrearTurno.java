package ar.turnos.odontologia.agenda.application;

import ar.turnos.odontologia.agenda.domain.Agenda;
import ar.turnos.odontologia.agenda.domain.CodigoRechazo;
import ar.turnos.odontologia.agenda.domain.Intervalo;
import ar.turnos.odontologia.agenda.domain.Margen;
import ar.turnos.odontologia.agenda.domain.ReferenciaInexistenteException;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.agenda.domain.TurnoRechazadoException;
import ar.turnos.odontologia.agenda.domain.reglas.ReglasDisponibilidad;
import ar.turnos.odontologia.configuracion.application.HabilitacionProfesional;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Caso de uso: crear un turno sin solapamientos por profesional ni por sillón (US-010).
 *
 * <p>Orden de validación: primero el pasado (RN-AG-09), después que existan los recursos y que estén
 * activos (RN-AG-11), que el profesional esté habilitado para la prestación (RN-AG-15), el cálculo del fin
 * (RN-AG-01), la disponibilidad del profesional (ausencia, bloqueo y horario de trabajo, C-07) y por último los
 * conflictos con margen (RN-AG-02 a RN-AG-05).
 */
public class CrearTurno {

    /** Sin control de especialidades: lo usan los constructores anteriores a RN-AG-15 y sus tests (design D4). */
    static final HabilitacionProfesional TODOS_HABILITADOS = (profesionalId, prestacionId) -> true;

    private final TurnoRepository turnos;
    private final HabilitacionProfesional habilitacion;
    private final RecursosAgenda recursos;
    private final GeneradorNumeroReserva numeros;
    private final Clock reloj;
    private final Margen margen;
    private final Agenda agenda;
    private final DisponibilidadProfesional disponibilidad;
    private final ZoneId zona;

    /** Sin reglas de disponibilidad: es el constructor anterior a C-07 y lo usan sus tests (design D2). */
    public CrearTurno(TurnoRepository turnos, RecursosAgenda recursos, GeneradorNumeroReserva numeros,
            Clock reloj, Margen margen) {
        this(turnos, recursos, numeros, reloj, margen, DisponibilidadProfesional.SIN_RESTRICCIONES, ZoneOffset.UTC);
    }

    /** @param zona zona horaria de la agenda, para ubicar el turno en las franjas semanales (C-07, D4) */
    public CrearTurno(TurnoRepository turnos, RecursosAgenda recursos, GeneradorNumeroReserva numeros,
            Clock reloj, Margen margen, DisponibilidadProfesional disponibilidad, ZoneId zona) {
        this(turnos, recursos, numeros, reloj, margen, disponibilidad, zona, TODOS_HABILITADOS);
    }

    /** @param habilitacion especialidades de cada profesional (RN-AG-15, change validar-habilitacion-profesional) */
    public CrearTurno(TurnoRepository turnos, RecursosAgenda recursos, GeneradorNumeroReserva numeros,
            Clock reloj, Margen margen, DisponibilidadProfesional disponibilidad, ZoneId zona,
            HabilitacionProfesional habilitacion) {
        this.habilitacion = Objects.requireNonNull(habilitacion, "habilitacion");
        this.disponibilidad = Objects.requireNonNull(disponibilidad, "disponibilidad");
        this.zona = Objects.requireNonNull(zona, "zona");
        this.turnos = Objects.requireNonNull(turnos, "turnos");
        this.recursos = Objects.requireNonNull(recursos, "recursos");
        this.numeros = Objects.requireNonNull(numeros, "numeros");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
        this.margen = Objects.requireNonNull(margen, "margen");
        this.agenda = new Agenda(margen);
    }

    /** Crear un turno nuevo: siempre exige que el profesional esté habilitado (RN-AG-15). */
    public Turno ejecutar(CrearTurnoCommand comando) {
        return turnos.guardar(preparar(comando, null, true));
    }

    /**
     * Firma original: el fin se calcula con la duración de la prestación y no se controla la habilitación. La usa
     * {@code ReasignarTurno}, que la controla por su cuenta solo al cambiar de profesional (C-07, design D1).
     */
    Turno preparar(CrearTurnoCommand comando, Long reemplazaA) {
        return preparar(comando, reemplazaA, false);
    }

    /**
     * Arma y verifica un turno nuevo sin guardarlo. Si {@code reemplazaA} no es nulo, ese turno no cuenta
     * como conflicto: es el original de una reprogramación, que se cancela en la misma operación.
     *
     * @param exigirHabilitacion si se controla RN-AG-15: crear siempre; reprogramar solo al cambiar de profesional
     */
    Turno preparar(CrearTurnoCommand comando, Long reemplazaA, boolean exigirHabilitacion) {
        if (comando.inicio().isBefore(reloj.instant())) {
            throw new TurnoRechazadoException(CodigoRechazo.INICIO_EN_EL_PASADO,
                    "No se puede crear un turno en el pasado");
        }
        return armar(comando, reemplazaA, null, true, exigirHabilitacion);
    }

    /**
     * Verifica la extensión del turno {@code turnoId}, que puede estar ya iniciado: lo que no puede quedar en el
     * pasado es el nuevo fin, no el inicio. Es el único camino que omite el control de inicio de
     * {@link #preparar}: crear y reprogramar siguen rechazando inicios pasados (RN-AG-09). La extensión puede
     * pasar el fin de la franja de trabajo (RN-AG-14), pero no entrar en una ausencia o un bloqueo (C-07).
     */
    Turno prepararExtension(CrearTurnoCommand comando, Long turnoId, Instant nuevoFin) {
        if (!nuevoFin.isAfter(reloj.instant())) {
            throw new TurnoRechazadoException(CodigoRechazo.DATOS_INVALIDOS,
                    "El nuevo fin debe ser posterior al momento actual");
        }
        // La extensión conserva el profesional y la prestación del turno ya dado: no se vuelve a exigir RN-AG-15.
        return armar(comando, turnoId, nuevoFin, false, false);
    }

    private Turno armar(CrearTurnoCommand comando, Long reemplazaA, Instant finAlternativo, boolean exigirHorario,
            boolean exigirHabilitacion) {
        RecursosAgenda.Prestacion prestacion = verificarRecursos(comando);
        if (exigirHabilitacion) {
            verificarHabilitacion(habilitacion, comando.profesionalId(), comando.prestacionId());
        }
        Instant fin = finAlternativo != null ? finAlternativo : comando.inicio().plus(prestacion.duracion());
        Intervalo intervalo = new Intervalo(comando.inicio(), fin);
        ReglasDisponibilidad.verificar(intervalo, disponibilidad.periodos(comando.profesionalId(), intervalo),
                zona, exigirHorario);

        List<Turno> cercanos = turnos.buscarQueIntersecan(comando.profesionalId(),
                comando.sillonId(), intervalo.ampliadoPor(margen)).stream()
                .filter(t -> reemplazaA == null || !reemplazaA.equals(t.id()))
                .toList();
        Turno nuevo = Turno.reservar(comando.pacienteId(), comando.profesionalId(),
                comando.sillonId(), comando.prestacionId(), intervalo, numeros.generar());
        agenda.verificar(nuevo, cercanos);
        return nuevo;
    }

    private RecursosAgenda.Prestacion verificarRecursos(CrearTurnoCommand comando) {
        if (!recursos.existePaciente(comando.pacienteId())) {
            throw new ReferenciaInexistenteException("pacienteId");
        }
        boolean profesionalActivo = existente(recursos.profesionalActivo(comando.profesionalId()), "profesionalId");
        boolean sillonActivo = existente(recursos.sillonActivo(comando.sillonId()), "sillonId");
        RecursosAgenda.Prestacion prestacion = existente(recursos.prestacion(comando.prestacionId()), "prestacionId");

        if (!profesionalActivo) {
            throw inactivo("El profesional está inactivo");
        }
        if (!sillonActivo) {
            throw inactivo("El sillón está inactivo");
        }
        if (!prestacion.activa()) {
            throw inactivo("La prestación está inactiva");
        }
        return prestacion;
    }

    /** RN-AG-15: va después de recursos inactivos y antes de ausencia, bloqueo, horario y solapamientos. */
    static void verificarHabilitacion(HabilitacionProfesional habilitacion, Long profesionalId, Long prestacionId) {
        if (!habilitacion.estaHabilitado(profesionalId, prestacionId)) {
            throw new TurnoRechazadoException(CodigoRechazo.PROFESIONAL_NO_HABILITADO,
                    "El profesional no está habilitado para la prestación");
        }
    }

    private static <T> T existente(Optional<T> valor, String campo) {
        return valor.orElseThrow(() -> new ReferenciaInexistenteException(campo));
    }

    private static TurnoRechazadoException inactivo(String mensaje) {
        return new TurnoRechazadoException(CodigoRechazo.RECURSO_INACTIVO, mensaje);
    }
}
