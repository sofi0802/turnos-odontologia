package ar.turnos.odontologia.agenda.listaespera.domain;

/** RN-LE-09: una inscripción está activa hasta que se asigna, se da de baja o vence. */
public enum EstadoInscripcion {
    ACTIVA, ASIGNADA, BAJA, VENCIDA
}
