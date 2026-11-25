package ar.turnos.odontologia.agenda.listaespera.api;

import ar.turnos.odontologia.agenda.listaespera.application.ListaEsperaRepository.FilaInscripcion;
import ar.turnos.odontologia.agenda.listaespera.domain.CodigoListaEspera;
import ar.turnos.odontologia.agenda.listaespera.domain.Inscripcion;
import ar.turnos.odontologia.agenda.listaespera.domain.ListaEsperaException;
import ar.turnos.odontologia.agenda.listaespera.infrastructure.ServicioListaEspera;
import ar.turnos.odontologia.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lista de espera (C-15): la recepción y el administrador ven y anotan a cualquier paciente; el paciente con sesión
 * solo se anota a sí mismo y ve o da de baja las suyas. El odontólogo no tiene acceso.
 */
@RestController
class ListaEsperaController {

    static final String PERSONAL = "hasAnyRole('ADMIN','RECEPCIONISTA')";

    private final ServicioListaEspera servicio;
    private final ZoneId zona;

    ListaEsperaController(ServicioListaEspera servicio, ZoneId zonaHoraria) {
        this.servicio = servicio;
        this.zona = zonaHoraria;
    }

    record InscripcionPersonalRequest(
            @NotNull(message = "es obligatorio") Long pacienteId,
            @NotNull(message = "es obligatorio") Long prestacionId,
            Long profesionalId,
            @NotNull(message = "es obligatorio") LocalDate desde,
            @NotNull(message = "es obligatorio") LocalDate hasta) {}

    record InscripcionPacienteRequest(
            @NotNull(message = "es obligatorio") Long prestacionId,
            Long profesionalId,
            @NotNull(message = "es obligatorio") LocalDate desde,
            @NotNull(message = "es obligatorio") LocalDate hasta) {}

    // ---------- personal ----------

    @PreAuthorize(PERSONAL)
    @GetMapping("/api/v1/lista-espera")
    List<InscripcionResponse> lista() {
        return servicio.activas().stream().map(fila -> InscripcionResponse.deLaRecepcion(fila, zona)).toList();
    }

    @PreAuthorize(PERSONAL)
    @PostMapping("/api/v1/lista-espera")
    ResponseEntity<InscripcionResponse> anotar(@Valid @RequestBody InscripcionPersonalRequest pedido,
                                               @AuthenticationPrincipal UsuarioAutenticado usuario) {
        Inscripcion creada = servicio.inscribir(pedido.pacienteId(), pedido.prestacionId(), pedido.profesionalId(),
                pedido.desde(), pedido.hasta(), usuario.getUsername());
        return creada(creada, servicio.activas(), fila -> InscripcionResponse.deLaRecepcion(fila, zona));
    }

    @PreAuthorize(PERSONAL)
    @PostMapping("/api/v1/lista-espera/{id}/baja")
    ResponseEntity<Void> bajaDelPersonal(@PathVariable Long id) {
        servicio.darDeBaja(id, null);
        return ResponseEntity.noContent().build();
    }

    // ---------- paciente ----------

    @GetMapping("/api/v1/paciente/lista-espera")
    List<InscripcionResponse> propias(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return servicio.activasDe(usuario.pacienteId()).stream()
                .map(fila -> InscripcionResponse.delPaciente(fila, zona)).toList();
    }

    @PostMapping("/api/v1/paciente/lista-espera")
    ResponseEntity<InscripcionResponse> anotarme(@Valid @RequestBody InscripcionPacienteRequest pedido,
                                                 @AuthenticationPrincipal UsuarioAutenticado usuario) {
        Inscripcion creada = servicio.inscribir(usuario.pacienteId(), pedido.prestacionId(), pedido.profesionalId(),
                pedido.desde(), pedido.hasta(), "paciente-online:" + usuario.usuarioId());
        return creada(creada, servicio.activasDe(usuario.pacienteId()),
                fila -> InscripcionResponse.delPaciente(fila, zona));
    }

    @PostMapping("/api/v1/paciente/lista-espera/{id}/baja")
    ResponseEntity<Void> bajaPropia(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        servicio.darDeBaja(id, usuario.pacienteId());
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<InscripcionResponse> creada(Inscripcion creada, List<FilaInscripcion> activas,
                                                              Function<FilaInscripcion, InscripcionResponse> a) {
        InscripcionResponse respuesta = activas.stream()
                .filter(fila -> fila.id().equals(creada.id()))
                .findFirst()
                .map(a)
                .orElseThrow(() -> new ListaEsperaException(CodigoListaEspera.INSCRIPCION_NO_ACTIVA,
                        "La inscripción ya no está activa"));
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }
}
