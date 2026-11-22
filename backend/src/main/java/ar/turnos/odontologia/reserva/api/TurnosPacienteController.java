package ar.turnos.odontologia.reserva.api;

import ar.turnos.odontologia.reserva.application.ConsultarMisTurnos;
import ar.turnos.odontologia.shared.security.UsuarioAutenticado;
import java.time.ZoneId;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Mis turnos": solo los del paciente de la sesión. Cualquier id de paciente del pedido se ignora. */
@RestController
@RequestMapping("/api/v1/paciente")
class TurnosPacienteController {

    private final ConsultarMisTurnos misTurnos;
    private final ZoneId zona;

    TurnosPacienteController(ConsultarMisTurnos misTurnos, ZoneId zonaHoraria) {
        this.misTurnos = misTurnos;
        this.zona = zonaHoraria;
    }

    @GetMapping("/turnos")
    List<TurnoPacienteResponse> misTurnos(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return misTurnos.ejecutar(usuario.pacienteId()).stream()
                .map(mio -> TurnoPacienteResponse.de(mio.turno(), zona, mio.modificableOnline()))
                .toList();
    }
}
