package ar.turnos.odontologia.agenda.listaespera.infrastructure;

import ar.turnos.odontologia.agenda.listaespera.application.AgendaListaEspera;
import ar.turnos.odontologia.agenda.listaespera.application.ListaEspera;
import ar.turnos.odontologia.agenda.listaespera.application.ListaEsperaRepository;
import ar.turnos.odontologia.agenda.listaespera.application.PuertosListaEspera.AvisoOferta;
import ar.turnos.odontologia.agenda.listaespera.application.PuertosListaEspera.PlazoVigente;
import ar.turnos.odontologia.notificaciones.application.GeneradorToken;
import ar.turnos.odontologia.reserva.application.ReservaPorListaEspera;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Arma la lista de espera (C-15), que no depende de Spring en su dominio ni en sus casos de uso. */
@Configuration
class ListaEsperaConfig {

    @Bean
    AgendaListaEspera agendaListaEspera(ReservaPorListaEspera reserva) {
        return new AdaptadoresListaEspera.AgendaPorReserva(reserva);
    }

    @Bean
    PlazoVigente plazoListaEspera(JdbcTemplate jdbc) {
        return new AdaptadoresListaEspera.PlazoJdbc(jdbc);
    }

    @Bean
    ListaEspera listaEspera(ListaEsperaRepository repositorio, AgendaListaEspera agenda, JdbcTemplate jdbc,
                            PlazoVigente plazo, GeneradorToken generador, Clock reloj, ZoneId zonaHoraria) {
        return new ListaEspera(repositorio, agenda, new AdaptadoresListaEspera.PacientesJdbc(jdbc), plazo,
                new AdaptadoresListaEspera.TokensSeguros(generador), reloj, zonaHoraria);
    }

    /** Sin el módulo de notificaciones, la oferta se guarda pero el aviso nunca sale (y la tarea lo reintenta). */
    @Bean
    ServicioListaEspera servicioListaEspera(ListaEspera casos, AgendaListaEspera agenda, PlazoVigente plazo,
                                            ObjectProvider<AvisoOferta> avisos,
                                            PlatformTransactionManager transacciones) {
        return new ServicioListaEspera(casos, agenda, plazo,
                avisos.getIfAvailable(() -> (paciente, inicio, vence, token) -> false), transacciones);
    }
}
