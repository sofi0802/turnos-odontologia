package ar.turnos.odontologia.usuarios.api;

import ar.turnos.odontologia.usuarios.application.RegistrarPaciente;
import ar.turnos.odontologia.usuarios.domain.Usuario;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Registro público del paciente. No inicia la sesión: el paciente entra con el login. */
@RestController
@RequestMapping("/api/v1/public/pacientes")
class RegistroPacienteController {

    private final RegistrarPaciente registrarPaciente;

    RegistroPacienteController(RegistrarPaciente registrarPaciente) {
        this.registrarPaciente = registrarPaciente;
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    RegistroPacienteResponse registrar(@Valid @RequestBody RegistroPacienteRequest pedido) {
        Usuario cuenta = registrarPaciente.registrar(new RegistrarPaciente.Datos(pedido.nombre(),
                pedido.apellido(), pedido.dni(), pedido.email(), pedido.telefono(), pedido.password()),
                pedido.aceptaLosMensajes());
        return new RegistroPacienteResponse(cuenta.username().valor(), cuenta.rol());
    }
}
