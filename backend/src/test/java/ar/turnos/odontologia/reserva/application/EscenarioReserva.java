package ar.turnos.odontologia.reserva.application;

import ar.turnos.odontologia.agenda.application.CancelarTurnoCommand;
import ar.turnos.odontologia.agenda.application.CrearTurnoCommand;
import ar.turnos.odontologia.agenda.application.DisponibilidadProfesional;
import ar.turnos.odontologia.agenda.application.ReprogramarTurnoCommand;
import ar.turnos.odontologia.agenda.application.TurnoRepository;
import ar.turnos.odontologia.agenda.domain.CodigoRechazo;
import ar.turnos.odontologia.agenda.domain.EstadoTurno;
import ar.turnos.odontologia.agenda.domain.Intervalo;
import ar.turnos.odontologia.agenda.domain.Margen;
import ar.turnos.odontologia.agenda.domain.NumeroReserva;
import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.agenda.domain.TurnoRechazadoException;
import ar.turnos.odontologia.agenda.domain.reglas.FranjaHoraria;
import ar.turnos.odontologia.agenda.domain.reglas.HorarioSemanal;
import ar.turnos.odontologia.agenda.domain.reglas.PeriodoSinAtencion;
import ar.turnos.odontologia.agenda.domain.reglas.PeriodosProfesional;
import ar.turnos.odontologia.reserva.application.ReservasPaciente.TurnoDelPaciente;
import ar.turnos.odontologia.reserva.domain.Desempate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Datos ficticios y dobles en memoria de los tests unitarios de la reserva (design D11): la Dra. A (sillón habitual:
 * Sillón 1) y el Dr. B (sillón habitual: Sillón 2), "Consulta" y "Tratamiento de conducto" de 30 minutos, el reloj
 * en el martes 2026-12-01 a las 08:00 y el margen de 10 minutos. Los profesionales no tienen franjas hasta que el
 * test las carga.
 */
final class EscenarioReserva {

    static final ZoneId BA = ZoneId.of("America/Argentina/Buenos_Aires");
    static final long DRA_A = 1L;
    static final long DR_B = 2L;
    static final long SILLON_1 = 1L;
    static final long SILLON_2 = 2L;
    static final long CONSULTA = 1L;
    static final long CONDUCTO = 2L;
    static final long PACIENTE_UNO = 1L;
    static final long PACIENTE_DOS = 2L;
    static final long CUENTA_UNO = 77L;
    static final String ACTOR_UNO = "paciente-online:77";

    final CatalogoEnMemoria catalogo = new CatalogoEnMemoria();
    final TurnosEnMemoria turnos = new TurnosEnMemoria();
    final PeriodosEnMemoria periodos = new PeriodosEnMemoria();
    final RelojDeTest reloj = new RelojDeTest(LocalDateTime.of(2026, 12, 1, 8, 0));
    final DesempateDeTest desempate = new DesempateDeTest();
    final ReservasEnMemoria reservas = new ReservasEnMemoria(turnos, catalogo);
    final AgendaFalsa agenda = new AgendaFalsa(turnos, catalogo);
    final AvisoGrabador aviso = new AvisoGrabador();
    final BuscadorDeHuecos buscador = new BuscadorDeHuecos(catalogo, turnos, periodos, Margen.PREDETERMINADO, reloj, BA,
            15, desempate);

    EscenarioReserva() {
        catalogo.prestacion(CONSULTA, "Consulta", 30, true);
        catalogo.prestacion(CONDUCTO, "Tratamiento de conducto", 30, true);
        catalogo.profesional(DRA_A, "Ana Ejemplo", true, SILLON_1);
        catalogo.profesional(DR_B, "Bruno Prueba", true, SILLON_2);
        catalogo.habilitar(DRA_A, CONSULTA);
        catalogo.habilitar(DR_B, CONSULTA);
        catalogo.habilitar(DR_B, CONDUCTO);
        catalogo.sillones(SILLON_1, SILLON_2);
    }

    /** Franja del martes 2026-12-01 (día de la semana 2). */
    void martes(long profesional, int desdeHora, int hastaHora) {
        periodos.franja(profesional, FranjaHoraria.de(2, LocalTime.of(desdeHora, 0), LocalTime.of(hastaHora, 0)));
    }

