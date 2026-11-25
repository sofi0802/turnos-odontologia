package ar.turnos.odontologia.agenda.listaespera.api;

import ar.turnos.odontologia.agenda.listaespera.application.AgendaListaEspera.TurnoReservado;
import ar.turnos.odontologia.agenda.listaespera.application.ListaEsperaRepository.VistaOferta;
import ar.turnos.odontologia.agenda.listaespera.infrastructure.ServicioListaEspera;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La oferta desde el link del mensaje (C-15, design D5): pública, como la confirmación de C-08. Ver no consume el
 * token, porque los revisores de links abren las direcciones solos; aceptar y rechazar son {@code POST} con CSRF. Nada
 * de lo que se devuelve nombra la prestación.
 */
@RestController
@RequestMapping("/api/v1/public/ofertas/{token}")
class OfertasController {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final ServicioListaEspera servicio;
    private final ZoneId zona;

    OfertasController(ServicioListaEspera servicio, ZoneId zonaHoraria) {
        this.servicio = servicio;
        this.zona = zonaHoraria;
    }

    record OfertaResponse(String fecha, String hora, String profesional, OffsetDateTime venceEn, String estado) {}

    record AceptacionResponse(String fecha, String hora, String profesional, String numeroReserva) {}

    record RechazoResponse(String estado) {}

    @GetMapping
    OfertaResponse ver(@PathVariable String token) {
        VistaOferta vista = servicio.ver(token);
        var inicio = vista.inicio().atZone(zona);
        return new OfertaResponse(FECHA.format(inicio), HORA.format(inicio), vista.profesionalNombre(),
                vista.venceEn().atZone(zona).toOffsetDateTime(), vista.estado().name());
    }

    @PostMapping("/aceptar")
    AceptacionResponse aceptar(@PathVariable String token) {
        TurnoReservado turno = servicio.aceptar(token);
        var inicio = turno.inicio().atZone(zona);
        return new AceptacionResponse(FECHA.format(inicio), HORA.format(inicio), turno.profesionalNombre(),
                turno.numeroReserva());
    }

    @PostMapping("/rechazar")
    RechazoResponse rechazar(@PathVariable String token) {
        servicio.rechazar(token);
        return new RechazoResponse("RECHAZADA");
    }
}
