package ar.turnos.odontologia.reserva.api;

import ar.turnos.odontologia.reserva.application.ConsultarDisponibilidad;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Disponibilidad pública, sin sesión (design D1): los horarios libres de un día para una prestación. */
@RestController
@RequestMapping("/api/v1/public")
class DisponibilidadPublicaController {

    private final ConsultarDisponibilidad disponibilidad;
    private final ZoneId zona;

    DisponibilidadPublicaController(ConsultarDisponibilidad disponibilidad, ZoneId zonaHoraria) {
        this.disponibilidad = disponibilidad;
        this.zona = zonaHoraria;
    }

    @GetMapping("/disponibilidad")
    List<HorarioResponse> disponibilidad(
            @RequestParam Long prestacionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Long profesionalId) {
        return disponibilidad.ejecutar(prestacionId, fecha, profesionalId).stream()
                .map(horario -> HorarioResponse.de(horario, zona))
                .toList();
    }
}
