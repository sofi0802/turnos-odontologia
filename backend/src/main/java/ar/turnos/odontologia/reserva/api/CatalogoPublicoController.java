package ar.turnos.odontologia.reserva.api;

import ar.turnos.odontologia.reserva.application.ConsultarCatalogo;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo público sin sesión: solo lo que el paciente necesita para elegir. Sin matrícula ni otros datos. */
@RestController
@RequestMapping("/api/v1/public")
class CatalogoPublicoController {

    private final ConsultarCatalogo catalogo;

    CatalogoPublicoController(ConsultarCatalogo catalogo) {
        this.catalogo = catalogo;
    }

    record PrestacionPublica(Long id, String nombre, long duracionMinutos) {}

    record ProfesionalPublico(Long id, String nombre) {}

    @GetMapping("/prestaciones")
    List<PrestacionPublica> prestaciones() {
        return catalogo.prestaciones().stream()
                .map(p -> new PrestacionPublica(p.id(), p.nombre(), p.duracion().toMinutes()))
                .toList();
    }

    @GetMapping("/profesionales")
    List<ProfesionalPublico> profesionales(@RequestParam Long prestacionId) {
        return catalogo.profesionales(prestacionId).stream()
                .map(p -> new ProfesionalPublico(p.id(), p.nombre()))
                .toList();
    }
}
