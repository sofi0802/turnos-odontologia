package ar.turnos.odontologia.coberturas.infrastructure;

import ar.turnos.odontologia.coberturas.application.LecturaPresupuestos;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de {@link LecturaPresupuestos}: lee solo las columnas de la foto de {@code presupuesto} y
 * {@code presupuesto_item}, más el nombre del profesional. Nunca une con el arancel, la cobertura, el nomenclador ni la
 * afiliación, así un cambio posterior no altera lo que muestra un presupuesto emitido (RN-PR-04).
 */
@Repository
class LecturaPresupuestosJdbc implements LecturaPresupuestos {

    private static final String CABECERA = """
            SELECT p.id, p.fecha, p.vence_el, p.estado, p.profesional_id, pr.nombre || ' ' || pr.apellido AS profesional,
                   p.afiliacion_id, p.obra_social_nombre, p.plan_nombre, p.numero_afiliado, p.total_precio,
                   p.total_cobertura, p.total_copago, p.creado_en
            FROM presupuesto p JOIN profesional pr ON pr.id = p.profesional_id
            """;

    private final JdbcTemplate jdbc;

    LecturaPresupuestosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PresupuestoFila> detalle(Long pacienteId, Long presupuestoId) {
        List<PresupuestoFila> filas = cargar(CABECERA + "WHERE p.paciente_id = ? AND p.id = ?", pacienteId,
                presupuestoId);
        return filas.stream().findFirst();
    }

    @Override
    public List<PresupuestoFila> delPaciente(Long pacienteId) {
        return cargar(CABECERA + "WHERE p.paciente_id = ? ORDER BY p.fecha DESC, p.id DESC", pacienteId);
    }

    private List<PresupuestoFila> cargar(String sql, Object... argumentos) {
        List<Cabecera> cabeceras = jdbc.query(sql, LecturaPresupuestosJdbc::cabecera, argumentos);
        if (cabeceras.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ItemFila>> itemsPorPresupuesto = new LinkedHashMap<>();
        String marcas = String.join(",", Collections.nCopies(cabeceras.size(), "?"));
        jdbc.query("SELECT id, presupuesto_id, prestacion_id, prestacion_nombre, codigo_nomenclador, pieza, cara, "
                + "cantidad, precio_unitario, porcentaje_cobertura, importe, cobertura, copago "
                + "FROM presupuesto_item WHERE presupuesto_id IN (" + marcas + ") ORDER BY id",
                rs -> {
                    int pieza = rs.getInt("pieza");
                    Integer piezaONula = rs.wasNull() ? null : pieza;
                    itemsPorPresupuesto.computeIfAbsent(rs.getLong("presupuesto_id"), k -> new ArrayList<>())
                            .add(new ItemFila(rs.getLong("id"), rs.getLong("prestacion_id"),
                                    rs.getString("prestacion_nombre"), rs.getString("codigo_nomenclador"),
                                    piezaONula, rs.getString("cara"), rs.getInt("cantidad"),
                                    rs.getBigDecimal("precio_unitario"), rs.getInt("porcentaje_cobertura"),
                                    rs.getBigDecimal("importe"), rs.getBigDecimal("cobertura"),
                                    rs.getBigDecimal("copago")));
                }, cabeceras.stream().map(c -> c.id).toArray());
        List<PresupuestoFila> filas = new ArrayList<>();
        for (Cabecera c : cabeceras) {
            filas.add(new PresupuestoFila(c.id, c.fecha, c.venceEl, c.estado, c.profesionalId, c.profesional,
                    c.afiliacionId, c.obraSocial, c.plan, c.numeroAfiliado, c.totalPrecio, c.totalCobertura,
                    c.totalCopago, c.creadoEn, itemsPorPresupuesto.getOrDefault(c.id, List.of())));
        }
        return filas;
    }

    private static Cabecera cabecera(ResultSet rs, int numero) throws SQLException {
        long afiliacion = rs.getLong("afiliacion_id");
        Long afiliacionId = rs.wasNull() ? null : afiliacion;
        return new Cabecera(rs.getLong("id"), rs.getDate("fecha").toLocalDate(), rs.getDate("vence_el").toLocalDate(),
                rs.getString("estado"), rs.getLong("profesional_id"), rs.getString("profesional"), afiliacionId,
                rs.getString("obra_social_nombre"), rs.getString("plan_nombre"), rs.getString("numero_afiliado"),
                rs.getBigDecimal("total_precio"), rs.getBigDecimal("total_cobertura"),
                rs.getBigDecimal("total_copago"), rs.getObject("creado_en", OffsetDateTime.class).toInstant());
    }

    private record Cabecera(Long id, java.time.LocalDate fecha, java.time.LocalDate venceEl, String estado,
                            Long profesionalId, String profesional, Long afiliacionId, String obraSocial,
                            String plan, String numeroAfiliado, java.math.BigDecimal totalPrecio,
                            java.math.BigDecimal totalCobertura, java.math.BigDecimal totalCopago,
                            java.time.Instant creadoEn) {}
}
