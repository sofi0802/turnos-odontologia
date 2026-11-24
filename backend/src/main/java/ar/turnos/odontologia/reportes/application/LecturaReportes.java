package ar.turnos.odontologia.reportes.application;

import ar.turnos.odontologia.reportes.domain.DatosReporte;
import ar.turnos.odontologia.reportes.domain.Intervalo;

/** Puerto de lectura de los datos de un reporte (design D2). Solo lee: nunca escribe tablas de otros módulos. */
public interface LecturaReportes {

    /**
     * Turnos con inicio en {@code periodo} (con los datos de su auditoría), ausencias y bloqueos que se superponen con
     * él, y todos los horarios, profesionales y sillones. Con {@code profesionalId}, solo los turnos, ausencias y
     * bloqueos de ese profesional.
     */
    DatosReporte leer(Intervalo periodo, Long profesionalId);

    boolean existeProfesional(long profesionalId);
}
