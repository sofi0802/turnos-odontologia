package ar.turnos.odontologia.agenda.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Registro de auditoría de una operación sobre un turno (V4, RN-GL-03). Solo ids, acción, actor y fecha. */
@Entity
@Table(name = "turno_auditoria")
class TurnoAuditoriaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long turnoId;
    private String accion;
    private String actor;
    private Instant fecha;
    private Long turnoRelacionadoId;
    /** C-23: causa de una cancelación con seña pagada (V23). */
    private String causa;

    protected TurnoAuditoriaEntity() {}

    TurnoAuditoriaEntity(Long turnoId, String accion, String actor, Instant fecha, Long turnoRelacionadoId) {
        this.turnoId = turnoId;
        this.accion = accion;
        this.actor = actor;
        this.fecha = fecha;
        this.turnoRelacionadoId = turnoRelacionadoId;
    }

    void conCausa(String causa) {
        this.causa = causa;
    }
}
