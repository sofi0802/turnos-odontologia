package ar.turnos.odontologia.reportes.domain;

/** Los cuatro bloques de un reporte (US-210). */
public record Reporte(Periodo periodo, Long profesionalId, Ocupacion ocupacion, Origen origen, Ausencias ausencias,
                      Cancelaciones cancelaciones) {
}
