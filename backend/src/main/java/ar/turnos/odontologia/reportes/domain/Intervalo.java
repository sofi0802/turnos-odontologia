package ar.turnos.odontologia.reportes.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Intervalo semiabierto {@code [inicio, fin)}. */
public record Intervalo(Instant inicio, Instant fin) {

    public Intervalo {
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(fin, "fin");
        if (!fin.isAfter(inicio)) {
            throw new IllegalArgumentException("El fin tiene que ser posterior al inicio");
        }
    }

    /** La parte de este intervalo que cae dentro de {@code limite}, si hay alguna. */
    public Optional<Intervalo> recortar(Intervalo limite) {
        Instant desde = inicio.isAfter(limite.inicio()) ? inicio : limite.inicio();
        Instant hasta = fin.isBefore(limite.fin()) ? fin : limite.fin();
        return hasta.isAfter(desde) ? Optional.of(new Intervalo(desde, hasta)) : Optional.empty();
    }

    public long minutos() {
        return java.time.Duration.between(inicio, fin).toMinutes();
    }
}
