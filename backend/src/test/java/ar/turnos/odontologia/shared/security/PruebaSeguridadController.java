package ar.turnos.odontologia.shared.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller solo de test: permite probar {@code @PreAuthorize} por método sin depender de endpoints de
 * otros changes. Vive en src/test, nunca llega al código de producción.
 */
@RestController
@RequestMapping("/api/v1/prueba-seguridad")
class PruebaSeguridadController {

    @GetMapping("/solo-admin")
    @PreAuthorize("hasRole('ADMIN')")
    String soloAdmin() {
        return "ok";
    }

    /** Falla con un error no controlado cuyo mensaje es ficticio (spec seguridad-autorizacion, error interno). */
    @GetMapping("/falla")
    String falla() {
        throw new IllegalStateException("detalle-interno-ficticio");
    }
}
