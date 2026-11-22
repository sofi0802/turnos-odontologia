package ar.turnos.odontologia.notificaciones.application;

import static ar.turnos.odontologia.notificaciones.application.NotificacionesEnMemoria.ahora;
import static ar.turnos.odontologia.notificaciones.application.NotificacionesEnMemoria.conWhatsappYEmail;
import static ar.turnos.odontologia.notificaciones.application.NotificacionesEnMemoria.en;
import static ar.turnos.odontologia.notificaciones.application.NotificacionesEnMemoria.soloEmail;
import static ar.turnos.odontologia.notificaciones.application.NotificacionesEnMemoria.soloWhatsapp;
import static ar.turnos.odontologia.notificaciones.application.NotificacionesEnMemoria.turno;
import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.notificaciones.application.NotificacionesEnMemoria.CanalEnMemoria;
import ar.turnos.odontologia.notificaciones.application.NotificacionesEnMemoria.RelojMutable;
import ar.turnos.odontologia.notificaciones.application.NotificacionesEnMemoria.RepositorioEnMemoria;
import ar.turnos.odontologia.notificaciones.application.NotificacionesEnMemoria.TokensSecuenciales;
import ar.turnos.odontologia.notificaciones.application.NotificacionesEnMemoria.TurnosEnMemoria;
import ar.turnos.odontologia.notificaciones.domain.Canal;
import ar.turnos.odontologia.notificaciones.domain.Consultorio;
import ar.turnos.odontologia.notificaciones.domain.ContactoPaciente;
import ar.turnos.odontologia.notificaciones.domain.EstadoNotificacion;
import ar.turnos.odontologia.notificaciones.domain.Notificacion;
import ar.turnos.odontologia.notificaciones.domain.TipoNotificacion;
import ar.turnos.odontologia.notificaciones.domain.TurnoARecordar;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Escenarios de recordatorio a las 24 horas, anticipación, consentimiento y reintentos del spec
 * {@code notificaciones-recordatorios} (C-11), con dobles en memoria y un reloj controlado. "Ahora" es el
 * 2026-12-01 a las 08:00 y el turno de los escenarios es el 2026-12-02 a las 07:30, salvo que se indique otro.
 */
class EnviarRecordatoriosTest {

    private static final Instant TURNO = en(2026, 12, 2, 7, 30);
    private static final Consultorio CONSULTORIO = new Consultorio("Clínica Ejemplo", "Calle Ficticia 123");
    private static final String BASE_URL = "https://turnos.example.test";

    private final RelojMutable reloj = new RelojMutable(ahora());
    private final TurnosEnMemoria turnos = new TurnosEnMemoria();
    private final RepositorioEnMemoria repositorio = new RepositorioEnMemoria(turnos);
    private final CanalEnMemoria whatsapp = new CanalEnMemoria(Canal.WHATSAPP);
    private final CanalEnMemoria email = new CanalEnMemoria(Canal.EMAIL);
    private final EnviarRecordatorios casoDeUso = new EnviarRecordatorios(turnos, repositorio,
            List.of(whatsapp, email), new TokensSecuenciales(), CONSULTORIO, BASE_URL, reloj);

    private void hay(TurnoARecordar turno) {
        turnos.guardar(turno);
    }

    private static TurnoARecordar inactivo(TurnoARecordar t) {
        return new TurnoARecordar(t.turnoId(), t.inicio(),
                t.reservadoEn(), false, t.contacto());
    }

    private static TurnoARecordar reservadoEl(TurnoARecordar t, Instant reservadoEn) {
        return new TurnoARecordar(t.turnoId(), t.inicio(), reservadoEn,
                t.activo(), t.contacto());
    }

    // ---------- Requirement: Recordatorio 24 horas antes del turno ----------

