package ar.turnos.odontologia.agenda.application;

import ar.turnos.odontologia.agenda.domain.Agenda;
import ar.turnos.odontologia.agenda.domain.CodigoRechazo;
import ar.turnos.odontologia.agenda.domain.Intervalo;
import ar.turnos.odontologia.agenda.domain.Margen;
import ar.turnos.odontologia.agenda.domain.NumeroReserva;
import ar.turnos.odontologia.agenda.domain.ReferenciaInexistenteException;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.agenda.domain.TurnoRechazadoException;
import ar.turnos.odontologia.agenda.domain.reglas.ReglasDisponibilidad;
import ar.turnos.odontologia.configuracion.application.HabilitacionProfesional;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Caso de uso: alta de un sobreturno (US-013, RN-AG-06). El sillón lo elige el servidor entre los activos
 * libres con margen, en orden determinista; se relaja solo la restricción del profesional. El rechazo por
 * sillón se informa siempre como {@code SIN_SILLON_DISPONIBLE}, incluso si lo detecta la base (D4).
 *
 * <p>C-07: el sobreturno puede quedar fuera de las franjas de trabajo, pero no durante una ausencia, licencia o
 * bloqueo del profesional, sin importar el sillón.
 */
public class CrearSobreturno {

    private final TurnoRepository turnos;
    private final RecursosAgenda recursos;
    private final SillonesDisponibles sillonesDisponibles;
    private final GeneradorNumeroReserva numeros;
    private final Clock reloj;
    private final Margen margen;
    private final Agenda agenda;
    private final DisponibilidadProfesional disponibilidad;
    private final ZoneId zona;
    private final HabilitacionProfesional habilitacion;

    /** Sin reglas de disponibilidad: es el constructor anterior a C-07 y lo usan sus tests (design D2). */
    public CrearSobreturno(TurnoRepository turnos, RecursosAgenda recursos,
            SillonesDisponibles sillonesDisponibles, GeneradorNumeroReserva numeros,
            Clock reloj, Margen margen) {
        this(turnos, recursos, sillonesDisponibles, numeros, reloj, margen,
                DisponibilidadProfesional.SIN_RESTRICCIONES, ZoneOffset.UTC);
    }

    public CrearSobreturno(TurnoRepository turnos, RecursosAgenda recursos,
            SillonesDisponibles sillonesDisponibles, GeneradorNumeroReserva numeros,
            Clock reloj, Margen margen, DisponibilidadProfesional disponibilidad, ZoneId zona) {
        this(turnos, recursos, sillonesDisponibles, numeros, reloj, margen, disponibilidad, zona,
                CrearTurno.TODOS_HABILITADOS);
    }

    /**
     * @param habilitacion especialidades de cada profesional: RN-AG-06 exceptúa al sobreturno de la agenda del
     *                     profesional, no de RN-AG-15 (change validar-habilitacion-profesional, design D3)
     */
    public CrearSobreturno(TurnoRepository turnos, RecursosAgenda recursos,
            SillonesDisponibles sillonesDisponibles, GeneradorNumeroReserva numeros,
            Clock reloj, Margen margen, DisponibilidadProfesional disponibilidad, ZoneId zona,
            HabilitacionProfesional habilitacion) {
        this.habilitacion = Objects.requireNonNull(habilitacion, "habilitacion");
        this.disponibilidad = Objects.requireNonNull(disponibilidad, "disponibilidad");
        this.zona = Objects.requireNonNull(zona, "zona");
        this.turnos = Objects.requireNonNull(turnos, "turnos");
        this.recursos = Objects.requireNonNull(recursos, "recursos");
        this.sillonesDisponibles = Objects.requireNonNull(sillonesDisponibles, "sillonesDisponibles");
        this.numeros = Objects.requireNonNull(numeros, "numeros");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
        this.margen = Objects.requireNonNull(margen, "margen");
        this.agenda = new Agenda(margen);
    }

    public Turno ejecutar(CrearSobreturnoCommand comando) {
        return ejecutar(comando, Set.of());
    }