    /** Franja del martes con minutos, para los escenarios con 10:30 u 11:30. */
    void martes(long profesional, LocalTime desde, LocalTime hasta) {
        periodos.franja(profesional, FranjaHoraria.de(2, desde, hasta));
    }

    static Instant en(int dia, int hora, int minuto) {
        return LocalDateTime.of(2026, 12, dia, hora, minuto).atZone(BA).toInstant();
    }

    static Instant hora(int hora, int minuto) {
        return en(1, hora, minuto);
    }

    Turno turno(long paciente, long profesional, long sillon, int dia, int hora, int minuto, EstadoTurno estado) {
        Instant inicio = en(dia, hora, minuto);
        Turno turno = new Turno(null, paciente, profesional, sillon, CONSULTA,
                new Intervalo(inicio, inicio.plus(Duration.ofMinutes(30))), estado,
                NumeroReserva.deSimbolos("BBBBBBBB"));
        return turnos.guardar(turno);
    }

    /** Un turno con un número de reserva propio (ocho símbolos del alfabeto), para buscarlo por número. */
    Turno turnoConNumero(long paciente, long profesional, long sillon, int dia, int hora, int minuto,
                         EstadoTurno estado, String simbolos) {
        Instant inicio = en(dia, hora, minuto);
        return turnos.guardar(new Turno(null, paciente, profesional, sillon, CONSULTA,
                new Intervalo(inicio, inicio.plus(Duration.ofMinutes(30))), estado, NumeroReserva.deSimbolos(simbolos)));
    }

    Turno turno(long paciente, long profesional, long sillon, int hora, int minuto) {
        return turno(paciente, profesional, sillon, 1, hora, minuto, EstadoTurno.RESERVADO);
    }

    Turno cancelado(long paciente, long profesional, long sillon, int hora, int minuto) {
        return turno(paciente, profesional, sillon, 1, hora, minuto, EstadoTurno.CANCELADO);
    }

    // ---------- dobles ----------

    static final class RelojDeTest extends Clock {
        private Instant ahora;

        RelojDeTest(LocalDateTime local) {
            this.ahora = local.atZone(BA).toInstant();
        }

        void fijarEn(int dia, int hora, int minuto) {
            ahora = en(dia, hora, minuto);
        }