    @Test
    @DisplayName("Turno dentro de las próximas 24 horas")
    void turnoDentroDeLasProximas24Horas() {
        // GIVEN un turno RESERVADO de "Paciente Uno" el 2026-12-02 a las 07:30, reservado el 2026-11-28
        // AND "Paciente Uno" con consentimiento, WhatsApp y teléfono
        hay(turno(1, TURNO, conWhatsappYEmail()));

        // WHEN corre la tarea de recordatorios
        casoDeUso.ejecutar();

        // THEN se envía un recordatorio a "Paciente Uno" por WhatsApp
        assertThat(whatsapp.enviados).hasSize(1);
        assertThat(whatsapp.enviados.get(0).cuerpo()).contains("02/12/2026", "07:30")
                .doesNotContain("Paciente Uno", "Dra. A");
        assertThat(whatsapp.enviados.get(0).destino()).isEqualTo("+54 11 5555-0001");
        assertThat(email.enviados).isEmpty();
        // AND queda una notificación RECORDATORIO en estado ENVIADA para ese turno
        Notificacion n = repositorio.unica();
        assertThat(n.tipo()).isEqualTo(TipoNotificacion.RECORDATORIO);
        assertThat(n.estado()).isEqualTo(EstadoNotificacion.ENVIADA);
        assertThat(n.turnoId()).isEqualTo(1L);
        assertThat(n.canal()).isEqualTo(Canal.WHATSAPP);
    }

    @Test
    @DisplayName("Turno que todavía no está dentro de las 24 horas")
    void turnoQueTodaviaNoEstaDentroDeLas24Horas() {
        // GIVEN un turno RESERVADO de "Paciente Uno" el 2026-12-02 a las 09:00, reservado el 2026-11-28
        hay(turno(1, en(2026, 12, 2, 9, 0), conWhatsappYEmail()));

        // WHEN corre la tarea de recordatorios
        casoDeUso.ejecutar();

        // THEN no se envía ningún recordatorio y no se crea ninguna notificación
        assertThat(whatsapp.enviados).isEmpty();
        assertThat(repositorio.notificaciones).isEmpty();
    }

    @Test
    void elLimiteExactoDeLas24HorasEntraYUnSegundoDespuesNo() {
        // las 08:00 + 24 h = 2026-12-02 08:00
        hay(turno(1, en(2026, 12, 2, 8, 0), conWhatsappYEmail()));
        hay(turno(2, en(2026, 12, 2, 8, 0).plusSeconds(1), conWhatsappYEmail()));

        casoDeUso.ejecutar();

        assertThat(repositorio.notificaciones).extracting(Notificacion::turnoId).containsExactly(1L);
    }

    @Test
    void unTurnoQueYaEmpezoNoRecibeRecordatorio() {
        hay(turno(1, en(2026, 12, 1, 7, 30), conWhatsappYEmail()));

        casoDeUso.ejecutar();

        assertThat(repositorio.notificaciones).isEmpty();
    }

    @Test
    @DisplayName("Un turno confirmado también recibe recordatorio")
    void unTurnoConfirmadoTambienRecibeRecordatorio() {
        // GIVEN un turno CONFIRMADO (activo) de "Paciente Uno" el 2026-12-02 a las 07:30, reservado el 2026-11-28
        // AND "Paciente Uno" con consentimiento y email
        hay(turno(1, TURNO, soloEmail()));

        // WHEN corre la tarea de recordatorios
        casoDeUso.ejecutar();

        // THEN se envía un recordatorio por email
        assertThat(email.enviados).hasSize(1);
        assertThat(whatsapp.enviados).isEmpty();
        assertThat(email.enviados.get(0).destino()).isEqualTo("paciente.uno@example.com");
    }

    @Test
    @DisplayName("Un turno cancelado no recibe recordatorio")
    void unTurnoCanceladoNoRecibeRecordatorio() {
        // GIVEN un turno CANCELADO de "Paciente Uno" el 2026-12-02 a las 07:30
        hay(inactivo(turno(1, TURNO, conWhatsappYEmail())));

        // WHEN corre la tarea de recordatorios
        casoDeUso.ejecutar();

        // THEN no se envía ningún recordatorio
        assertThat(whatsapp.enviados).isEmpty();
        assertThat(email.enviados).isEmpty();
        assertThat(repositorio.notificaciones).isEmpty();
    }

