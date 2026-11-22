package ar.turnos.odontologia.reserva.application;

import ar.turnos.odontologia.agenda.domain.ReferenciaInexistenteException;
import ar.turnos.odontologia.reserva.application.CatalogoReserva.Prestacion;
import ar.turnos.odontologia.reserva.application.CatalogoReserva.Profesional;
import java.util.List;
import java.util.Objects;

/** Caso de uso: el catálogo público para elegir qué reservar (propuesta aceptada, design "Resolución de decisiones"). */
public class ConsultarCatalogo {

    private final CatalogoReserva catalogo;

    public ConsultarCatalogo(CatalogoReserva catalogo) {
        this.catalogo = Objects.requireNonNull(catalogo, "catalogo");
    }

    public List<Prestacion> prestaciones() {
        return catalogo.prestacionesActivas();
    }

    /**
     * Profesionales activos habilitados para la prestación. Una prestación inactiva no tiene profesionales para
     * ofrecer.
     *
     * @throws ReferenciaInexistenteException si la prestación no existe
     */
    public List<Profesional> profesionales(Long prestacionId) {
        Prestacion prestacion = catalogo.prestacion(prestacionId)
                .orElseThrow(() -> new ReferenciaInexistenteException("prestacionId"));
        return prestacion.activa() ? catalogo.profesionalesHabilitados(prestacionId) : List.of();
    }
}