        @Override
        public ZoneId getZone() {
            return BA;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    static final class DesempateDeTest implements Desempate {
        boolean ultimo;

        @Override
        public Long elegir(List<Long> empatadosOrdenadosPorId) {
            return ultimo ? empatadosOrdenadosPorId.get(empatadosOrdenadosPorId.size() - 1)
                    : empatadosOrdenadosPorId.get(0);
        }
    }

    static final class CatalogoEnMemoria implements CatalogoReserva {
        private final Map<Long, Prestacion> prestaciones = new HashMap<>();
        private final Map<Long, Profesional> profesionales = new HashMap<>();
        private final List<long[]> habilitaciones = new ArrayList<>();
        private List<Long> sillones = List.of();

        void prestacion(long id, String nombre, int minutos, boolean activa) {
            prestaciones.put(id, new Prestacion(id, nombre, Duration.ofMinutes(minutos), activa));
        }

        void profesional(long id, String nombre, boolean activo, Long habitual) {
            profesionales.put(id, new Profesional(id, nombre, activo, habitual));
        }

        void habilitar(long profesional, long prestacion) {
            habilitaciones.add(new long[] {profesional, prestacion});
        }

        void sillones(Long... ids) {
            sillones = List.of(ids);
        }

        @Override
        public Optional<Prestacion> prestacion(Long id) {
            return Optional.ofNullable(prestaciones.get(id));
        }

        @Override
        public Optional<Profesional> profesional(Long id) {
            return Optional.ofNullable(profesionales.get(id));
        }

        @Override
        public List<Prestacion> prestacionesActivas() {
            return prestaciones.values().stream().filter(Prestacion::activa).toList();
        }

        @Override
        public List<Profesional> profesionalesHabilitados(Long prestacionId) {
            return profesionales.values().stream()
                    .filter(Profesional::activo)
                    .filter(p -> estaHabilitado(p.id(), prestacionId))
                    .sorted(java.util.Comparator.comparing(Profesional::id))
                    .toList();
        }

        @Override
        public boolean estaHabilitado(Long profesionalId, Long prestacionId) {
            return habilitaciones.stream().anyMatch(h -> h[0] == profesionalId && h[1] == prestacionId);
        }

        @Override
        public List<Long> sillonesActivos() {
            return sillones;
        }
    }

    static final class TurnosEnMemoria implements TurnoRepository {
        final List<Turno> todos = new ArrayList<>();
        private long proximoId = 1;

        @Override
        public List<Turno> buscarQueIntersecan(Long profesionalId, Long sillonId, Intervalo ventana) {
            return todos.stream()
                    .filter(t -> (profesionalId != null && t.profesionalId().equals(profesionalId))
                            || (sillonId != null && t.sillonId().equals(sillonId)))
                    .filter(t -> t.intervalo().seSuperponeCon(ventana))
                    .toList();
        }

        @Override
        public Turno guardar(Turno turno) {
            if (turno.id() == null) {
                Turno guardado = turno.conId(proximoId++);
                todos.add(guardado);
                return guardado;
            }
            todos.replaceAll(t -> t.id().equals(turno.id()) ? turno : t);
            return turno;
        }

        @Override
        public Optional<TurnoModificable> buscarParaModificar(Long turnoId) {
            throw new UnsupportedOperationException("La reserva no modifica turnos por este puerto");
        }

        Turno porId(long id) {
            return todos.stream().filter(t -> t.id() == id).findFirst().orElseThrow();
        }

        long activos() {
            return todos.stream().filter(Turno::esActivo).count();
        }
    }

    /** Doble de {@link ReservasPaciente} sobre los mismos turnos que ve la agenda falsa. */
    static final class ReservasEnMemoria implements ReservasPaciente {
        private final TurnosEnMemoria turnos;
        private final CatalogoEnMemoria catalogo;
        final Map<Long, String> dnis = new HashMap<>(Map.of(PACIENTE_UNO, "99000001", PACIENTE_DOS, "99000002"));

        ReservasEnMemoria(TurnosEnMemoria turnos, CatalogoEnMemoria catalogo) {
            this.turnos = turnos;
            this.catalogo = catalogo;
        }

        @Override
        public Optional<TurnoDelPaciente> buscar(String numeroNormalizado, Long pacienteId) {
            return turnos.todos.stream()
                    .filter(t -> t.numeroReserva().valor().equals(numeroNormalizado) && t.pacienteId().equals(pacienteId))
                    .map(this::vista).findFirst();
        }

        @Override
        public List<TurnoDelPaciente> turnosDe(Long pacienteId) {
            return turnos.todos.stream().filter(t -> t.pacienteId().equals(pacienteId))
                    .sorted(java.util.Comparator.comparing(t -> t.intervalo().inicio())).map(this::vista).toList();
        }

        @Override
        public Optional<String> dni(Long pacienteId) {
            return Optional.ofNullable(dnis.get(pacienteId));
        }

        private TurnoDelPaciente vista(Turno t) {
            return new TurnoDelPaciente(t.id(), t.numeroReserva().valor(), t.intervalo().inicio(), t.intervalo().fin(),
                    t.estado(), t.prestacionId(), catalogo.prestacion(t.prestacionId()).orElseThrow().nombre(),
                    t.profesionalId(), catalogo.profesional(t.profesionalId()).orElseThrow().nombre());
        }
    }

    static final class PeriodosEnMemoria implements DisponibilidadProfesional {
        private final Map<Long, List<FranjaHoraria>> franjas = new HashMap<>();
        private final Map<Long, List<PeriodoSinAtencion>> sinAtencion = new HashMap<>();

        void franja(long profesional, FranjaHoraria franja) {
            franjas.computeIfAbsent(profesional, k -> new ArrayList<>()).add(franja);
        }

        void periodo(long profesional, PeriodoSinAtencion periodo) {
            sinAtencion.computeIfAbsent(profesional, k -> new ArrayList<>()).add(periodo);
        }

        @Override
        public PeriodosProfesional periodos(Long profesionalId, Intervalo ventana) {
            return new PeriodosProfesional(HorarioSemanal.de(franjas.getOrDefault(profesionalId, List.of())),
                    sinAtencion.getOrDefault(profesionalId, List.of()).stream()
                            .filter(p -> p.seCruzaCon(ventana)).toList());
        }
    }

    /** Doble de {@code ServicioAgenda}: guarda los turnos, anota los comandos y puede simular rechazos. */
    static final class AgendaFalsa implements AgendaDeTurnos {
        final List<CrearTurnoCommand> creaciones = new ArrayList<>();
        final List<CancelarTurnoCommand> cancelaciones = new ArrayList<>();
        final List<ReprogramarTurnoCommand> reprogramaciones = new ArrayList<>();
        final List<String> actores = new ArrayList<>();
        private final Deque<RuntimeException> rechazos = new ArrayDeque<>();
        private final Deque<Runnable> efectos = new ArrayDeque<>();
        private final TurnosEnMemoria turnos;
        private final CatalogoEnMemoria catalogo;

        AgendaFalsa(TurnosEnMemoria turnos, CatalogoEnMemoria catalogo) {
            this.turnos = turnos;
            this.catalogo = catalogo;
        }

        /** El próximo intento de escritura lanza este rechazo (por ejemplo, una carrera ganada por otro). */
        void rechazarProximo(CodigoRechazo codigo) {
            rechazarProximo(codigo, () -> { });
        }

        /** Igual, y además ejecuta {@code ganador}: lo que hizo el otro paciente para ganar la carrera. */
        void rechazarProximo(CodigoRechazo codigo, Runnable ganador) {
            rechazos.add(new TurnoRechazadoException(codigo, "rechazo simulado " + codigo));
            efectos.add(ganador);
        }

        private void simularRechazo() {
            RuntimeException rechazo = rechazos.poll();
            if (rechazo != null) {
                efectos.poll().run();
                throw rechazo;
            }
        }

        @Override
        public Turno crear(CrearTurnoCommand comando, String actor) {
            creaciones.add(comando);
            actores.add(actor);
            simularRechazo();
            return turnos.guardar(nuevo(comando.pacienteId(), comando.profesionalId(), comando.sillonId(),
                    comando.prestacionId(), comando.inicio()));
        }

        @Override
        public Turno cancelar(CancelarTurnoCommand comando, String actor) {
            cancelaciones.add(comando);
            actores.add(actor);
            simularRechazo();
            return turnos.guardar(turnos.porId(comando.turnoId()).cancelado());
        }

        @Override
        public Turno reprogramar(ReprogramarTurnoCommand comando, String actor) {
            reprogramaciones.add(comando);
            actores.add(actor);
            simularRechazo();
            Turno original = turnos.porId(comando.turnoId());
            turnos.guardar(original.cancelado());
            return turnos.guardar(nuevo(original.pacienteId(), comando.profesionalId(), comando.sillonId(),
                    original.prestacionId(), comando.inicio()));
        }

        private Turno nuevo(Long paciente, Long profesional, Long sillon, Long prestacion, Instant inicio) {
            Duration duracion = catalogo.prestacion(prestacion).orElseThrow().duracion();
            return Turno.reservar(paciente, profesional, sillon, prestacion, new Intervalo(inicio, inicio.plus(duracion)),
                    NumeroReserva.deSimbolos("CCCCCCCC"));
        }
    }

    /** Aviso que anota los ids y puede fallar, para probar que la reserva no depende de él. */
    static final class AvisoGrabador implements AvisoReservaOnline {
        final List<String> llamadas = new ArrayList<>();
        boolean fallar;

        private void anotar(String tipo, Long id) {
            llamadas.add(tipo + ":" + id);
            if (fallar) {
                throw new IllegalStateException("fallo simulado del aviso");
            }
        }

        @Override
        public void reservado(Long turnoId) {
            anotar("reservado", turnoId);
        }

        @Override
        public void cancelado(Long turnoId) {
            anotar("cancelado", turnoId);
        }

        @Override
        public void reprogramado(Long turnoIdNuevo) {
            anotar("reprogramado", turnoIdNuevo);
        }
    }
}