    @Test
    @DisplayName("No se envía dos veces el recordatorio de un turno")
    void noSeEnviaDosVecesElRecordatorioDeUnTurno() {
        // GIVEN un turno RESERVADO de "Paciente Uno" que ya tiene un recordatorio enviado
        hay(turno(1, TURNO, conWhatsappYEmail()));
        casoDeUso.ejecutar();

        // WHEN la tarea de recordatorios corre otra vez
        casoDeUso.ejecutar();

        // THEN no se envía un segundo recordatorio y sigue existiendo una sola notificación RECORDATORIO
        assertThat(whatsapp.enviados).hasSize(1);
        assertThat(repositorio.deTipo(TipoNotificacion.RECORDATORIO)).hasSize(1);
    }

    @Test
    void siOtraCorridaGanoLaInsercionNoSeEnviaNada() {
        // el filtro previo no vio el recordatorio (corridas simultáneas) pero la inserción lo rechaza,
        // como lo hace el índice único de la base: quien pierde no envía
        hay(turno(1, TURNO, conWhatsappYEmail()));
        RepositorioEnMemoria quePierde = new RepositorioEnMemoria(turnos) {
            @Override
            public boolean existeRecordatorio(Long turnoId) {
                return false;
            }
        };
        quePierde.insertar(Notificacion.pendiente(1L, Canal.WHATSAPP, TipoNotificacion.RECORDATORIO, ahora(), "otra"));
        EnviarRecordatorios corrida = new EnviarRecordatorios(turnos, quePierde, List.of(whatsapp, email),
                new TokensSecuenciales(), CONSULTORIO, BASE_URL, reloj);

        corrida.ejecutar();

        assertThat(whatsapp.enviados).isEmpty();
        assertThat(quePierde.notificaciones).hasSize(1);
    }

    // ---------- Requirement: Reservas con menos de 24 horas de anticipación ----------

    @Test
    @DisplayName("Turno reservado el mismo día")
    void turnoReservadoElMismoDia() {
        // GIVEN un turno de "Paciente Uno" el 2026-12-02 a las 07:30 creado el 2026-12-01 a las 09:00
        hay(reservadoEl(turno(1, TURNO, conWhatsappYEmail()), en(2026, 12, 1, 9, 0)));

        // WHEN corre la tarea de recordatorios el 2026-12-01 a las 09:30
        reloj.en(en(2026, 12, 1, 9, 30));
        casoDeUso.ejecutar();

        // THEN no se envía ningún recordatorio para ese turno
        assertThat(whatsapp.enviados).isEmpty();
        assertThat(repositorio.notificaciones).isEmpty();
    }

    @Test
    @DisplayName("Turno reservado con exactamente 24 horas de anticipación")
    void turnoReservadoConExactamente24HorasDeAnticipacion() {
        // GIVEN un turno de "Paciente Uno" el 2026-12-02 a las 09:00 creado el 2026-12-01 a las 09:00
        hay(reservadoEl(turno(1, en(2026, 12, 2, 9, 0), conWhatsappYEmail()), en(2026, 12, 1, 9, 0)));

        // WHEN corre la tarea de recordatorios el 2026-12-01 a las 09:00
        reloj.en(en(2026, 12, 1, 9, 0));
        casoDeUso.ejecutar();

        // THEN se envía el recordatorio
        assertThat(whatsapp.enviados).hasSize(1);
    }

    @Test
    void unTurnoSinRegistroDeAuditoriaSeTrataComoReservadoConTiempo() {
        hay(reservadoEl(turno(1, TURNO, conWhatsappYEmail()), null));

        casoDeUso.ejecutar();

        assertThat(whatsapp.enviados).hasSize(1);
    }

    // ---------- Requirement: Solo con consentimiento ----------