    /**
     * @param descartados sillones que una tentativa anterior encontró tomados por otra operación: no se prueban.
     * @throws SillonTomadoException si la base rechaza el insert en un sillón que el dominio vio libre
     */
    public Turno ejecutar(CrearSobreturnoCommand comando, Set<Long> descartados) {
        validar(comando);
        RecursosAgenda.Prestacion prestacion = verificarRecursos(comando);
        CrearTurno.verificarHabilitacion(habilitacion, comando.profesionalId(), comando.prestacionId());
        Intervalo intervalo = new Intervalo(comando.inicio(), comando.inicio().plus(prestacion.duracion()));
        ReglasDisponibilidad.verificar(intervalo, disponibilidad.periodos(comando.profesionalId(), intervalo),
                zona, false);
        NumeroReserva numero = numeros.generar();

        for (Long sillonId : candidatos(comando.profesionalId())) {
            if (descartados.contains(sillonId)
                    || !Boolean.TRUE.equals(recursos.sillonActivo(sillonId).orElse(false))) {
                continue;
            }
            Turno candidato = Turno.sobreturno(comando.pacienteId(), comando.profesionalId(), sillonId,
                    comando.prestacionId(), intervalo, numero, comando.motivo());
            List<Turno> existentes = turnos.buscarQueIntersecan(null, sillonId, intervalo.ampliadoPor(margen));
            if (!sillonLibre(candidato, existentes)) {
                continue;
            }
            try {
                return turnos.guardar(candidato);
            } catch (TurnoRechazadoException e) {
                if (e.codigo() == CodigoRechazo.SOLAPAMIENTO_SILLON
                        || e.codigo() == CodigoRechazo.MARGEN_INSUFICIENTE) {
                    throw new SillonTomadoException(sillonId);
                }
                throw e;
            }
        }
        throw sinSillon();
    }

    /**
     * Los sillones en orden determinista. El enganche {@code preferidoPara} hoy devuelve vacío porque C-04/V2
     * todavía no expone el sillón habitual (Q1, opción (a)); cuando lo exponga, el preferido se prueba
     * primero y el orden por id sigue siendo el fallback.
     */
    private List<Long> candidatos(Long profesionalId) {
        List<Long> disponibles = sillonesDisponibles.activos();
        return sillonesDisponibles.preferidoPara(profesionalId)
                .filter(disponibles::contains)
                .map(preferido -> Stream.concat(
                        Stream.of(preferido),
                        disponibles.stream().filter(id -> !id.equals(preferido))).toList())
                .orElse(disponibles);
    }

    private boolean sillonLibre(Turno candidato, List<Turno> existentes) {
        try {
            agenda.verificarSillon(candidato, existentes);
            return true;
        } catch (TurnoRechazadoException e) {
            return false;
        }
    }

    private static TurnoRechazadoException sinSillon() {
        return new TurnoRechazadoException(CodigoRechazo.SIN_SILLON_DISPONIBLE,
                "No hay un sillón libre con el margen de limpieza");
    }

    private void validar(CrearSobreturnoCommand comando) {
        if (comando.inicio().isBefore(reloj.instant())) {
            throw new TurnoRechazadoException(CodigoRechazo.INICIO_EN_EL_PASADO,
                    "No se puede crear un turno en el pasado");
        }
        String motivo = comando.motivo();
        if (motivo == null || motivo.isBlank() || motivo.trim().length() > 500) {
            throw new TurnoRechazadoException(CodigoRechazo.DATOS_INVALIDOS,
                    "El motivo administrativo del sobreturno es obligatorio y no puede superar 500 caracteres");
        }
    }

    private RecursosAgenda.Prestacion verificarRecursos(CrearSobreturnoCommand comando) {
        if (!recursos.existePaciente(comando.pacienteId())) {
            throw new ReferenciaInexistenteException("pacienteId");
        }
        boolean profesionalActivo = recursos.profesionalActivo(comando.profesionalId())
                .orElseThrow(() -> new ReferenciaInexistenteException("profesionalId"));
        RecursosAgenda.Prestacion prestacion = recursos.prestacion(comando.prestacionId())
                .orElseThrow(() -> new ReferenciaInexistenteException("prestacionId"));
        if (!profesionalActivo) {
            throw new TurnoRechazadoException(CodigoRechazo.RECURSO_INACTIVO, "El profesional está inactivo");
        }
        if (!prestacion.activa()) {
            throw new TurnoRechazadoException(CodigoRechazo.RECURSO_INACTIVO, "La prestación está inactiva");
        }
        return prestacion;
    }
}
