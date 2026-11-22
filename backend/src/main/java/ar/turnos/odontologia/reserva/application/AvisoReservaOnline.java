package ar.turnos.odontologia.reserva.application;

/**
 * Puerto de salida (C-10, design D9): pide el aviso al paciente después de confirmar una operación online. Solo viaja
 * el id del turno: el adaptador decide consentimiento, canal y contenido, y nunca recibe datos de salud (RN-NO-01).
 */
public interface AvisoReservaOnline {

    void reservado(Long turnoId);

    void cancelado(Long turnoId);

    /** @param turnoIdNuevo el turno que reemplaza al original */
    void reprogramado(Long turnoIdNuevo);
}
