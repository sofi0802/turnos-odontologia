package ar.turnos.odontologia.reserva.infrastructure;

import ar.turnos.odontologia.reserva.domain.Desempate;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Desempate de producción (RN-RO-05, design D3): elige al azar entre los empatados. No es un secreto, así que no hace
 * falta {@code SecureRandom}.
 */
final class DesempateAleatorio implements Desempate {

    private final RandomGenerator azar;

    DesempateAleatorio(RandomGenerator azar) {
        this.azar = Objects.requireNonNull(azar, "azar");
    }

    @Override
    public Long elegir(List<Long> empatadosOrdenadosPorId) {
        return empatadosOrdenadosPorId.get(azar.nextInt(empatadosOrdenadosPorId.size()));
    }
}
