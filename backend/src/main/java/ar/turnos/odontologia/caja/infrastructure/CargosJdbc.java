package ar.turnos.odontologia.caja.infrastructure;

import ar.turnos.odontologia.caja.application.Cargos;
import ar.turnos.odontologia.caja.application.Vistas.CargoVista;
import ar.turnos.odontologia.caja.application.Vistas.PendienteVista;
import ar.turnos.odontologia.caja.application.Vistas.SinPrecioVista;
import ar.turnos.odontologia.caja.domain.CargoCalculado;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Cargos por JDBC (design D2 y D4). Lee {@code turno}, {@code paciente} y {@code prestacion} sin escribirlas. */
@Repository
class CargosJdbc implements Cargos {

    private static final String PAGADO = "COALESCE((SELECT SUM(cb.importe) FROM cobro cb "
            + "WHERE cb.cargo_id = c.id AND NOT cb.anulado), 0)";

    private final NamedParameterJdbcTemplate jdbc;
    private final ZoneId zona;

    CargosJdbc(NamedParameterJdbcTemplate jdbc, ZoneId zonaHoraria) {
        this.jdbc = jdbc;
        this.zona = zonaHoraria;
    }

    @Override
    public boolean insertarSiNoExiste(long turnoId, long pacienteId, long prestacionId, LocalDate fecha,
                                      CargoCalculado cargo, Instant ahora) {
        int filas = jdbc.update("INSERT INTO cargo(paciente_id, turno_id, prestacion_id, fecha, afiliacion_id, precio, "
                        + "porcentaje_cobertura, cobertura, copago, creado_en) VALUES (:paciente, :turno, :prestacion, "
                        + ":fecha, :afiliacion, :precio, :porcentaje, :cobertura, :copago, :ahora) "
                        + "ON CONFLICT (turno_id) DO NOTHING",
                new MapSqlParameterSource().addValue("paciente", pacienteId).addValue("turno", turnoId)
                        .addValue("prestacion", prestacionId).addValue("fecha", fecha)
                        .addValue("afiliacion", cargo.afiliacionId()).addValue("precio", cargo.precio())
                        .addValue("porcentaje", cargo.porcentajeCobertura()).addValue("cobertura", cargo.cobertura())
                        .addValue("copago", cargo.copago()).addValue("ahora", Fechas.aOffset(ahora)));
        return filas > 0;
    }

    @Override
    public Optional<CargoBloqueado> bloquear(long cargoId) {
        // FOR UPDATE del cargo serializa los cobros del mismo cargo; lo pagado se suma después del bloqueo.
        return jdbc.query("SELECT c.id, c.copago FROM cargo c WHERE c.id = :id FOR UPDATE",
                        new MapSqlParameterSource("id", cargoId), (rs, n) -> rs.getLong("id"))
                .stream().findFirst()
                .map(id -> jdbc.queryForObject("SELECT c.id, c.copago, " + PAGADO + " AS pagado FROM cargo c "
                                + "WHERE c.id = :id", new MapSqlParameterSource("id", id),
                        (rs, n) -> new CargoBloqueado(rs.getLong("id"), rs.getBigDecimal("copago"),
                                rs.getBigDecimal("pagado"))));
    }

    @Override
    public List<PendienteVista> pendientes() {
        return jdbc.query("SELECT * FROM (SELECT c.id, c.paciente_id, p.apellido || ', ' || p.nombre AS paciente, "
                        + "c.fecha, t.inicio, pr.nombre AS prestacion, c.copago, " + PAGADO + " AS pagado "
                        + "FROM cargo c JOIN paciente p ON p.id = c.paciente_id JOIN turno t ON t.id = c.turno_id "
                        + "JOIN prestacion pr ON pr.id = c.prestacion_id) x WHERE x.copago > x.pagado "
                        + "ORDER BY x.inicio, x.id", new MapSqlParameterSource(),
                (rs, n) -> {
                    BigDecimal copago = rs.getBigDecimal("copago");
                    BigDecimal pagado = rs.getBigDecimal("pagado");
                    return new PendienteVista(rs.getLong("id"), rs.getLong("paciente_id"), rs.getString("paciente"),
                            Fechas.fecha(rs, "fecha"), Fechas.instante(rs, "inicio"), rs.getString("prestacion"),
                            copago, pagado, copago.subtract(pagado));
                });
    }

    @Override
    public List<SinPrecioVista> sinPrecio() {
        return jdbc.query("SELECT t.id, t.paciente_id, p.apellido || ', ' || p.nombre AS paciente, "
                        + "CAST(t.inicio AT TIME ZONE :zona AS DATE) AS fecha, t.inicio, pr.nombre AS prestacion "
                        + "FROM turno t JOIN paciente p ON p.id = t.paciente_id JOIN prestacion pr ON pr.id = t.prestacion_id "
                        + "WHERE t.estado = 'ATENDIDO' AND NOT EXISTS (SELECT 1 FROM cargo c WHERE c.turno_id = t.id) "
                        + "AND NOT EXISTS (SELECT 1 FROM arancel a WHERE a.prestacion_id = t.prestacion_id) "
                        + "ORDER BY t.inicio, t.id", new MapSqlParameterSource("zona", zona.getId()),
                (rs, n) -> new SinPrecioVista(rs.getLong("id"), rs.getLong("paciente_id"), rs.getString("paciente"),
                        Fechas.fecha(rs, "fecha"), Fechas.instante(rs, "inicio"), rs.getString("prestacion")));
    }

    @Override
    public List<CargoVista> delPaciente(long pacienteId) {
        return jdbc.query("SELECT c.id, c.fecha, t.inicio, pr.nombre AS prestacion, c.precio, c.porcentaje_cobertura, "
                        + "c.cobertura, c.copago, " + PAGADO + " AS pagado FROM cargo c "
                        + "JOIN turno t ON t.id = c.turno_id JOIN prestacion pr ON pr.id = c.prestacion_id "
                        + "WHERE c.paciente_id = :paciente ORDER BY t.inicio DESC, c.id DESC",
                new MapSqlParameterSource("paciente", pacienteId),
                (rs, n) -> {
                    BigDecimal copago = rs.getBigDecimal("copago");
                    BigDecimal pagado = rs.getBigDecimal("pagado");
                    return new CargoVista(rs.getLong("id"), Fechas.fecha(rs, "fecha"), Fechas.instante(rs, "inicio"),
                            rs.getString("prestacion"), rs.getBigDecimal("precio"), rs.getInt("porcentaje_cobertura"),
                            rs.getBigDecimal("cobertura"), copago, pagado, copago.subtract(pagado), List.of());
                });
    }

    @Override
    public Optional<String> nombrePaciente(long pacienteId) {
        return jdbc.query("SELECT apellido || ', ' || nombre AS paciente FROM paciente WHERE id = :id",
                new MapSqlParameterSource("id", pacienteId), (rs, n) -> rs.getString("paciente")).stream().findFirst();
    }
}
