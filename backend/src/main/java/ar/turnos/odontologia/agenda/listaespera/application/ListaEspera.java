package ar.turnos.odontologia.agenda.listaespera.application;

import ar.turnos.odontologia.agenda.listaespera.application.ListaEsperaRepository.AvisoPendiente;
import ar.turnos.odontologia.agenda.listaespera.application.ListaEsperaRepository.FilaInscripcion;
import ar.turnos.odontologia.agenda.listaespera.application.ListaEsperaRepository.InscripcionDuplicadaException;
import ar.turnos.odontologia.agenda.listaespera.application.ListaEsperaRepository.VistaOferta;
import ar.turnos.odontologia.agenda.listaespera.application.PuertosListaEspera.Pacientes;
import ar.turnos.odontologia.agenda.listaespera.application.PuertosListaEspera.PlazoVigente;
import ar.turnos.odontologia.agenda.listaespera.application.PuertosListaEspera.Tokens;
import ar.turnos.odontologia.agenda.listaespera.domain.CodigoListaEspera;
import ar.turnos.odontologia.agenda.listaespera.domain.EstadoInscripcion;
import ar.turnos.odontologia.agenda.listaespera.domain.EstadoOferta;
import ar.turnos.odontologia.agenda.listaespera.domain.Inscripcion;
import ar.turnos.odontologia.agenda.listaespera.domain.ListaEsperaException;
import ar.turnos.odontologia.agenda.listaespera.domain.Oferta;
import ar.turnos.odontologia.agenda.listaespera.domain.RangoDeFechas;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Casos de uso de la lista de espera (C-15, RN-LE-01 a RN-LE-09). Cada método corre dentro de una transacción que abre
 * el borde de infraestructura; los avisos se mandan después del commit, con lo que devuelve cada método.
 */
public class ListaEspera {

    /** Inscripciones evaluadas como máximo por horario liberado (design, riesgos). */
    static final int MAXIMO_CANDIDATAS = 50;

    /** Intentos de aviso por oferta: el primero y dos reintentos de la tarea programada. */
    public static final int MAXIMO_INTENTOS_AVISO = 3;

    /** Lo que genera el servidor: base64url. Cualquier otra cosa nunca se emitió (como en C-08). */
    private static final Pattern FORMATO_DE_TOKEN = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    private final ListaEsperaRepository repositorio;
    private final AgendaListaEspera agenda;
    private final Pacientes pacientes;
    private final PlazoVigente plazo;
    private final Tokens tokens;
    private final Clock reloj;
    private final ZoneId zona;

