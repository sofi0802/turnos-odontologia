package ar.turnos.odontologia.reserva.domain;

import java.util.List;

/**
 * Resuelve el empate de la asignación de profesional (RN-RO-05, design D3). En producción elige al azar; en los tests
 * se inyecta uno fijo para que el resultado sea determinista.
 */
@FunctionalInterface
public interface Desempate {

    /** @param empatadosOrdenadosPorId al menos dos ids, de menor a mayor */
    Long elegir(List<Long> empatadosOrdenadosPorId);
}
