package ar.turnos.odontologia.clinica.api;

import ar.turnos.odontologia.clinica.api.PeriodontogramaDtos.PeriodontogramaSolicitud;
import ar.turnos.odontologia.clinica.api.PeriodontogramaDtos.ResumenVista;
import ar.turnos.odontologia.clinica.api.PeriodontogramaDtos.VersionVista;
import ar.turnos.odontologia.clinica.application.ServicioPeriodontograma;
import ar.turnos.odontologia.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Periodontograma del paciente (C-25, design D1). Los mismos permisos que la ficha clínica (RN-CL-01): ADMIN y el
 * odontólogo leen, solo el odontólogo registra. La recepción queda afuera por la anotación, antes de leer ningún dato
 * de salud. Los errores los escribe {@code ClinicaExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/pacientes/{id}/periodontograma")
class PeriodontogramaController {

    private static final String LECTURA = "hasAnyRole('ADMIN', 'ODONTOLOGO')";
    private static final String ESCRITURA = "hasRole('ODONTOLOGO')";

    private final ServicioPeriodontograma periodontograma;
    private final ZoneId zona;

    PeriodontogramaController(ServicioPeriodontograma periodontograma, ZoneId zona) {
        this.periodontograma = periodontograma;
        this.zona = zona;
    }

    @PreAuthorize(ESCRITURA)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    VersionVista registrar(@PathVariable Long id, @Valid @RequestBody PeriodontogramaSolicitud solicitud,
                           @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return VersionVista.de(periodontograma.registrar(id, solicitud.aPedido(), usuario.profesionalId()), zona);
    }

    @PreAuthorize(LECTURA)
    @GetMapping
    List<ResumenVista> versiones(@PathVariable Long id) {
        return ResumenVista.de(periodontograma.versiones(id), zona);
    }

    @PreAuthorize(LECTURA)
    @GetMapping("/{versionId}")
    VersionVista version(@PathVariable Long id, @PathVariable Long versionId) {
        return VersionVista.de(periodontograma.version(id, versionId), zona);
    }
}
