package ar.turnos.odontologia.reserva.api;

import ar.turnos.odontologia.agenda.domain.Intervalo;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Un horario libre: solo {@code inicio} y {@code fin}, sin profesional, sillón ni datos de terceros (RN-RO-01). */
record HorarioResponse(OffsetDateTime inicio, OffsetDateTime fin) {

    static HorarioResponse de(Intervalo intervalo, ZoneId zona) {
        return new HorarioResponse(intervalo.inicio().atZone(zona).toOffsetDateTime(),
                intervalo.fin().atZone(zona).toOffsetDateTime());
    }
}
