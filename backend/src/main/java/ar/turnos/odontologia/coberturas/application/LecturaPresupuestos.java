package ar.turnos.odontologia.coberturas.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de lectura de los presupuestos (C-19, design D7): solo las columnas de la foto, nunca los catálogos vivos,
 * así lo que se muestra es lo que se emitió aunque después cambien precios, coberturas o la afiliación.
 */
public interface LecturaPresupuestos {

    /** El presupuesto buscado junto con su paciente: de otro paciente no existe. */
    Optional<PresupuestoFila> detalle(Long pacienteId, Long presupuestoId);

    /** Los del paciente, del más reciente al más antiguo. */
    List<PresupuestoFila> delPaciente(Long pacienteId);

    record PresupuestoFila(Long id, LocalDate fecha, LocalDate venceEl, String estado, Long profesionalId,
                           String profesional, Long afiliacionId, String obraSocial, String plan,
                           String numeroAfiliado, BigDecimal totalPrecio, BigDecimal totalCobertura,
                           BigDecimal totalCopago, Instant creadoEn, List<ItemFila> items) {}

    record ItemFila(Long id, Long prestacionId, String prestacion, String codigoNomenclador, Integer pieza, String cara,
                    int cantidad, BigDecimal precioUnitario, int porcentajeCobertura, BigDecimal importe,
                    BigDecimal cobertura, BigDecimal copago) {}
}