    @Test
    @DisplayName("Paciente sin consentimiento")
    void pacienteSinConsentimiento() {
        // GIVEN un turno RESERVADO de "Paciente Uno" el 2026-12-02 a las 07:30, reservado el 2026-11-28
        // AND "Paciente Uno" sin consentimiento de comunicaciones
        hay(turno(1, TURNO, new ContactoPaciente("paciente.uno@example.com", "+54 11 5555-0001", true, false)));

        // WHEN corre la tarea de recordatorios
        casoDeUso.ejecutar();

        // THEN no se envía ningún mensaje y no se crea ninguna notificación
        assertThat(whatsapp.enviados).isEmpty();
        assertThat(email.enviados).isEmpty();
        assertThat(repositorio.notificaciones).isEmpty();
    }

    @Test
    @DisplayName("El consentimiento otorgado después se respeta")
    void elConsentimientoOtorgadoDespuesSeRespeta() {
        // GIVEN el mismo turno y "Paciente Uno" sin consentimiento en la primera corrida
        hay(turno(1, TURNO, new ContactoPaciente("paciente.uno@example.com", "+54 11 5555-0001", true, false)));
        casoDeUso.ejecutar();
        assertThat(repositorio.notificaciones).isEmpty();

        // WHEN "Paciente Uno" otorga el consentimiento y la tarea corre otra vez dentro de las 24 horas
        hay(turno(1, TURNO, conWhatsappYEmail()));
        reloj.adelantarMinutos(5);
        casoDeUso.ejecutar();

        // THEN se envía el recordatorio
        assertThat(whatsapp.enviados).hasSize(1);
    }

    // ---------- Requirement: Elección del canal ----------

    @Test
    @DisplayName("Paciente sin ningún contacto")
    void pacienteSinNingunContacto() {
        // GIVEN "Paciente Uno" con consentimiento, sin WhatsApp y sin email
        hay(turno(1, TURNO, new ContactoPaciente(null, null, false, true)));

        // WHEN corre la tarea de recordatorios
        casoDeUso.ejecutar();

        // THEN no se envía ningún mensaje y no se crea ninguna notificación
        assertThat(whatsapp.enviados).isEmpty();
        assertThat(email.enviados).isEmpty();
        assertThat(repositorio.notificaciones).isEmpty();
    }

    // ---------- Requirement: Reintentos y respaldo por email ----------

    @Test
    @DisplayName("Falla y reintento")
    void fallaYReintento() {
        // GIVEN un recordatorio por WhatsApp cuyo envío falla
        hay(turno(1, TURNO, conWhatsappYEmail()));
        whatsapp.falla = true;

        // WHEN corre la tarea de recordatorios
        casoDeUso.ejecutar();

        // THEN la notificación queda FALLIDA con un reintento programado más adelante
        Notificacion n = repositorio.unica();
        assertThat(n.estado()).isEqualTo(EstadoNotificacion.FALLIDA);
        assertThat(n.intentos()).isEqualTo(1);
        assertThat(n.proximoIntento()).isEqualTo(ahora().plus(Duration.ofMinutes(5)));
        // AND el turno no recibe un segundo recordatorio por otro canal todavía
        assertThat(email.intentos).isZero();
        reloj.adelantarMinutos(1);
        casoDeUso.ejecutar();
        assertThat(whatsapp.intentos).isEqualTo(1);
        assertThat(email.intentos).isZero();
        assertThat(repositorio.notificaciones).hasSize(1);
    }

    @Test
    @DisplayName("Tres fallas por WhatsApp con email disponible")
    void tresFallasPorWhatsappConEmailDisponible() {
        // GIVEN un recordatorio por WhatsApp que falló 3 veces y "Paciente Uno" con email
        hay(turno(1, TURNO, conWhatsappYEmail()));
        whatsapp.falla = true;
        fallarTresVeces();
        assertThat(whatsapp.intentos).isEqualTo(3);
        assertThat(repositorio.unica().estado()).isEqualTo(EstadoNotificacion.FALLIDA);

        // WHEN corre la tarea de recordatorios
        reloj.adelantarMinutos(20);
        casoDeUso.ejecutar();

        // THEN se envía el recordatorio por email (la misma fila pasa a EMAIL como canal alternativo)
        assertThat(email.enviados).hasSize(1);
        assertThat(email.enviados.get(0).destino()).isEqualTo("paciente.uno@example.com");
        Notificacion n = repositorio.unica();
        assertThat(n.canal()).isEqualTo(Canal.EMAIL);
        assertThat(n.canalAlternativo()).isTrue();
        assertThat(n.estado()).isEqualTo(EstadoNotificacion.ENVIADA);
    }

