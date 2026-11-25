package ar.turnos.odontologia.pagos.application;

/**
 * Puerto del aviso al paciente de que se reintegró su seña (RN-SE-11). Lo implementa {@code notificaciones/}: solo dice
 * la fecha del turno, sin importe, y solo con consentimiento de comunicaciones (RN-NO-03).
 */
public interface AvisoReintegroSena {

    void reintegrada(long turnoId, long pacienteId);
}
