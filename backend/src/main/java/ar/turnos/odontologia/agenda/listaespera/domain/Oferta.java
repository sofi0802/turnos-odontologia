package ar.turnos.odontologia.agenda.listaespera.domain;

import java.time.Instant;
import java.util.Objects;

/** Un horario liberado ofrecido a una inscripción (RN-LE-04 a RN-LE-07). */
public record Oferta(Long id, Long inscripcionId, Long turnoLiberadoId, Long profesionalId, Instant inicio,
                     EstadoOferta estado, Instant venceEn) {

    public Oferta {
        Objects.requireNonNull(inscripcionId, "inscripcionId");
        Objects.requireNonNull(turnoLiberadoId, "turnoLiberadoId");
        Objects.requireNonNull(profesionalId, "profesionalId");
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(venceEn, "venceEn");
    }

    public Oferta conEstado(EstadoOferta nuevo) {
        return new Oferta(id, inscripcionId, turnoLiberadoId, profesionalId, inicio, nuevo, venceEn);
    }

    /**
     * @throws ListaEsperaException {@code OFERTA_VENCIDA} si venció (aunque la tarea todavía no la marcó) y
     *     {@code OFERTA_YA_RESPONDIDA} si ya se aceptó, se rechazó o el horario dejó de estar disponible
     */
    public void verificarRespondible(Instant ahora) {
        if (estado == EstadoOferta.VENCIDA || (estado == EstadoOferta.PENDIENTE && !ahora.isBefore(venceEn))) {
            throw new ListaEsperaException(CodigoListaEspera.OFERTA_VENCIDA, "La oferta venció");
        }
        if (estado != EstadoOferta.PENDIENTE) {
            throw new ListaEsperaException(CodigoListaEspera.OFERTA_YA_RESPONDIDA, "La oferta ya se respondió");
        }
    }
}