    @Test
    @DisplayName("Tres fallas sin email")
    void tresFallasSinEmail() {
        // GIVEN un recordatorio por WhatsApp que falló 3 veces y "Paciente Uno" sin email
        hay(turno(1, TURNO, soloWhatsapp()));
        whatsapp.falla = true;
        fallarTresVeces();

        // WHEN corre la tarea de recordatorios
        reloj.adelantarMinutos(60);
        casoDeUso.ejecutar();

        // THEN la notificación queda FALLIDA sin nuevos reintentos
        Notificacion n = repositorio.unica();
        assertThat(n.estado()).isEqualTo(EstadoNotificacion.FALLIDA);
        assertThat(n.proximoIntento()).isNull();
        assertThat(whatsapp.intentos).isEqualTo(3);
        assertThat(email.intentos).isZero();
    }

    @Test
    void unReintentoAntesDelProximoIntentoNoHaceNada() {
        hay(turno(1, TURNO, conWhatsappYEmail()));
        whatsapp.falla = true;
        casoDeUso.ejecutar();

        reloj.adelantarMinutos(4);
        casoDeUso.ejecutar();

        assertThat(whatsapp.intentos).isEqualTo(1);
        assertThat(repositorio.unica().intentos()).isEqualTo(1);
    }

    @Test
    void elTurnoYaCanceladoNoSeReintenta() {
        hay(turno(1, TURNO, conWhatsappYEmail()));
        whatsapp.falla = true;
        casoDeUso.ejecutar();
        hay(inactivo(turno(1, TURNO, conWhatsappYEmail())));

        reloj.adelantarMinutos(10);
        casoDeUso.ejecutar();

        assertThat(whatsapp.intentos).isEqualTo(1);
        assertThat(repositorio.unica().proximoIntento()).isNull();
    }

    @Test
    void elReintentoPorWhatsappSeRecuperaSiElProveedorVuelve() {
        hay(turno(1, TURNO, conWhatsappYEmail()));
        whatsapp.falla = true;
        casoDeUso.ejecutar();
        whatsapp.falla = false;

        reloj.adelantarMinutos(5);
        casoDeUso.ejecutar();

        assertThat(whatsapp.enviados).hasSize(1);
        assertThat(repositorio.unica().estado()).isEqualTo(EstadoNotificacion.ENVIADA);
        assertThat(repositorio.unica().canalAlternativo()).isFalse();
    }

    // ---------- Requirement: Recuperación de un envío interrumpido ----------

    private Notificacion pendienteDesde(Instant creada, String token) {
        return repositorio.insertar(Notificacion.pendiente(1L, Canal.WHATSAPP, TipoNotificacion.RECORDATORIO, creada,
                HuellaToken.de(token)));
    }

    @Test
    @DisplayName("Notificación pendiente que quedó sin enviar")
    void notificacionPendienteQueQuedoSinEnviar() {
        // GIVEN una notificación RECORDATORIO en estado PENDIENTE creada hace más de 10 minutos, para un turno que
        // sigue activo y con consentimiento
        hay(turno(1, TURNO, conWhatsappYEmail()));
        pendienteDesde(ahora().minus(Duration.ofMinutes(11)), "token-viejo");

        // WHEN corre la tarea de recordatorios
        casoDeUso.ejecutar();

        // THEN se envía el recordatorio y la notificación queda ENVIADA
        assertThat(whatsapp.enviados).hasSize(1);
        Notificacion n = repositorio.unica();
        assertThat(n.estado()).isEqualTo(EstadoNotificacion.ENVIADA);
        // el token del intento viejo no se puede recuperar: el link lleva uno nuevo y la huella lo acompaña
        assertThat(n.tokenConfirmacion()).isNotEqualTo(HuellaToken.de("token-viejo"));
    }

