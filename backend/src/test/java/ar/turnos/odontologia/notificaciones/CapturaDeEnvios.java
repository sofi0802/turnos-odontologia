package ar.turnos.odontologia.notificaciones;

import ar.turnos.odontologia.notificaciones.application.AvisarOperacionOnline;
import ar.turnos.odontologia.notificaciones.application.AvisarReasignacion;
import ar.turnos.odontologia.notificaciones.application.EnviarRecordatorios;
import ar.turnos.odontologia.notificaciones.application.GeneradorToken;
import ar.turnos.odontologia.notificaciones.application.NotificacionPort;
import ar.turnos.odontologia.notificaciones.application.NotificacionRepository;
import ar.turnos.odontologia.notificaciones.application.TurnosARecordar;
import ar.turnos.odontologia.notificaciones.domain.Canal;
import ar.turnos.odontologia.notificaciones.domain.Consultorio;
import ar.turnos.odontologia.notificaciones.domain.Enlaces;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Soporte de los tests de integración: reemplaza los casos de uso de envío por otros que usan canales que
 * guardan lo enviado (el adaptador real no registra el cuerpo ni el token) y que pueden fallar a pedido. Así un
 * test puede leer el link del recordatorio, como lo haría el paciente.
 */
@TestConfiguration
public class CapturaDeEnvios {

    /** Los dos canales capturadores. No son beans: no se mezclan con los adaptadores simulados reales. */
    public static final class Registro {
        public final Capturador whatsapp = new Capturador(Canal.WHATSAPP);
        public final Capturador email = new Capturador(Canal.EMAIL);

        public void reiniciar() {
            whatsapp.reiniciar();
            email.reiniciar();
        }

        /** El token del último link de confirmación enviado por cualquier canal. */
        public String ultimoToken() {
            List<NotificacionPort.EnvioNotificacion> todos = new ArrayList<>(whatsapp.enviados);
            todos.addAll(email.enviados);
            String cuerpo = todos.get(todos.size() - 1).cuerpo();
            return cuerpo.substring(cuerpo.lastIndexOf("/confirmar/") + "/confirmar/".length());
        }

        public int totalEnviados() {
            return whatsapp.enviados.size() + email.enviados.size();
        }
    }

    public static final class Capturador implements NotificacionPort {
        private final Canal canal;
        public final List<EnvioNotificacion> enviados = new ArrayList<>();
        public volatile boolean falla;
        /** Simula que el proceso se corta justo al enviar: lanza un Error que ningún caso de uso captura. */
        public volatile boolean corte;

        Capturador(Canal canal) {
            this.canal = canal;
        }

        void reiniciar() {
            enviados.clear();
            falla = false;
            corte = false;
        }

        @Override
        public Canal canal() {
            return canal;
        }

        @Override
        public synchronized ResultadoEnvio enviar(EnvioNotificacion envio) {
            if (corte) {
                throw new Error("corte simulado");
            }
            if (falla) {
                return ResultadoEnvio.FALLO;
            }
            enviados.add(envio);
            return ResultadoEnvio.OK;
        }
    }

    @Bean
    Registro registroDeEnvios() {
        return new Registro();
    }

    @Bean
    @Primary
    EnviarRecordatorios enviarRecordatoriosCapturado(Registro registro, TurnosARecordar turnos,
            NotificacionRepository notificaciones, GeneradorToken tokens, Consultorio consultorio,
            Enlaces enlaces, Clock reloj) {
        return new EnviarRecordatorios(turnos, notificaciones, List.of(registro.whatsapp, registro.email), tokens,
                consultorio, enlaces, reloj);
    }

    @Bean
    @Primary
    AvisarOperacionOnline avisarOperacionOnlineCapturado(Registro registro, TurnosARecordar turnos,
            NotificacionRepository notificaciones, Consultorio consultorio, Enlaces enlaces, Clock reloj) {
        return new AvisarOperacionOnline(turnos, notificaciones, List.of(registro.whatsapp, registro.email),
                consultorio, enlaces, reloj);
    }

    @Bean
    @Primary
    AvisarReasignacion avisarReasignacionCapturado(Registro registro, TurnosARecordar turnos,
            NotificacionRepository notificaciones, Consultorio consultorio, Clock reloj) {
        return new AvisarReasignacion(turnos, notificaciones, List.of(registro.whatsapp, registro.email),
                consultorio, reloj);
    }
}
