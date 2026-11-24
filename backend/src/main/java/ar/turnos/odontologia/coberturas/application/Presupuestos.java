package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.domain.EstadoPresupuesto;
import ar.turnos.odontologia.coberturas.domain.Presupuesto;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Puerto de escritura de los presupuestos (C-19, design D4 y D5): se guardan una sola vez y después solo cambia el
 * estado, con un {@code UPDATE} condicional que serializa los pedidos que llegan a la vez.
 */
public interface Presupuestos {

    /** Guarda el presupuesto nuevo con sus ítems y devuelve su id. */
    Long guardar(Presupuesto presupuesto);

    /**
     * Pasa a {@code destino} un presupuesto {@code PENDIENTE} del paciente; aceptar exige además que no esté vencido
     * en {@code hoy}. Devuelve las filas cambiadas: 0 si no existe, no es del paciente, ya no está pendiente o venció.
     */
    int cambiarEstado(Long pacienteId, Long presupuestoId, EstadoPresupuesto destino, LocalDate hoy);

    /** El estado guardado y el vencimiento de un presupuesto del paciente, para explicar por qué no cambió. */
    Optional<EstadoGuardado> estado(Long pacienteId, Long presupuestoId);

    record EstadoGuardado(EstadoPresupuesto estado, LocalDate venceEl) {}
}
