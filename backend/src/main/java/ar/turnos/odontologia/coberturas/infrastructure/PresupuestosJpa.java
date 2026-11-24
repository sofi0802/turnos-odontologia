package ar.turnos.odontologia.coberturas.infrastructure;

import ar.turnos.odontologia.coberturas.application.Presupuestos;
import ar.turnos.odontologia.coberturas.domain.EstadoPresupuesto;
import ar.turnos.odontologia.coberturas.domain.Presupuesto;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de {@link Presupuestos}: el presupuesto se inserta con JPA (ítems en cascada) y el estado cambia con un
 * {@code UPDATE} condicional por JDBC (design D5). El trigger {@code presupuesto_solo_estado} de V13 repite en la base
 * que solo el estado puede cambiar y solo desde {@code PENDIENTE}.
 */
@Repository
class PresupuestosJpa implements Presupuestos {

    private final PresupuestoJpaRepository repositorio;
    private final JdbcTemplate jdbc;

    PresupuestosJpa(PresupuestoJpaRepository repositorio, JdbcTemplate jdbc) {
        this.repositorio = repositorio;
        this.jdbc = jdbc;
    }

    @Override
    public Long guardar(Presupuesto presupuesto) {
        return repositorio.saveAndFlush(new PresupuestoEntity(presupuesto)).id();
    }

    @Override
    public int cambiarEstado(Long pacienteId, Long presupuestoId, EstadoPresupuesto destino, LocalDate hoy) {
        return jdbc.update("UPDATE presupuesto SET estado = ? WHERE id = ? AND paciente_id = ? "
                + "AND estado = 'PENDIENTE' AND (? = 'RECHAZADO' OR vence_el >= ?)",
                destino.name(), presupuestoId, pacienteId, destino.name(), Date.valueOf(hoy));
    }

    @Override
    public Optional<EstadoGuardado> estado(Long pacienteId, Long presupuestoId) {
        return jdbc.query("SELECT estado, vence_el FROM presupuesto WHERE id = ? AND paciente_id = ?",
                (rs, fila) -> new EstadoGuardado(EstadoPresupuesto.valueOf(rs.getString("estado")),
                        rs.getDate("vence_el").toLocalDate()), presupuestoId, pacienteId).stream().findFirst();
    }
}
