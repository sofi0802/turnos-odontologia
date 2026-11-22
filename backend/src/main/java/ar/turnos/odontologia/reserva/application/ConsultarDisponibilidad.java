package ar.turnos.odontologia.reserva.application;

import ar.turnos.odontologia.agenda.domain.Intervalo;
import ar.turnos.odontologia.agenda.domain.ReferenciaInexistenteException;
import ar.turnos.odontologia.reserva.application.CatalogoReserva.Prestacion;
import ar.turnos.odontologia.reserva.application.CatalogoReserva.Profesional;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Caso de uso: horarios libres de un día para una prestación, con un profesional elegido o sin preferencia (US-020,
 * US-023). Una combinación que no se ofrece (prestación inactiva, profesional inactivo o no habilitado) da la lista
 * vacía y no revela el motivo.
 */
public class ConsultarDisponibilidad {

    private final CatalogoReserva catalogo;
    private final BuscadorDeHuecos buscador;

    public ConsultarDisponibilidad(CatalogoReserva catalogo, BuscadorDeHuecos buscador) {
        this.catalogo = Objects.requireNonNull(catalogo, "catalogo");
        this.buscador = Objects.requireNonNull(buscador, "buscador");
    }

    /**
     * @param profesionalId el profesional elegido, o {@code null} para "sin preferencia"
     * @throws ReferenciaInexistenteException si la prestación o el profesional no existen
     */
    public List<Intervalo> ejecutar(Long prestacionId, LocalDate fecha, Long profesionalId) {
        Prestacion prestacion = catalogo.prestacion(prestacionId)
                .orElseThrow(() -> new ReferenciaInexistenteException("prestacionId"));
        Profesional elegido = profesionalId == null ? null : catalogo.profesional(profesionalId)
                .orElseThrow(() -> new ReferenciaInexistenteException("profesionalId"));
        if (!prestacion.activa()) {
            return List.of();
        }
        List<Profesional> candidatos;
        if (elegido == null) {
            candidatos = catalogo.profesionalesHabilitados(prestacionId);
        } else if (elegido.activo() && catalogo.estaHabilitado(elegido.id(), prestacionId)) {
            candidatos = List.of(elegido);
        } else {
            return List.of();
        }
        return buscador.horariosLibres(fecha, prestacion, candidatos, null);
    }
}
