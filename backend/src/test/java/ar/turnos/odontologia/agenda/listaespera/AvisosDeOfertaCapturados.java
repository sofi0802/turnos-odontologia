package ar.turnos.odontologia.agenda.listaespera;

import ar.turnos.odontologia.agenda.listaespera.application.PuertosListaEspera.AvisoOferta;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Soporte de los tests de la lista de espera: reemplaza el aviso real por uno que guarda lo que se avisó (con el token
 * en claro, como lo recibiría el paciente) y que puede fallar a pedido.
 */
@TestConfiguration
public class AvisosDeOfertaCapturados {

    public record Avisado(Long pacienteId, Instant inicio, Instant venceEn, String token) {}

    public static final class Registro implements AvisoOferta {
        public final List<Avisado> avisados = new ArrayList<>();
        public volatile boolean falla;

        public void reiniciar() {
            avisados.clear();
            falla = false;
        }

        public Avisado ultimo() {
            return avisados.get(avisados.size() - 1);
        }

        @Override
        public synchronized boolean avisar(Long pacienteId, Instant inicio, Instant venceEn, String token) {
            if (falla) {
                return false;
            }
            avisados.add(new Avisado(pacienteId, inicio, venceEn, token));
            return true;
        }
    }

    @Bean
    @Primary
    Registro avisosDeOfertaCapturados() {
        return new Registro();
    }
}
