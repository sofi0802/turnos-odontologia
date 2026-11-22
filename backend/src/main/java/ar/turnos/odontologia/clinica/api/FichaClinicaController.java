package ar.turnos.odontologia.clinica.api;

import ar.turnos.odontologia.agenda.application.MarcarAusenciaCommand;
import ar.turnos.odontologia.agenda.infrastructure.ServicioAgenda;
import ar.turnos.odontologia.clinica.api.ClinicaDtos.AusenciaSolicitud;
import ar.turnos.odontologia.clinica.api.ClinicaDtos.CambioDto;
import ar.turnos.odontologia.clinica.api.ClinicaDtos.FichaRespuesta;
import ar.turnos.odontologia.clinica.api.ClinicaDtos.FichaSolicitud;
import ar.turnos.odontologia.clinica.api.ClinicaDtos.OdontogramaSolicitud;
import ar.turnos.odontologia.clinica.api.ClinicaDtos.RegistroVista;
import ar.turnos.odontologia.clinica.api.ClinicaDtos.TurnoAusente;
import ar.turnos.odontologia.clinica.application.ServicioFicha;
import ar.turnos.odontologia.clinica.application.ServicioOdontograma;
import ar.turnos.odontologia.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import java.time.ZoneId;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ficha clínica del paciente (C-13, design D5): ficha básica, odontograma y ausencias. ADMIN y el odontólogo leen;
 * solo el odontólogo escribe. La recepción queda afuera por la anotación, antes de leer ningún dato de salud.
 */
@RestController
@RequestMapping("/api/v1/pacientes/{id}")
class FichaClinicaController {

    private static final String LECTURA = "hasAnyRole('ADMIN', 'ODONTOLOGO')";
    private static final String ESCRITURA = "hasRole('ODONTOLOGO')";

    private final ServicioFicha fichas;
    private final ServicioOdontograma odontograma;
    private final ServicioAgenda agenda;
    private final ZoneId zona;

    FichaClinicaController(ServicioFicha fichas, ServicioOdontograma odontograma, ServicioAgenda agenda,
                           ZoneId zona) {
        this.fichas = fichas;
        this.odontograma = odontograma;
        this.agenda = agenda;
        this.zona = zona;
    }

    @PreAuthorize(LECTURA)
    @GetMapping("/ficha")
    FichaRespuesta ficha(@PathVariable Long id) {
        return FichaRespuesta.de(fichas.consultar(id), zona);
    }

    @PreAuthorize(ESCRITURA)
    @PutMapping("/ficha")
    FichaRespuesta guardarFicha(@PathVariable Long id, @Valid @RequestBody FichaSolicitud solicitud,
                                @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return FichaRespuesta.de(fichas.guardar(id, solicitud.antecedentes(), solicitud.alergias(),
                solicitud.medicacion(), usuario.getUsername()), zona);
    }

    @PreAuthorize(LECTURA)
    @GetMapping("/odontograma")
    List<RegistroVista> odontograma(@PathVariable Long id) {
        return RegistroVista.de(odontograma.vigente(id), zona);
    }

    @PreAuthorize(LECTURA)
    @GetMapping("/odontograma/historial")
    List<RegistroVista> historial(@PathVariable Long id) {
        return RegistroVista.de(odontograma.historial(id), zona);
    }

    @PreAuthorize(ESCRITURA)
    @PutMapping("/odontograma")
    List<RegistroVista> registrarOdontograma(@PathVariable Long id,
                                             @Valid @RequestBody OdontogramaSolicitud solicitud,
                                             @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return RegistroVista.de(odontograma.registrar(id,
                solicitud.cambios().stream().map(CambioDto::aPedido).toList(), usuario.profesionalId()), zona);
    }

    /** RN-AG-17: el odontólogo del turno marca que el paciente no se presentó (design D6). */
    @PreAuthorize(ESCRITURA)
    @PostMapping("/ausencias")
    TurnoAusente marcarAusencia(@PathVariable Long id, @Valid @RequestBody AusenciaSolicitud solicitud,
                                @AuthenticationPrincipal UsuarioAutenticado usuario) {
        if (usuario.profesionalId() == null) {
            throw new AccessDeniedException("El usuario no está vinculado a un profesional");
        }
        return TurnoAusente.de(agenda.marcarAusencia(
                new MarcarAusenciaCommand(solicitud.turnoId(), id, usuario.profesionalId()), usuario.getUsername()),
                zona);
    }
}
