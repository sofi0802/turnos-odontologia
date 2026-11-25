package ar.turnos.odontologia.agenda.listaespera.domain;

import java.time.Duration;
import java.time.Instant;

/** Plazo para aceptar una oferta (RN-LE-05): de 1 a 24 horas enteras, 2 por defecto. */
public record PlazoOferta(int horas) {

    public static final int MINIMO = 1;
    public static final int MAXIMO = 24;
    public static final PlazoOferta PREDETERMINADO = new PlazoOferta(2);

    public PlazoOferta {
        if (horas < MINIMO || horas > MAXIMO) {
            throw new ListaEsperaException(CodigoListaEspera.PLAZO_INVALIDO,
                    "El plazo tiene que ser de 1 a 24 horas");
        }
    }

    public static PlazoOferta deHoras(int horas) {
        return new PlazoOferta(horas);
    }

    /** La oferta vence al cumplirse el plazo o al llegar el inicio del horario, lo que pase primero. */
    public Instant vencimiento(Instant ahora, Instant inicioDelHorario) {
        Instant conPlazo = ahora.plus(Duration.ofHours(horas));
        return conPlazo.isBefore(inicioDelHorario) ? conPlazo : inicioDelHorario;
    }
}
