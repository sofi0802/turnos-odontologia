package ar.turnos.odontologia.reportes.api;

import ar.turnos.odontologia.reportes.application.ConsultaReportes;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reportes del consultorio (US-210): solo para la administración y la recepción (RN-RE-01, design D8). */
@RestController
@RequestMapping("/api/v1/reportes")
@PreAuthorize("hasAnyRole('ADMIN','RECEPCIONISTA')")
class ReportesController {

    private final ConsultaReportes consulta;

    ReportesController(ConsultaReportes consulta) {
        this.consulta = consulta;
    }

    @GetMapping
    ReporteRespuesta consultar(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                               @RequestParam(required = false) Long profesionalId) {
        return ReporteRespuesta.de(consulta.consultar(desde, hasta, profesionalId));
    }
}