    public ListaEspera(ListaEsperaRepository repositorio, AgendaListaEspera agenda, Pacientes pacientes,
                       PlazoVigente plazo, Tokens tokens, Clock reloj, ZoneId zona) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
        this.agenda = Objects.requireNonNull(agenda, "agenda");
        this.pacientes = Objects.requireNonNull(pacientes, "pacientes");
        this.plazo = Objects.requireNonNull(plazo, "plazo");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
        this.zona = Objects.requireNonNull(zona, "zona");
    }

    /** Un horario que se liberó: el turno cancelado o el viejo de una reprogramación. */
    public record HorarioLiberado(Long turnoId, Long profesionalId, Instant inicio) {}

    /** Una oferta recién creada (o con un token nuevo) cuyo aviso hay que mandar después del commit. */
    public record AvisoAEnviar(Long ofertaId, Long pacienteId, Instant inicio, Instant venceEn, String token) {}

    /** La oferta tomada para aceptar, con lo que hace falta para crear el turno. */
    public record Aceptacion(Oferta oferta, Inscripcion inscripcion) {}

    // ---------- inscripción ----------

    /**
     * RN-LE-01: anota a un paciente.
     *
     * @param actor {@code paciente-online:<usuarioId>} o el usuario del personal, sin datos personales
     */
    public Inscripcion inscribir(Long pacienteId, Long prestacionId, Long profesionalId, LocalDate desde,
                                 LocalDate hasta, String actor) {
        RangoDeFechas rango = RangoDeFechas.validado(desde, hasta, hoy());
        boolean consentimiento = pacientes.consentimiento(pacienteId)
                .orElseThrow(() -> new ListaEsperaException(CodigoListaEspera.RECURSO_NO_ENCONTRADO,
                        "El paciente no existe"));
        agenda.validarPreferencia(prestacionId, profesionalId);
        if (!consentimiento) {
            throw new ListaEsperaException(CodigoListaEspera.SIN_CONSENTIMIENTO,
                    "El paciente no aceptó recibir comunicaciones");
        }
        if (repositorio.existeActiva(pacienteId, prestacionId)) {
            throw yaAnotado();
        }
        try {
            return repositorio.insertar(Inscripcion.nueva(pacienteId, prestacionId, profesionalId, rango,
                    reloj.instant()), actor);
        } catch (InscripcionDuplicadaException e) {
            throw yaAnotado();
        }
    }

    public List<FilaInscripcion> activas() {
        return repositorio.activas();
    }

    public List<FilaInscripcion> activasDe(Long pacienteId) {
        return repositorio.activasDe(pacienteId);
    }

    /**
     * RN-LE-09: da de baja una inscripción activa. Una oferta pendiente suya queda rechazada y su horario se devuelve
     * para ofrecerlo al siguiente.
     *
     * @param duenioId el paciente de la sesión, o {@code null} si da la baja el personal
     */
    public Optional<HorarioLiberado> darDeBaja(Long inscripcionId, Long duenioId) {
        Inscripcion inscripcion = repositorio.inscripcionParaActualizar(inscripcionId)
                .filter(i -> duenioId == null || duenioId.equals(i.pacienteId()))
                .orElseThrow(() -> new ListaEsperaException(CodigoListaEspera.RECURSO_NO_ENCONTRADO,
                        "No existe esa inscripción"));
        if (inscripcion.estado() != EstadoInscripcion.ACTIVA) {
            throw new ListaEsperaException(CodigoListaEspera.INSCRIPCION_NO_ACTIVA, "La inscripción ya no está activa");
        }
        Instant ahora = reloj.instant();
        repositorio.cerrarInscripcion(inscripcion.id(), EstadoInscripcion.BAJA, ahora);
        return repositorio.ofertaPendienteParaActualizar(inscripcion.id()).map(oferta -> {
            repositorio.responder(oferta.id(), EstadoOferta.RECHAZADA, ahora);
            return horarioDe(oferta);
        });
    }

    // ---------- ofertas ----------

    /**
     * RN-LE-02 a RN-LE-05: ofrece el horario a la primera inscripción compatible por orden de llegada. Nada si el
     * horario ya empezó, si ya tiene una oferta pendiente o si nadie es compatible.
     */
    public Optional<AvisoAEnviar> ofrecer(HorarioLiberado horario) {
        Instant ahora = reloj.instant();
        if (!horario.inicio().isAfter(ahora) || repositorio.hayPendientePara(horario.turnoId())) {
            return Optional.empty();
        }
        LocalDate fecha = horario.inicio().atZone(zona).toLocalDate();
        for (Inscripcion candidata : repositorio.candidatas(horario.profesionalId(), fecha, horario.turnoId(),
                MAXIMO_CANDIDATAS)) {
            if (!candidata.admite(horario.profesionalId(), fecha)
                    || !agenda.hayLugar(candidata.prestacionId(), horario.profesionalId(), horario.inicio())) {
                continue;
            }
            String token = tokens.generar();
            Oferta nueva = new Oferta(null, candidata.id(), horario.turnoId(), horario.profesionalId(),
                    horario.inicio(), EstadoOferta.PENDIENTE, plazo.actual().vencimiento(ahora, horario.inicio()));
            Optional<Oferta> guardada = repositorio.insertarOferta(nueva, tokens.huella(token), ahora);
            if (guardada.isEmpty()) {
                // otra cancelación simultánea ya ofreció este horario: no se ofrece dos veces
                return Optional.empty();
            }
            return Optional.of(new AvisoAEnviar(guardada.get().id(), candidata.pacienteId(), horario.inicio(),
                    guardada.get().venceEn(), token));
        }
        return Optional.empty();
    }

    /** Lo que muestra el link, sin consumir el token. Una oferta pendiente vencida se informa como vencida. */
    public VistaOferta ver(String token) {
        VistaOferta vista = repositorio.vista(huellaValida(token)).orElseThrow(ListaEspera::inexistente);
        if (vista.estado() == EstadoOferta.PENDIENTE && !reloj.instant().isBefore(vista.venceEn())) {
            return new VistaOferta(vista.inicio(), vista.profesionalNombre(), vista.venceEn(), EstadoOferta.VENCIDA);
        }
        return vista;
    }

    /**
     * Primer paso de la aceptación (design D4): toma la oferta, verifica que se pueda responder y la marca
     * {@code ACEPTADA} antes de crear el turno, así un segundo pedido con el mismo token ya la ve respondida.
     */
    public Aceptacion tomarParaAceptar(String token) {
        Oferta oferta = repositorio.ofertaParaActualizar(huellaValida(token)).orElseThrow(ListaEspera::inexistente);
        oferta.verificarRespondible(reloj.instant());
        Inscripcion inscripcion = repositorio.inscripcion(oferta.inscripcionId()).orElseThrow();
        repositorio.responder(oferta.id(), EstadoOferta.ACEPTADA, reloj.instant());
        return new Aceptacion(oferta, inscripcion);
    }

    /** El turno se creó: queda enlazado a la oferta y la inscripción pasa a {@code ASIGNADA}. */
    public void confirmarAceptacion(Aceptacion aceptacion, Long turnoId) {
        repositorio.registrarTurnoCreado(aceptacion.oferta().id(), turnoId);
        repositorio.cerrarInscripcion(aceptacion.inscripcion().id(), EstadoInscripcion.ASIGNADA, reloj.instant());
    }

    /** RN-LE-06: el horario ya no está; la inscripción sigue activa y no se le ofrece a nadie más. */
    public void marcarNoDisponible(Aceptacion aceptacion) {
        repositorio.responder(aceptacion.oferta().id(), EstadoOferta.NO_DISPONIBLE, reloj.instant());
    }

    /** Falla inesperada al crear el turno: la oferta vuelve a estar pendiente, como si no se hubiera respondido. */
    public void deshacerAceptacion(Aceptacion aceptacion) {
        repositorio.responder(aceptacion.oferta().id(), EstadoOferta.PENDIENTE, null);
    }

    /** RN-LE-07: "No me interesa". La inscripción sigue activa y el horario pasa al siguiente. */
    public HorarioLiberado rechazar(String token) {
        Oferta oferta = repositorio.ofertaParaActualizar(huellaValida(token)).orElseThrow(ListaEspera::inexistente);
        oferta.verificarRespondible(reloj.instant());
        repositorio.responder(oferta.id(), EstadoOferta.RECHAZADA, reloj.instant());
        return horarioDe(oferta);
    }

    /**
     * RN-LE-09: vence las ofertas y las inscripciones cumplidas. Devuelve los horarios que todavía no empezaron, para
     * ofrecerlos al siguiente.
     */
    public List<HorarioLiberado> vencer() {
        Instant ahora = reloj.instant();
        List<HorarioLiberado> aReofrecer = new ArrayList<>();
        for (Oferta vencida : repositorio.vencerOfertas(ahora)) {
            if (vencida.inicio().isAfter(ahora)) {
                aReofrecer.add(horarioDe(vencida));
            }
        }
        repositorio.vencerInscripciones(hoy(), ahora);
        return aReofrecer;
    }

    /** Ofertas vigentes cuyo aviso falló: cada reintento lleva un token nuevo (el anterior no se guarda en claro). */
    public List<AvisoAEnviar> avisosParaReintentar() {
        List<AvisoAEnviar> avisos = new ArrayList<>();
        for (AvisoPendiente pendiente : repositorio.avisosSinEnviar(reloj.instant(), MAXIMO_INTENTOS_AVISO)) {
            String token = tokens.generar();
            repositorio.registrarAviso(pendiente.ofertaId(), false, tokens.huella(token));
            avisos.add(new AvisoAEnviar(pendiente.ofertaId(), pendiente.pacienteId(), pendiente.inicio(),
                    pendiente.venceEn(), token));
        }
        return avisos;
    }

    public void registrarAviso(Long ofertaId, boolean enviado) {
        repositorio.registrarAviso(ofertaId, enviado, null);
    }

    // ---------- auxiliares ----------

    private LocalDate hoy() {
        return reloj.instant().atZone(zona).toLocalDate();
    }

    private String huellaValida(String token) {
        if (token == null || !FORMATO_DE_TOKEN.matcher(token).matches()) {
            throw inexistente();
        }
        return tokens.huella(token);
    }

    private static HorarioLiberado horarioDe(Oferta oferta) {
        return new HorarioLiberado(oferta.turnoLiberadoId(), oferta.profesionalId(), oferta.inicio());
    }

    private static ListaEsperaException inexistente() {
        return new ListaEsperaException(CodigoListaEspera.OFERTA_INEXISTENTE, "El link no es válido");
    }

    private static ListaEsperaException yaAnotado() {
        return new ListaEsperaException(CodigoListaEspera.YA_EN_LISTA_DE_ESPERA,
                "Ya hay una inscripción activa para esa prestación");
    }
}
