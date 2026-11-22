package ar.turnos.odontologia.reserva.application;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de lectura (design D1, D10): lo que la reserva necesita saber de prestaciones, profesionales y sillones.
 * Devuelve datos mínimos, nunca entidades de persistencia.
 */
public interface CatalogoReserva {

    Optional<Prestacion> prestacion(Long id);

    Optional<Profesional> profesional(Long id);

    /** Prestaciones activas, por nombre. */
    List<Prestacion> prestacionesActivas();

    /** Profesionales activos habilitados para la prestación (RN-AG-15), por id. */
    List<Profesional> profesionalesHabilitados(Long prestacionId);

    /** Si el profesional está habilitado para la prestación según sus especialidades, esté o no activo. */
    boolean estaHabilitado(Long profesionalId, Long prestacionId);

    /** Ids de los sillones activos, de menor a mayor. */
    List<Long> sillonesActivos();

    record Prestacion(Long id, String nombre, Duration duracion, boolean activa) {}

    /** {@code nombre} es el nombre completo para mostrar; {@code sillonHabitualId} puede ser nulo. */
    record Profesional(Long id, String nombre, boolean activo, Long sillonHabitualId) {}
}
