package ar.turnos.odontologia.clinica.api;

import ar.turnos.odontologia.clinica.api.ConsentimientoDtos.ConsentimientoResumen;
import ar.turnos.odontologia.clinica.api.ConsentimientoDtos.ConsentimientoVista;
import ar.turnos.odontologia.clinica.api.ConsentimientoDtos.FirmaSolicitud;
import ar.turnos.odontologia.clinica.api.ConsentimientoDtos.RevocacionSolicitud;
import ar.turnos.odontologia.clinica.api.ConsentimientoDtos.VerificacionVista;
import ar.turnos.odontologia.clinica.application.ServicioConsentimientos;
import ar.turnos.odontologia.clinica.domain.EvidenciaFirma;
import ar.turnos.odontologia.shared.security.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletRequest;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpHeaders;
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
 * Consentimientos informados del paciente (C-27, design D2, D7 y D8). Son parte de la historia clínica (RN-CL-01): el
 * odontólogo firma, ve, verifica y revoca; el {@code ADMIN} solo ve y verifica; la recepción y el paciente quedan afuera
 * por la anotación, antes de leer ningún dato. No hay {@code PUT}, {@code PATCH} ni {@code DELETE}: el documento firmado
 * no cambia. La firma es electrónica (Ley 25.506, art. 5), no una firma digital.
 */
@RestController
@RequestMapping("/api/v1/pacientes/{id}/consentimientos")
class ConsentimientoController {

    private static final String LECTURA = "hasAnyRole('ADMIN', 'ODONTOLOGO')";
    private static final String ESCRITURA = "hasRole('ODONTOLOGO')";

    private final ServicioConsentimientos consentimientos;
    private final ZoneId zona;

    ConsentimientoController(ServicioConsentimientos consentimientos, ZoneId zona) {
        this.consentimientos = consentimientos;
        this.zona = zona;
    }

    /**
     * La evidencia sale del pedido: {@code getRemoteAddr()} (nunca {@code X-Forwarded-For}, que inventa cualquier
     * cliente) y el {@code User-Agent} (design D7).
     */
    @PreAuthorize(ESCRITURA)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ConsentimientoVista firmar(@PathVariable Long id, @RequestBody(required = false) FirmaSolicitud solicitud,
                               @AuthenticationPrincipal UsuarioAutenticado usuario, HttpServletRequest pedido) {
        FirmaSolicitud firma = solicitud == null ? new FirmaSolicitud(null, null, null, null, null) : solicitud;
        EvidenciaFirma evidencia = EvidenciaFirma.de(pedido.getRemoteAddr(), pedido.getHeader(HttpHeaders.USER_AGENT));
        return ConsentimientoVista.de(consentimientos.firmar(id, usuario.profesionalId(), firma.plantillaId(),
                firma.versionNumero(), firma.firmanteNombre(), firma.firmanteDni(), firma.aceptado(), evidencia), zona);
    }

    @PreAuthorize(LECTURA)
    @GetMapping
    List<ConsentimientoResumen> listar(@PathVariable Long id) {
        return ConsentimientoResumen.de(consentimientos.listar(id), zona);
    }

    @PreAuthorize(LECTURA)
    @GetMapping("/{consentimientoId}")
    ConsentimientoVista ver(@PathVariable Long id, @PathVariable Long consentimientoId) {
        return ConsentimientoVista.de(consentimientos.ver(id, consentimientoId), zona);
    }

    @PreAuthorize(LECTURA)
    @GetMapping("/{consentimientoId}/verificacion")
    VerificacionVista verificar(@PathVariable Long id, @PathVariable Long consentimientoId) {
        return VerificacionVista.de(consentimientos.verificar(id, consentimientoId), zona);
    }

    @PreAuthorize(ESCRITURA)
    @PostMapping("/{consentimientoId}/revocacion")
    ConsentimientoVista revocar(@PathVariable Long id, @PathVariable Long consentimientoId,
                                @RequestBody(required = false) RevocacionSolicitud solicitud,
                                @AuthenticationPrincipal UsuarioAutenticado usuario) {
        String motivo = solicitud == null ? null : solicitud.motivo();
        return ConsentimientoVista.de(consentimientos.revocar(id, consentimientoId, usuario.profesionalId(), motivo),
                zona);
    }
}
