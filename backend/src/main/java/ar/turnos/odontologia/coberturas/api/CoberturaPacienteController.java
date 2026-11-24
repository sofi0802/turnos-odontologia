package ar.turnos.odontologia.coberturas.api;

import ar.turnos.odontologia.coberturas.application.ConsultaCobertura;
import ar.turnos.odontologia.coberturas.application.Vistas.CoberturaVista;
import ar.turnos.odontologia.shared.security.UsuarioAutenticado;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La cobertura vigente hoy del propio paciente, para "Mi cuenta" (design D5). Se identifica solo por la sesión: este
 * controller no declara ningún {@code @PathVariable} ni {@code @RequestParam}, así un id en el pedido no cambia la
 * respuesta. Solo lectura. {@code SecurityConfig} ya deja {@code /api/v1/paciente/**} al rol PACIENTE.
 */
@RestController
@RequestMapping("/api/v1/paciente/cobertura")
class CoberturaPacienteController {

    private final ConsultaCobertura consulta;

    CoberturaPacienteController(ConsultaCobertura consulta) {
        this.consulta = consulta;
    }

    @GetMapping
    CoberturaVista cobertura(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return consulta.deHoy(usuario.pacienteId());
    }
}