    @Test
    @DisplayName("Notificación pendiente reciente")
    void notificacionPendienteReciente() {
        // GIVEN una notificación RECORDATORIO en estado PENDIENTE creada hace menos de 10 minutos
        hay(turno(1, TURNO, conWhatsappYEmail()));
        pendienteDesde(ahora().minus(Duration.ofMinutes(9)), "token-reciente");

        // WHEN corre la tarea de recordatorios
        casoDeUso.ejecutar();

        // THEN no se envía nada todavía
        assertThat(whatsapp.enviados).isEmpty();
        assertThat(repositorio.unica().estado()).isEqualTo(EstadoNotificacion.PENDIENTE);
    }

    @Test
    @DisplayName("Dos corridas simultáneas sobre la misma notificación")
    void dosCorridasSimultaneasSobreLaMismaNotificacion() throws Exception {
        // GIVEN una notificación FALLIDA con reintento vencido
        hay(turno(1, TURNO, conWhatsappYEmail()));
        Notificacion fallida = pendienteDesde(ahora().minus(Duration.ofMinutes(30)), "token-1")
                .fallida(ahora().minus(Duration.ofMinutes(10)), true);
        repositorio.actualizar(fallida);
        // las dos corridas ven la misma lista de vencidas antes de que alguna la reclame
        CyclicBarrier ambasVieron = new CyclicBarrier(2);
        RepositorioEnMemoria conBarrera = new RepositorioEnMemoria(turnos) {
            @Override
            public List<Reintento> reintentosVencidos(Instant ahora) {
                List<Reintento> vistos = repositorio.reintentosVencidos(ahora);
                try {
                    ambasVieron.await(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                return vistos;
            }

            @Override
            public boolean reclamar(Notificacion vista, Instant ahora) {
                return repositorio.reclamar(vista, ahora);
            }

            @Override
            public void actualizar(Notificacion notificacion) {
                repositorio.actualizar(notificacion);
            }

            @Override
            public boolean existeRecordatorio(Long turnoId) {
                return repositorio.existeRecordatorio(turnoId);
            }
        };
        EnviarRecordatorios corrida = new EnviarRecordatorios(turnos, conBarrera, List.of(whatsapp, email),
                new TokensSecuenciales(), CONSULTORIO, BASE_URL, reloj);

        // WHEN dos corridas de la tarea la procesan al mismo tiempo
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = pool.submit(corrida::ejecutar);
            Future<?> b = pool.submit(corrida::ejecutar);
            a.get(10, TimeUnit.SECONDS);
            b.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        // THEN se envía una sola vez
        assertThat(whatsapp.enviados).hasSize(1);
        assertThat(repositorio.unica().estado()).isEqualTo(EstadoNotificacion.ENVIADA);
    }

    @Test
    void unaPendienteViejaConElTurnoCanceladoYaIniciadoOSinConsentimientoNoSeReprocesa() {
        hay(inactivo(turno(1, TURNO, conWhatsappYEmail())));
        hay(turno(2, en(2026, 12, 1, 7, 0), conWhatsappYEmail()));
        hay(turno(3, TURNO, new ContactoPaciente("paciente.uno@example.com", "+54 11 5555-0001", true, false)));
        for (long id = 1; id <= 3; id++) {
            repositorio.insertar(Notificacion.pendiente(id, Canal.WHATSAPP, TipoNotificacion.RECORDATORIO,
                    ahora().minus(Duration.ofMinutes(30)), HuellaToken.de("t" + id)));
        }

        casoDeUso.ejecutar();

        assertThat(whatsapp.enviados).isEmpty();
        assertThat(repositorio.notificaciones).allSatisfy(n -> {
            assertThat(n.estado()).isEqualTo(EstadoNotificacion.FALLIDA);
            assertThat(n.proximoIntento()).isNull();
        });
    }

    @Test
    void unRecordatorioReintentadoRenuevaSuToken() {
        hay(turno(1, TURNO, conWhatsappYEmail()));
        whatsapp.falla = true;
        casoDeUso.ejecutar();
        String primera = repositorio.unica().tokenConfirmacion();

        reloj.adelantarMinutos(5);
        casoDeUso.ejecutar();

        assertThat(repositorio.unica().tokenConfirmacion()).isNotNull().isNotEqualTo(primera);
    }

    // ---------- Robustez: una fila que falla no corta la corrida ----------

    private static final String DATO_PERSONAL = "paciente.uno@example.com";

    /** Captura todo lo que se registra en el log durante el test (y lo suelta al terminar). */
    private final ch.qos.logback.classic.Logger raiz =
            (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    private final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> registro =
            new ch.qos.logback.core.read.ListAppender<>();

    @org.junit.jupiter.api.BeforeEach
    void capturarElLog() {
        registro.start();
        raiz.addAppender(registro);
    }

    @org.junit.jupiter.api.AfterEach
    void soltarElLog() {
        raiz.detachAppender(registro);
    }

    private String todoElLog() {
        return registro.list.stream().map(e -> e.getFormattedMessage() + " " + e.getThrowableProxy())
                .reduce("", (a, b) -> a + "\n" + b);
    }

    @Test
    @DisplayName("Una fila que falla no corta el resto de la corrida")
    void unaFilaQueFallaNoCortaElRestoDeLaCorrida() {
        // GIVEN dos turnos a recordar y un repositorio que lanza al insertar el del primero (con un dato personal
        // en el mensaje de la excepción)
        hay(turno(1, TURNO, conWhatsappYEmail()));
        hay(turno(2, TURNO, conWhatsappYEmail()));
        RepositorioEnMemoria quePuedeFallar = new RepositorioEnMemoria(turnos) {
            @Override
            public synchronized Optional<Notificacion> insertarRecordatorio(Notificacion notificacion) {
                if (notificacion.turnoId() == 1L) {
                    throw new RuntimeException(DATO_PERSONAL + " +54 11 5555-0001");
                }
                return super.insertarRecordatorio(notificacion);
            }
        };
        EnviarRecordatorios corrida = new EnviarRecordatorios(turnos, quePuedeFallar, List.of(whatsapp, email),
                new TokensSecuenciales(), CONSULTORIO, BASE_URL, reloj);

        // WHEN corre la tarea
        EnviarRecordatorios.Resumen resumen = corrida.ejecutar();

        // THEN el turno sano de después sí se envía
        assertThat(resumen.nuevas()).isEqualTo(1);
        assertThat(whatsapp.enviados).hasSize(1);
        assertThat(quePuedeFallar.notificaciones).extracting(Notificacion::turnoId).containsExactly(2L);
        // AND el log tiene solo el tipo de la excepción: ni el mensaje, ni el contacto
        assertThat(todoElLog()).contains("RuntimeException").doesNotContain(DATO_PERSONAL, "5555");
    }

    @Test
    @DisplayName("Un reintento que falla no corta el resto de la corrida")
    void unReintentoQueFallaNoCortaElRestoDeLaCorrida() {
        // GIVEN una notificación PENDIENTE vieja cuyo procesamiento lanza (el generador de tokens falla la primera
        // vez) y otra FALLIDA sana, las dos vencidas
        hay(turno(1, en(2026, 12, 3, 10, 0), soloEmail()));
        hay(turno(2, TURNO, conWhatsappYEmail()));
        repositorio.insertar(Notificacion.pendiente(1L, Canal.EMAIL, TipoNotificacion.RECORDATORIO,
                ahora().minus(Duration.ofMinutes(30)), HuellaToken.de("t1")));
        EnviarRecordatorios corrida = new EnviarRecordatorios(turnos, repositorio, List.of(whatsapp, email),
                new TokensQueFallan(1), CONSULTORIO, BASE_URL, reloj);
        Notificacion sana = repositorio.insertar(Notificacion.pendiente(2L, Canal.WHATSAPP,
                TipoNotificacion.RECORDATORIO, ahora().minus(Duration.ofMinutes(30)), HuellaToken.de("t2")));
        repositorio.actualizar(sana.fallida(ahora().minus(Duration.ofMinutes(10)), true));

        // WHEN corre la tarea
        corrida.ejecutar();

        // THEN la sana se envía aunque la primera lanzó, y la que lanzó cuenta un intento fallido
        assertThat(whatsapp.enviados).hasSize(1);
        assertThat(repositorio.notificaciones.get(1).estado()).isEqualTo(EstadoNotificacion.ENVIADA);
        assertThat(repositorio.notificaciones.get(0).estado()).isEqualTo(EstadoNotificacion.FALLIDA);
        assertThat(repositorio.notificaciones.get(0).intentos()).isEqualTo(1);
        assertThat(todoElLog()).contains("IllegalStateException").doesNotContain(DATO_PERSONAL);
    }

    @Test
    @DisplayName("Una fila que siempre falla termina FALLIDA tras 3 intentos de recuperación")
    void unaFilaQueSiempreFallaTerminaFallidaTras3IntentosDeRecuperacion() {
        // GIVEN una notificación PENDIENTE vieja cuyo procesamiento siempre lanza (el generador de tokens falla)
        hay(turno(1, en(2026, 12, 3, 10, 0), soloEmail()));
        repositorio.insertar(Notificacion.pendiente(1L, Canal.EMAIL, TipoNotificacion.RECORDATORIO,
                ahora().minus(Duration.ofMinutes(30)), HuellaToken.de("t1")));
        EnviarRecordatorios casoDeUso = new EnviarRecordatorios(turnos, repositorio, List.of(whatsapp, email),
                new TokensQueFallan(Integer.MAX_VALUE), CONSULTORIO, BASE_URL, reloj);

        // WHEN la tarea la intenta recuperar una y otra vez
        casoDeUso.ejecutar();                       // intento 1, espera 5 min
        assertThat(repositorio.unica().intentos()).isEqualTo(1);
        reloj.adelantarMinutos(5);
        casoDeUso.ejecutar();                       // intento 2, espera 10 min
        assertThat(repositorio.unica().intentos()).isEqualTo(2);
        reloj.adelantarMinutos(10);
        casoDeUso.ejecutar();                       // intento 3: tope

        // THEN queda FALLIDA sin más reintentos y no se reprocesa para siempre
        Notificacion n = repositorio.unica();
        assertThat(n.estado()).isEqualTo(EstadoNotificacion.FALLIDA);
        assertThat(n.intentos()).isEqualTo(3);
        assertThat(n.proximoIntento()).isNull();
        reloj.adelantarMinutos(24 * 60);
        casoDeUso.ejecutar();
        assertThat(repositorio.unica().intentos()).isEqualTo(3);
        assertThat(email.intentos).isZero();
    }

    /** Generador que lanza en sus primeras {@code fallas} llamadas, con un mensaje que parece un dato personal. */
    private static final class TokensQueFallan implements GeneradorToken {
        private int quedan;

        TokensQueFallan(int fallas) {
            this.quedan = fallas;
        }

        @Override
        public String generar() {
            if (quedan > 0) {
                quedan--;
                throw new IllegalStateException(DATO_PERSONAL);
            }
            return "token-ficticio-sano";
        }
    }

    private void fallarTresVeces() {
        casoDeUso.ejecutar();            // falla 1: próximo intento en 5 minutos
        reloj.adelantarMinutos(5);
        casoDeUso.ejecutar();            // falla 2: próximo intento en 10 minutos
        reloj.adelantarMinutos(10);
        casoDeUso.ejecutar();            // falla 3: próximo intento en 20 minutos
    }
}
