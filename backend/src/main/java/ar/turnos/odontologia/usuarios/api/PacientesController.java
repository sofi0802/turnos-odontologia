package ar.turnos.odontologia.usuarios.api;

import ar.turnos.odontologia.usuarios.application.BuscarPacientes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Búsqueda de pacientes para el personal (ADMIN, RECEPCIONISTA y ODONTOLOGO, por la regla general de
 * {@code SecurityConfig}). El término buscado es un dato personal: este controller no lo registra en ningún log.
 */
@RestController
@RequestMapping("/api/v1/pacientes")
class PacientesController {

    private final BuscarPacientes buscarPacientes;

    PacientesController(BuscarPacientes buscarPacientes) {
        this.buscarPacientes = buscarPacientes;
    }

    /** Una {@code q} ausente la rechaza el dominio como cualquier término inválido (422 DATOS_INVALIDOS). */
    @GetMapping
    List<PacienteResumenResponse> buscar(@RequestParam(required = false) String q) {
        return buscarPacientes.buscar(q).stream().map(PacienteResumenResponse::de).toList();
    }
}
