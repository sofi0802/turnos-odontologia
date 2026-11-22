package ar.turnos.odontologia.agenda.infrastructure;

import ar.turnos.odontologia.agenda.application.CancelarTurno;
import ar.turnos.odontologia.agenda.application.ConfirmarTurno;
import ar.turnos.odontologia.agenda.application.CrearSobreturno;
import ar.turnos.odontologia.agenda.application.CrearTurno;
import ar.turnos.odontologia.agenda.application.DisponibilidadProfesional;
import ar.turnos.odontologia.agenda.application.ExtenderTurno;
import ar.turnos.odontologia.agenda.application.GeneradorNumeroReserva;
import ar.turnos.odontologia.agenda.application.MarcarAusencia;
import ar.turnos.odontologia.agenda.application.ReasignarTurno;
import ar.turnos.odontologia.agenda.application.RecursosAgenda;
import ar.turnos.odontologia.agenda.application.ReprogramarTurno;
import ar.turnos.odontologia.agenda.application.SillonesDisponibles;
import ar.turnos.odontologia.agenda.application.TurnoRepository;
import ar.turnos.odontologia.agenda.domain.Margen;
import ar.turnos.odontologia.configuracion.application.HabilitacionProfesional;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Arma los casos de uso de la agenda, que no dependen de Spring (design D5, D8). El margen y la zona horaria
 * salen de propiedades hasta que C-04 agregue la tabla {@code configuracion}. Desde C-07, crear, reprogramar,
 * extender y el sobreturno aplican las franjas, ausencias y bloqueos del profesional (design D2).
 */
@Configuration
class AgendaConfig {

    @Bean
    ZoneId zonaHoraria(@Value("${app.zona-horaria:America/Argentina/Buenos_Aires}") String zona) {
        return ZoneId.of(zona);
    }

    /** Los tests de integración lo reemplazan por un reloj fijo marcado como {@code @Primary}. */
    @Bean
    Clock reloj(ZoneId zonaHoraria) {
        return Clock.system(zonaHoraria);
    }

    /** Un margen menor a 10 minutos hace fallar el arranque con {@code MARGEN_INVALIDO} (RN-AG-04). */
    @Bean
    Margen margen(@Value("${app.agenda.margen-minutos:10}") int minutos) {
        return Margen.deMinutos(minutos);
    }

    @Bean
    GeneradorNumeroReserva generadorNumeroReserva(TurnoJpaRepository turnos) {
        return new GeneradorNumeroReservaAleatorio(new SecureRandom(), turnos::existsByCodigoReserva);
    }

    @Bean
    CrearTurno crearTurno(TurnoRepository turnos, RecursosAgenda recursos, GeneradorNumeroReserva numeros,
                          Clock reloj, Margen margen, DisponibilidadProfesional disponibilidad, ZoneId zonaHoraria,
                          HabilitacionProfesional habilitacion) {
        return new CrearTurno(turnos, recursos, numeros, reloj, margen, disponibilidad, zonaHoraria, habilitacion);
    }

    @Bean
    CrearSobreturno crearSobreturno(TurnoRepository turnos, RecursosAgenda recursos,
                                    SillonesDisponibles sillones, GeneradorNumeroReserva numeros,
                                    Clock reloj, Margen margen, DisponibilidadProfesional disponibilidad,
                                    ZoneId zonaHoraria, HabilitacionProfesional habilitacion) {
        return new CrearSobreturno(turnos, recursos, sillones, numeros, reloj, margen, disponibilidad, zonaHoraria,
                habilitacion);
    }

    @Bean
    ExtenderTurno extenderTurno(TurnoRepository turnos, CrearTurno crearTurno) {
        return new ExtenderTurno(turnos, crearTurno);
    }

    @Bean
    CancelarTurno cancelarTurno(TurnoRepository turnos) {
        return new CancelarTurno(turnos);
    }

    @Bean
    ConfirmarTurno confirmarTurno(TurnoRepository turnos) {
        return new ConfirmarTurno(turnos);
    }

    @Bean
    ReprogramarTurno reprogramarTurno(TurnoRepository turnos, CrearTurno crearTurno) {
        return new ReprogramarTurno(turnos, crearTurno);
    }

    @Bean
    MarcarAusencia marcarAusencia(TurnoRepository turnos, Clock reloj) {
        return new MarcarAusencia(turnos, reloj);
    }

    @Bean
    ReasignarTurno reasignarTurno(TurnoRepository turnos, CrearTurno crearTurno, HabilitacionProfesional habilitacion) {
        return new ReasignarTurno(turnos, crearTurno, habilitacion);
    }
}
