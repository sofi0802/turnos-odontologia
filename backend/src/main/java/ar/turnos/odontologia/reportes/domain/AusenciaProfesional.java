package ar.turnos.odontologia.reportes.domain;

/** Ausencias o licencias de un profesional que tocan el período, con los minutos que caen dentro (RN-RE-06). */
public record AusenciaProfesional(long profesionalId, String nombre, String tipo, int cantidad, long minutos) {
}
