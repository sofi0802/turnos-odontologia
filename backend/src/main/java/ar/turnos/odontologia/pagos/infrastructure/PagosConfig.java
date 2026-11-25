package ar.turnos.odontologia.pagos.infrastructure;

import ar.turnos.odontologia.agenda.application.SenasDeAgenda;
import ar.turnos.odontologia.agenda.infrastructure.ServicioAgenda;
import ar.turnos.odontologia.caja.application.TurnosACobrar;
import ar.turnos.odontologia.pagos.application.AvisoReintegroSena;
import ar.turnos.odontologia.pagos.application.CancelacionDelSistema;
import ar.turnos.odontologia.pagos.application.CrearSena;
import ar.turnos.odontologia.reserva.application.SenasDeReserva;
import ar.turnos.odontologia.pagos.application.ConfiguracionSenas;
import ar.turnos.odontologia.pagos.application.NotificacionDePago;
import ar.turnos.odontologia.pagos.application.PasarelaPago;
import ar.turnos.odontologia.pagos.application.Reintegros;
import ar.turnos.odontologia.pagos.application.Senas;
import java.time.Clock;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/** Beans del módulo {@code pagos} (design D1). */
@Configuration
class PagosConfig {

    @Bean
    ConfiguracionSenas configuracionSenas(JdbcTemplate jdbc) {
        return new ConfiguracionSenasJdbc(jdbc);
    }

    @Bean
    Senas senas(JdbcTemplate jdbc) {
        return new SenasJdbc(jdbc);
    }

    @Bean
    Reintegros reintegros(Senas senas, PasarelaPago pasarela, AvisoReintegroSena aviso, Clock reloj) {
        return new Reintegros(senas, pasarela, aviso, reloj);
    }

    @Bean
    NotificacionDePago notificacionDePago(Senas senas, PasarelaPago pasarela, Reintegros reintegros,
                                          PlatformTransactionManager transacciones, Clock reloj,
                                          @Value("${app.pagos.mercadopago.webhook-secret:}") String clave) {
        return new NotificacionDePago(senas, pasarela, reintegros, new TransactionTemplate(transacciones), reloj, clave);
    }

    @Bean
    CancelacionDelSistema cancelacionDelSistema(JdbcTemplate jdbc, ServicioAgenda agenda) {
        return new CancelacionDelSistemaAgenda(jdbc, agenda);
    }

    @Bean
    CrearSena crearSena(ConfiguracionSenas configuracion, TurnosACobrar turnos, Senas senas, PasarelaPago pasarela,
                        Clock reloj) {
        return new CrearSena(configuracion, turnos, senas, pasarela, reloj);
    }

    /** El puerto de la agenda (design D2): la cancelación pide la causa con la seña pagada. */
    @Bean
    SenasDeAgenda senasDeAgenda(JdbcTemplate jdbc) {
        return new SenasParaAgenda(jdbc);
    }

    /** El puerto de la reserva online (design D2): la reserva lo toma si existe. */
    @Bean
    SenasDeReserva senasDeReserva(CrearSena crear, Senas senas, PasarelaPago pasarela,
                                  CancelacionDelSistema cancelacion) {
        return new SenasParaReserva(crear, senas, pasarela, cancelacion);
    }

    /** La pasarela de {@code PAGOS_PASARELA}: {@code simulada} (por defecto) o {@code mercadopago} (design D6). */
    @Bean
    PasarelaPago pasarelaPago(@Value("${app.pagos.pasarela}") String tipo,
                              @Value("${app.pagos.mercadopago.url}") String urlApi,
                              @Value("${app.pagos.mercadopago.access-token:}") String accessToken,
                              @Value("${app.pagos.mercadopago.webhook-secret:}") String webhookSecret,
                              @Value("${app.pagos.url-publica}") String urlPublica,
                              @Value("${app.pagos.url-frontend}") String urlFrontend,
                              RestClient.Builder builder) {
        return switch (tipo) {
            case "simulada" -> new PasarelaSimulada();
            case "mercadopago" -> {
                PasarelaMercadoPago.verificarCredenciales(accessToken, webhookSecret);
                yield new PasarelaMercadoPago(builder.clone(), urlApi, accessToken, urlPublica, urlFrontend);
            }
            default -> throw new IllegalStateException("PAGOS_PASARELA tiene que ser 'simulada' o 'mercadopago'.");
        };
    }
}
