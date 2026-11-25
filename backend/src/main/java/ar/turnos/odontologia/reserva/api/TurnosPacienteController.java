package ar.turnos.odontologia.reserva.api;

import ar.turnos.odontologia.reserva.application.SenasDeReserva;
import org.springframework.beans.factory.ObjectProvider;
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
    private final SenasDeReserva senas;

    TurnosPacienteController(ConsultarMisTurnos misTurnos, ZoneId zonaHoraria, ObjectProvider<SenasDeReserva> senas) {
        this.senas = senas.getIfAvailable(() -> SenasDeReserva.NINGUNA);
        this.misTurnos = misTurnos;
        this.zona = zonaHoraria;
    }

    @GetMapping("/turnos")
    List<TurnoPacienteResponse> misTurnos(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        var mios = misTurnos.ejecutar(usuario.pacienteId());
        // C-23: la seña de cada turno, en una sola consulta
        var senasDeLosTurnos = senas.deTurnos(mios.stream().map(mio -> mio.turno().turnoId()).toList());
        return mios.stream()
                .map(mio -> TurnoPacienteResponse.de(mio.turno(), zona, mio.modificableOnline(), senasDeLosTurnos))
                .toList();
    }
}
