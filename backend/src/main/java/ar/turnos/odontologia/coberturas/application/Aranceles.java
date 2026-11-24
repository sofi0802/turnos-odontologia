package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.domain.Precio;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de los precios particulares (C-19, design D6): una fila por prestación, que se reemplaza (upsert). Las
 * prestaciones son de {@code configuracion/}: el adaptador solo las lee.
 */
public interface Aranceles {

    /** Todas las prestaciones por nombre, con su precio o {@code null} si no lo tienen. */
    List<ArancelFila> todos();

    /**
     * Carga o reemplaza el precio de la prestación en una sola sentencia; vacío si la prestación no existe.
     * {@code actualizadoEn} sale del reloj de la aplicación.
     */
    Optional<ArancelFila> guardar(Long prestacionId, Precio precio, Instant actualizadoEn);

    record ArancelFila(Long prestacionId, String prestacionNombre, boolean activa, BigDecimal precio,
                       Instant actualizadoEn) {}
}
