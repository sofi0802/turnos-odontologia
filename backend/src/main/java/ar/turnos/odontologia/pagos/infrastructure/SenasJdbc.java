package ar.turnos.odontologia.pagos.infrastructure;

import ar.turnos.odontologia.pagos.application.Senas;
import ar.turnos.odontologia.pagos.domain.Causa;
import ar.turnos.odontologia.pagos.domain.EstadoSena;
import ar.turnos.odontologia.pagos.domain.Sena;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Las señas por JDBC (design D8). Lee el estado del turno sin mapear la tabla de la agenda. */
class SenasJdbc implements Senas {

    private static final String COLUMNAS = "s.id, s.turno_id, s.paciente_id, s.porcentaje, s.copago_base, s.importe, "
            + "s.estado, s.vence_en, s.pagada_en, s.referencia, s.pago_externo_id, s.link_pago, s.cobro_id, "
            + "s.importe_a_reintegrar, s.importe_reintegrado, s.causa_cancelacion, t.estado AS estado_turno "
            + "FROM sena s JOIN turno t ON t.id = s.turno_id";

    private static final RowMapper<Sena> FILA = SenasJdbc::sena;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate nombrados;

    SenasJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.nombrados = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public long crear(long turnoId, long pacienteId, int porcentaje, BigDecimal copagoBase, BigDecimal importe,
                      Instant venceEn, String referencia, Instant ahora) {
        Long id = jdbc.queryForObject("INSERT INTO sena(turno_id, paciente_id, porcentaje, copago_base, importe, estado, "
                        + "vence_en, referencia, creada_en) VALUES (?, ?, ?, ?, ?, 'PENDIENTE', ?, ?, ?) RETURNING id",
                Long.class, turnoId, pacienteId, porcentaje, copagoBase, importe, utc(venceEn), referencia, utc(ahora));
        return id == null ? 0 : id;
    }

    @Override
    public Optional<Sena> porId(long id) {
        return jdbc.query("SELECT " + COLUMNAS + " WHERE s.id = ?", FILA, id).stream().findFirst();
    }

    @Override
    public Optional<Sena> porIdBloqueada(long id) {
        return jdbc.query("SELECT " + COLUMNAS + " WHERE s.id = ? FOR UPDATE OF s", FILA, id).stream().findFirst();
    }

    @Override
    public Optional<Sena> porIdSinEspera(long id) {
        return jdbc.query("SELECT " + COLUMNAS + " WHERE s.id = ? FOR UPDATE OF s SKIP LOCKED", FILA, id).stream()
                .findFirst();
    }

    @Override
    public Optional<Sena> porReferenciaBloqueada(String referencia) {
        return jdbc.query("SELECT " + COLUMNAS + " WHERE s.referencia = ? FOR UPDATE OF s", FILA, referencia).stream()
                .findFirst();
    }

    @Override
    public Map<Long, Sena> deTurnos(Collection<Long> turnoIds) {
        Map<Long, Sena> resultado = new LinkedHashMap<>();
        if (turnoIds.isEmpty()) {
            return resultado;
        }
        // La no terminal primero; si no hay, la más reciente.
        List<Sena> senas = nombrados.query("SELECT " + COLUMNAS + " WHERE s.turno_id IN (:ids) ORDER BY s.turno_id, "
                + "CASE WHEN s.estado IN ('PENDIENTE','PAGADA') THEN 0 ELSE 1 END, s.id DESC",
                new MapSqlParameterSource("ids", turnoIds), FILA);
        for (Sena sena : senas) {
            resultado.putIfAbsent(sena.turnoId(), sena);
        }
        return resultado;
    }

    @Override
    public List<Long> idsEnEstados(Collection<EstadoSena> estados) {
        return nombrados.queryForList("SELECT id FROM sena WHERE estado IN (:estados) ORDER BY id",
                new MapSqlParameterSource("estados", estados.stream().map(Enum::name).toList()), Long.class);
    }

    @Override
    public List<Long> idsConReintegroPendiente() {
        return jdbc.queryForList("SELECT id FROM sena WHERE importe_a_reintegrar > importe_reintegrado ORDER BY id",
                Long.class);
    }

    @Override
    public boolean registrarMovimiento(Long senaId, Movimiento tipo, String idExterno, BigDecimal importe,
                                       String detalle, Instant ahora) {
        return jdbc.update("INSERT INTO sena_movimiento(sena_id, tipo, id_externo, importe, registrado_en, detalle) "
                        + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (tipo, id_externo) WHERE id_externo IS NOT NULL DO NOTHING",
                senaId, tipo.name(), idExterno, importe, utc(ahora), detalle) == 1;
    }

    @Override
    public void guardarLink(long id, String link) {
        jdbc.update("UPDATE sena SET link_pago = ? WHERE id = ?", link, id);
    }

    @Override
    public void marcarPagada(long id, String pagoExternoId, Instant pagadaEn) {
        jdbc.update("UPDATE sena SET estado = 'PAGADA', pago_externo_id = ?, pagada_en = ?, link_pago = NULL "
                + "WHERE id = ?", pagoExternoId, utc(pagadaEn), id);
    }

    @Override
    public void registrarPagoTardio(long id, String pagoExternoId, Instant pagadaEn) {
        jdbc.update("UPDATE sena SET pago_externo_id = ?, pagada_en = ?, link_pago = NULL WHERE id = ?",
                pagoExternoId, utc(pagadaEn), id);
    }

    @Override
    public void cambiarEstado(long id, EstadoSena estado) {
        jdbc.update("UPDATE sena SET estado = ? WHERE id = ?", estado.name(), id);
    }

    @Override
    public void fijarReintegro(long id, BigDecimal aReintegrar) {
        jdbc.update("UPDATE sena SET importe_a_reintegrar = ? WHERE id = ?", aReintegrar, id);
    }

    @Override
    public void registrarReintegrado(long id, BigDecimal reintegrado) {
        jdbc.update("UPDATE sena SET importe_reintegrado = ? WHERE id = ?", reintegrado, id);
    }

    @Override
    public void moverATurno(long id, long turnoNuevo) {
        jdbc.update("UPDATE sena SET turno_id = ? WHERE id = ?", turnoNuevo, id);
    }

    @Override
    public void imputar(long id, long cobroId) {
        jdbc.update("UPDATE sena SET estado = 'IMPUTADA', cobro_id = ? WHERE id = ?", cobroId, id);
    }

    private static Sena sena(ResultSet fila, int numero) throws SQLException {
        String causa = fila.getString("causa_cancelacion");
        Long cobro = fila.getObject("cobro_id", Long.class);
        return new Sena(fila.getLong("id"), fila.getLong("turno_id"), fila.getLong("paciente_id"),
                fila.getInt("porcentaje"), fila.getBigDecimal("copago_base"), fila.getBigDecimal("importe"),
                EstadoSena.valueOf(fila.getString("estado")), instante(fila.getTimestamp("vence_en")),
                instante(fila.getTimestamp("pagada_en")), fila.getString("referencia"),
                fila.getString("pago_externo_id"), fila.getString("link_pago"), cobro,
                fila.getBigDecimal("importe_a_reintegrar"),
                fila.getBigDecimal("importe_reintegrado"), causa == null ? null : Causa.valueOf(causa),
                fila.getString("estado_turno"));
    }

    private static Instant instante(Timestamp valor) {
        return valor == null ? null : valor.toInstant();
    }

    private static java.time.OffsetDateTime utc(Instant instante) {
        return instante == null ? null : instante.atOffset(ZoneOffset.UTC);
    }
}
