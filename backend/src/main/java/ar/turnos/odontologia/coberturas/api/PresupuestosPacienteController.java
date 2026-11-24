package ar.turnos.odontologia.coberturas.api;

import ar.turnos.odontologia.coberturas.application.ServicioPresupuestos;
import ar.turnos.odontologia.coberturas.application.Vistas.PresupuestoVista;
import ar.turnos.odontologia.shared.security.UsuarioAutenticado;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los presupuestos del propio paciente, para "Mi cuenta" (C-19, design D7). El paciente se identifica solo por la
 * sesión: este controller no declara ningún {@code @PathVariable} de paciente ni {@code @RequestParam}, así un id en el
 * pedido no cambia la respuesta, y busca por {@code id} y paciente de la sesión (de otro paciente es {@code 404}).
 * Solo lectura; el paciente ve sus presupuestos completos, con pieza y cara (son sus datos, decisión 7).
 * {@code SecurityConfig} ya deja {@code /api/v1/paciente/**} al rol PACIENTE y no cambia.
 */
@RestController
@RequestMapping("/api/v1/paciente/presupuestos")
class PresupuestosPacienteController {

    private final ServicioPresupuestos servicio;

    PresupuestosPacienteController(ServicioPresupuestos servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    List<PresupuestoVista> listar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return servicio.propios(usuario.pacienteId());
    }

    @GetMapping("/{presupuestoId}")
    PresupuestoVista obtener(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long presupuestoId) {
        return servicio.propio(usuario.pacienteId(), presupuestoId);
    }
}
