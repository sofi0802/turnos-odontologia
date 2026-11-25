package ar.turnos.odontologia.agenda.listaespera.infrastructure;

import ar.turnos.odontologia.agenda.domain.ReferenciaInexistenteException;
import ar.turnos.odontologia.agenda.domain.TurnoRechazadoException;
import ar.turnos.odontologia.agenda.listaespera.application.AgendaListaEspera;
import ar.turnos.odontologia.agenda.listaespera.application.PuertosListaEspera.Pacientes;
import ar.turnos.odontologia.agenda.listaespera.application.PuertosListaEspera.PlazoVigente;
import ar.turnos.odontologia.agenda.listaespera.application.PuertosListaEspera.Tokens;
import ar.turnos.odontologia.agenda.listaespera.domain.CodigoListaEspera;
import ar.turnos.odontologia.agenda.listaespera.domain.ListaEsperaException;
import ar.turnos.odontologia.agenda.listaespera.domain.PlazoOferta;
import ar.turnos.odontologia.notificaciones.application.GeneradorToken;
import ar.turnos.odontologia.notificaciones.application.HuellaToken;
import ar.turnos.odontologia.reserva.application.ReservaPorListaEspera;
import ar.turnos.odontologia.reserva.application.ReservasPaciente.TurnoDelPaciente;
import ar.turnos.odontologia.reserva.domain.ReservaRechazadaException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/** Adaptadores chicos de los puertos de la lista de espera (C-15). Los arma {@link ListaEsperaConfig}. */
final class AdaptadoresListaEspera {

    private AdaptadoresListaEspera() {}

    /** Las reglas de la agenda a través de la reserva online (design D3, D4). */
    static final class AgendaPorReserva implements AgendaListaEspera {

        private final ReservaPorListaEspera reserva;

        AgendaPorReserva(ReservaPorListaEspera reserva) {
            this.reserva = reserva;
        }

        @Override
        public void validarPreferencia(Long prestacionId, Long profesionalId) {
            try {
                reserva.validarPreferencia(prestacionId, profesionalId);
            } catch (ReferenciaInexistenteException e) {
                throw new ListaEsperaException(CodigoListaEspera.RECURSO_NO_ENCONTRADO,
                        "La prestación o el profesional no existen");
            } catch (ReservaRechazadaException e) {
                CodigoListaEspera codigo = "PROFESIONAL_NO_HABILITADO".equals(e.codigo())
                        ? CodigoListaEspera.PROFESIONAL_NO_HABILITADO : CodigoListaEspera.RECURSO_INACTIVO;
                throw new ListaEsperaException(codigo, e.getMessage());
            }
        }

        @Override
        public boolean hayLugar(Long prestacionId, Long profesionalId, Instant inicio) {
            return reserva.hayLugar(prestacionId, profesionalId, inicio);
        }

        @Override
        public TurnoReservado reservar(Long pacienteId, Long prestacionId, Long profesionalId, Instant inicio,
                                       String actor) {
            try {
                TurnoDelPaciente turno = reserva.reservar(pacienteId, prestacionId, profesionalId, inicio, actor);
                return new TurnoReservado(turno.turnoId(), turno.numeroReserva(), turno.inicio(),
                        turno.profesionalNombre());
            } catch (ReservaRechazadaException | TurnoRechazadoException | ReferenciaInexistenteException e) {
                throw new ListaEsperaException(CodigoListaEspera.OFERTA_NO_DISPONIBLE, "El horario ya no está libre");
            }
        }
    }

    /** El consentimiento del paciente, leído de la tabla {@code paciente}. */
    static final class PacientesJdbc implements Pacientes {

        private final JdbcTemplate jdbc;

        PacientesJdbc(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public Optional<Boolean> consentimiento(Long pacienteId) {
            return jdbc.query("SELECT consentimiento_comunicaciones FROM paciente WHERE id = ?",
                    (rs, fila) -> rs.getBoolean(1), pacienteId).stream().findFirst();
        }
    }

    /** El plazo en {@code configuracion}, leído en cada llamada para que un cambio aplique de inmediato. */
    static final class PlazoJdbc implements PlazoVigente {

        static final String CLAVE = "lista_espera_plazo_horas";

        private final JdbcTemplate jdbc;

        PlazoJdbc(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public PlazoOferta actual() {
            return jdbc.query("SELECT valor FROM configuracion WHERE clave = ?", (rs, fila) -> rs.getString(1), CLAVE)
                    .stream().findFirst()
                    .map(valor -> PlazoOferta.deHoras(Integer.parseInt(valor)))
                    .orElse(PlazoOferta.PREDETERMINADO);
        }

        @Override
        public void guardar(PlazoOferta plazo) {
            jdbc.update("INSERT INTO configuracion (clave, valor) VALUES (?, ?) "
                    + "ON CONFLICT (clave) DO UPDATE SET valor = EXCLUDED.valor", CLAVE, String.valueOf(plazo.horas()));
        }
    }

    /** Los mismos tokens que la confirmación de C-08: 256 bits en base64url y huella SHA-256. */
    static final class TokensSeguros implements Tokens {

        private final GeneradorToken generador;

        TokensSeguros(GeneradorToken generador) {
            this.generador = generador;
        }

        @Override
        public String generar() {
            return generador.generar();
        }

        @Override
        public String huella(String token) {
            return HuellaToken.de(token);
        }
    }
}
