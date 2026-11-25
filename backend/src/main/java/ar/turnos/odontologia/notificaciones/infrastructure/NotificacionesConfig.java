package ar.turnos.odontologia.notificaciones.infrastructure;

import ar.turnos.odontologia.notificaciones.application.AvisarOperacionOnline;
import ar.turnos.odontologia.notificaciones.application.AvisarReasignacion;
import ar.turnos.odontologia.notificaciones.application.CambiarConsentimiento;
import ar.turnos.odontologia.notificaciones.application.ConfirmadorDeTurnos;
import ar.turnos.odontologia.notificaciones.application.ConsentimientoRepository;
import ar.turnos.odontologia.notificaciones.application.ConfirmarAsistencia;
import ar.turnos.odontologia.notificaciones.application.EnviarRecordatorios;
import ar.turnos.odontologia.notificaciones.application.GeneradorToken;
import ar.turnos.odontologia.notificaciones.application.NotificacionPort;
import ar.turnos.odontologia.notificaciones.application.NotificacionRepository;
import ar.turnos.odontologia.notificaciones.application.TurnosARecordar;
import ar.turnos.odontologia.notificaciones.domain.Consultorio;
import ar.turnos.odontologia.notificaciones.domain.Enlaces;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Arma los casos de uso de notificaciones, que no dependen de Spring. El nombre y la dirección del consultorio y la
 * URL base del link salen de variables de entorno ({@code CONSULTORIO_NOMBRE}, {@code CONSULTORIO_DIRECCION},
 * {@code NOTIFICACIONES_BASE_URL}) hasta que C-04 los guarde en {@code configuracion} (design, preguntas abiertas).
 */
@Configuration
class NotificacionesConfig {

    @Bean
    Consultorio consultorio(@Value("${app.consultorio.nombre:Consultorio odontológico}") String nombre,
                            @Value("${app.consultorio.direccion:Dirección a confirmar}") String direccion) {
        return new Consultorio(nombre, direccion);
    }

    @Bean
    GeneradorToken generadorToken() {
        return new GeneradorTokenSeguro(new SecureRandom());
    }

    /**
     * Los links de los mensajes: {@code NOTIFICACIONES_BASE_URL} más las rutas de las pantallas del paciente
     * ({@code NOTIFICACIONES_RUTA_CANCELAR} y {@code NOTIFICACIONES_RUTA_REPROGRAMAR}, que define el frontend).
     */
    @Bean
    Enlaces enlaces(@Value("${app.notificaciones.base-url:http://localhost:5173}") String baseUrl,
                    @Value("${app.notificaciones.ruta-cancelar:" + Enlaces.RUTA_CANCELAR + "}") String rutaCancelar,
                    @Value("${app.notificaciones.ruta-reprogramar:" + Enlaces.RUTA_REPROGRAMAR + "}")
                    String rutaReprogramar) {
        return new Enlaces(baseUrl, rutaCancelar, rutaReprogramar);
    }

    @Bean
    EnviarRecordatorios enviarRecordatorios(TurnosARecordar turnos, NotificacionRepository notificaciones,
            List<NotificacionPort> canales, GeneradorToken tokens, Consultorio consultorio, Enlaces enlaces,
            Clock reloj) {
        return new EnviarRecordatorios(turnos, notificaciones, canales, tokens, consultorio, enlaces, reloj);
    }

    @Bean
    AvisarOperacionOnline avisarOperacionOnline(TurnosARecordar turnos, NotificacionRepository notificaciones,
            List<NotificacionPort> canales, Consultorio consultorio, Enlaces enlaces, Clock reloj) {
        return new AvisarOperacionOnline(turnos, notificaciones, canales, consultorio, enlaces, reloj);
    }

    @Bean
    AvisarReasignacion avisarReasignacion(TurnosARecordar turnos, NotificacionRepository notificaciones,
            List<NotificacionPort> canales, Consultorio consultorio, Clock reloj) {
        return new AvisarReasignacion(turnos, notificaciones, canales, consultorio, reloj);
    }

    @Bean
    CambiarConsentimiento cambiarConsentimiento(ConsentimientoRepository repositorio, Clock reloj) {
        return new CambiarConsentimiento(repositorio, reloj);
    }

    @Bean
    ConfirmarAsistencia confirmarAsistencia(NotificacionRepository notificaciones, ConfirmadorDeTurnos confirmador,
                                            Clock reloj) {
        return new ConfirmarAsistencia(notificaciones, confirmador, reloj);
    }
}
