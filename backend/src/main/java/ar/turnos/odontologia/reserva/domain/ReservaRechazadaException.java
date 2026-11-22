package ar.turnos.odontologia.reserva.domain;

import ar.turnos.odontologia.agenda.domain.CodigoRechazo;
import ar.turnos.odontologia.agenda.domain.Intervalo;
import java.util.List;
import java.util.Objects;

/**
 * Rechazo de negocio de la reserva online. {@code codigo} es el código estable que ve el paciente: uno de
 * {@link CodigoReserva} o el nombre de un {@link CodigoRechazo} que se puede mostrar tal cual. Lleva, si corresponde,
 * hasta 3 horarios alternativos (design D7).
 */
public class ReservaRechazadaException extends RuntimeException {

    private final String codigo;
    private final List<Intervalo> alternativas;

    private ReservaRechazadaException(String codigo, String mensaje, List<Intervalo> alternativas) {
        super(mensaje);
        this.codigo = Objects.requireNonNull(codigo, "codigo");
        this.alternativas = List.copyOf(alternativas);
    }

    public static ReservaRechazadaException de(CodigoReserva codigo, String mensaje) {
        return new ReservaRechazadaException(codigo.name(), mensaje, List.of());
    }

    public static ReservaRechazadaException de(CodigoRechazo codigo, String mensaje) {
        return new ReservaRechazadaException(codigo.name(), mensaje, List.of());
    }

    public static ReservaRechazadaException sinDisponibilidad(List<Intervalo> alternativas) {
        return new ReservaRechazadaException(CodigoReserva.SIN_DISPONIBILIDAD.name(),
                "El horario elegido ya no está disponible", alternativas);
    }

    public String codigo() {
        return codigo;
    }

    public List<Intervalo> alternativas() {
        return alternativas;
    }
}
