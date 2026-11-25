package ar.turnos.odontologia.reserva.infrastructure;

import ar.turnos.odontologia.agenda.application.DisponibilidadProfesional;
import ar.turnos.odontologia.agenda.application.TurnoRepository;
import ar.turnos.odontologia.agenda.domain.Margen;
import ar.turnos.odontologia.reserva.application.AgendaDeTurnos;
import ar.turnos.odontologia.reserva.application.AvisoReservaOnline;
import ar.turnos.odontologia.reserva.application.AvisoReservaOnlineInactivo;
import ar.turnos.odontologia.reserva.application.BuscadorDeHuecos;
import ar.turnos.odontologia.reserva.application.ConsultarMisTurnos;
import ar.turnos.odontologia.reserva.application.CancelarReservaOnline;
import ar.turnos.odontologia.reserva.application.ReprogramarReservaOnline;
import ar.turnos.odontologia.reserva.application.ReservaPorListaEspera;
import ar.turnos.odontologia.reserva.application.ReservarTurno;
import ar.turnos.odontologia.reserva.application.ReservasPaciente;
import ar.turnos.odontologia.reserva.application.CatalogoReserva;
import ar.turnos.odontologia.reserva.application.ConsultarCatalogo;
import ar.turnos.odontologia.reserva.application.ConsultarDisponibilidad;
import ar.turnos.odontologia.reserva.domain.Desempate;
import java.time.Clock;
import java.time.ZoneId;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Arma los casos de uso de la reserva online (C-10, design D1). Las clases de {@code reserva/domain} y
 * {@code reserva/application} no llevan anotaciones de Spring: se registran acá como beans. El reloj, la zona y el
 * margen son los de la agenda; el paso de la grilla es una propuesta configurable.
 */
@Configuration
class ReservaConfig {

    @Bean
    Desempate desempateAleatorio() {
        return new DesempateAleatorio(RandomGenerator.getDefault());
    }

    @Bean
    BuscadorDeHuecos buscadorDeHuecos(CatalogoReserva catalogo, TurnoRepository turnos,
                                      DisponibilidadProfesional disponibilidad, Margen margen, Clock reloj,
                                      ZoneId zonaHoraria, Desempate desempate,
                                      @Value("${app.reserva.paso-minutos:15}") int pasoMinutos) {
        return new BuscadorDeHuecos(catalogo, turnos, disponibilidad, margen, reloj, zonaHoraria, pasoMinutos,
                desempate);
    }

    /** Si ningún módulo publica un aviso real, la reserva usa uno que solo deja una línea de log (design D9). */
    /** C-15: la lista de espera reusa la validación y la asignación de sillón de la reserva online. */
    @Bean
    ReservaPorListaEspera reservaPorListaEspera(CatalogoReserva catalogo, BuscadorDeHuecos buscador,
                                                AgendaDeTurnos agenda, ObjectProvider<AvisoReservaOnline> avisos,
                                                Clock reloj, ZoneId zonaHoraria) {
        return new ReservaPorListaEspera(catalogo, buscador, agenda,
                avisos.getIfAvailable(AvisoReservaOnlineInactivo::new), reloj, zonaHoraria);
    }

    @Bean
    ReservarTurno reservarTurno(CatalogoReserva catalogo, BuscadorDeHuecos buscador, AgendaDeTurnos agenda,
                                ObjectProvider<AvisoReservaOnline> avisos, Clock reloj, ZoneId zonaHoraria,
                                @Value("${app.reserva.alternativas:3}") int alternativas) {
        return new ReservarTurno(catalogo, buscador, agenda, avisos.getIfAvailable(AvisoReservaOnlineInactivo::new),
                reloj, zonaHoraria, alternativas);
    }

    @Bean
    CancelarReservaOnline cancelarReservaOnline(ReservasPaciente reservas, AgendaDeTurnos agenda,
                                                ObjectProvider<AvisoReservaOnline> avisos, Clock reloj) {
        return new CancelarReservaOnline(reservas, agenda, avisos.getIfAvailable(AvisoReservaOnlineInactivo::new),
                reloj);
    }

    @Bean
    ReprogramarReservaOnline reprogramarReservaOnline(CatalogoReserva catalogo, ReservasPaciente reservas,
                                                      BuscadorDeHuecos buscador, AgendaDeTurnos agenda,
                                                      ObjectProvider<AvisoReservaOnline> avisos, Clock reloj,
                                                      ZoneId zonaHoraria,
                                                      @Value("${app.reserva.alternativas:3}") int alternativas) {
        return new ReprogramarReservaOnline(catalogo, reservas, buscador, agenda,
                avisos.getIfAvailable(AvisoReservaOnlineInactivo::new), reloj, zonaHoraria, alternativas);
    }

    @Bean
    ConsultarMisTurnos consultarMisTurnos(ReservasPaciente reservas, Clock reloj) {
        return new ConsultarMisTurnos(reservas, reloj);
    }

    @Bean
    ConsultarCatalogo consultarCatalogo(CatalogoReserva catalogo) {
        return new ConsultarCatalogo(catalogo);
    }

    @Bean
    ConsultarDisponibilidad consultarDisponibilidad(CatalogoReserva catalogo, BuscadorDeHuecos buscador) {
        return new ConsultarDisponibilidad(catalogo, buscador);
    }
}
