package ar.turnos.odontologia.pagos.application;

/**
 * Puerto para cancelar un turno con el actor {@code sistema} (design D3 y D4): una seña vencida o una seña que no se
 * pudo guardar. Lo implementa un adaptador que llama a la agenda, así valen sus reglas y su auditoría.
 */
public interface CancelacionDelSistema {

    String ACTOR = "sistema";

    void cancelar(long turnoId);
}
