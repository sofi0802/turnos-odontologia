package ar.turnos.odontologia.coberturas.api;

import ar.turnos.odontologia.coberturas.application.ConsultaCobertura;
import ar.turnos.odontologia.coberturas.application.Vistas.CoberturaVista;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cobertura de un paciente en una fecha, para el personal (design D5). Sin {@code fecha} es hoy en Buenos Aires.
 * Lo pueden consultar ADMIN, RECEPCIONISTA y ODONTOLOGO: lo impone {@code SecurityConfig} para {@code /api/v1/**}.
 */
@RestController
@RequestMapping("/api/v1/pacientes/{pacienteId}/cobertura")
class CoberturaController {

    private final ConsultaCobertura consulta;

    CoberturaController(ConsultaCobertura consulta) {
        this.consulta = consulta;
    }

    @GetMapping
    CoberturaVista cobertura(@PathVariable Long pacienteId,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                             LocalDate fecha) {
        return consulta.de(pacienteId, fecha);
    }
}
