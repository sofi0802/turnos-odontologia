package ar.turnos.odontologia.agenda.listaespera.infrastructure;

import ar.turnos.odontologia.agenda.listaespera.application.ListaEsperaRepository;
import ar.turnos.odontologia.agenda.listaespera.domain.EstadoInscripcion;
import ar.turnos.odontologia.agenda.listaespera.domain.EstadoOferta;
import ar.turnos.odontologia.agenda.listaespera.domain.Inscripcion;
import ar.turnos.odontologia.agenda.listaespera.domain.Oferta;
import ar.turnos.odontologia.agenda.listaespera.domain.RangoDeFechas;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persistencia de la lista de espera con JDBC (C-15, design D9). */
@Repository
class ListaEsperaRepositoryJdbc implements ListaEsperaRepository {

    private static final String COLUMNAS_INSCRIPCION =
            "i.id, i.paciente_id, i.prestacion_id, i.profesional_id, i.desde, i.hasta, i.estado, i.inscripta_en";

    private static final String COLUMNAS_OFERTA =
            "o.id, o.inscripcion_id, o.turno_liberado_id, o.profesional_id, o.inicio, o.estado, o.vence_en";

    /** La fila de la lista: paciente, prestación, profesional y la oferta pendiente, si la hay. */
    private static final String FILAS = """
            SELECT i.id, i.paciente_id, pa.nombre || ' ' || pa.apellido AS paciente_nombre, pa.dni,
                   i.prestacion_id, pr.nombre AS prestacion_nombre, i.profesional_id,
                   CASE WHEN p.id IS NULL THEN NULL ELSE p.nombre || ' ' || p.apellido END AS profesional_nombre,
                   i.desde, i.hasta, i.inscripta_en, o.inicio AS oferta_inicio, o.vence_en AS oferta_vence_en
            FROM inscripcion_lista_espera i
            JOIN paciente pa ON pa.id = i.paciente_id
            JOIN prestacion pr ON pr.id = i.prestacion_id
            LEFT JOIN profesional p ON p.id = i.profesional_id
            LEFT JOIN oferta_lista_espera o ON o.inscripcion_id = i.id AND o.estado = 'PENDIENTE'
            WHERE i.estado = 'ACTIVA'
            """;

    private final JdbcTemplate jdbc;

    ListaEsperaRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- inscripciones ----------

    @Override
    public boolean existeActiva(Long pacienteId, Long prestacionId) {
        Boolean existe = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM inscripcion_lista_espera "
                + "WHERE paciente_id = ? AND prestacion_id = ? AND estado = 'ACTIVA')", Boolean.class,
                pacienteId, prestacionId);
        return Boolean.TRUE.equals(existe);
    }

    @Override
    public Inscripcion insertar(Inscripcion inscripcion, String actor) {
        // ON CONFLICT DO NOTHING: un índice único que rechaza la fila no aborta la transacción de PostgreSQL.
        Long id = jdbc.query("""
                INSERT INTO inscripcion_lista_espera (paciente_id, prestacion_id, profesional_id, desde, hasta,
                                                      estado, inscripta_en, inscripta_por)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING RETURNING id
                """, (rs, fila) -> rs.getLong("id"), inscripcion.pacienteId(), inscripcion.prestacionId(),
                inscripcion.profesionalId(), Date.valueOf(inscripcion.rango().desde()),
                Date.valueOf(inscripcion.rango().hasta()), inscripcion.estado().name(),
                Timestamp.from(inscripcion.inscriptaEn()), actor).stream().findFirst()
                .orElseThrow(InscripcionDuplicadaException::new);
        return new Inscripcion(id, inscripcion.pacienteId(), inscripcion.prestacionId(),
                inscripcion.profesionalId(), inscripcion.rango(), inscripcion.estado(), inscripcion.inscriptaEn());
    }

    @Override
    public Optional<Inscripcion> inscripcion(Long id) {
        return jdbc.query("SELECT " + COLUMNAS_INSCRIPCION + " FROM inscripcion_lista_espera i WHERE i.id = ?",
                (rs, fila) -> inscripcion(rs), id).stream().findFirst();
    }

    @Override
    public Optional<Inscripcion> inscripcionParaActualizar(Long id) {
        return jdbc.query("SELECT " + COLUMNAS_INSCRIPCION
                        + " FROM inscripcion_lista_espera i WHERE i.id = ? FOR UPDATE",
                (rs, fila) -> inscripcion(rs), id).stream().findFirst();
    }

    @Override
    public void cerrarInscripcion(Long id, EstadoInscripcion estado, Instant ahora) {
        jdbc.update("UPDATE inscripcion_lista_espera SET estado = ?, cerrada_en = ? WHERE id = ?",
                estado.name(), Timestamp.from(ahora), id);
    }

    @Override
    public int vencerInscripciones(LocalDate hoy, Instant ahora) {
        return jdbc.update("UPDATE inscripcion_lista_espera SET estado = 'VENCIDA', cerrada_en = ? "
                + "WHERE estado = 'ACTIVA' AND hasta < ?", Timestamp.from(ahora), Date.valueOf(hoy));
    }

    @Override
    public List<FilaInscripcion> activas() {
        return jdbc.query(FILAS + " ORDER BY i.inscripta_en, i.id", (rs, fila) -> fila(rs));
    }

    @Override
    public List<FilaInscripcion> activasDe(Long pacienteId) {
        return jdbc.query(FILAS + " AND i.paciente_id = ? ORDER BY i.inscripta_en, i.id",
                (rs, fila) -> fila(rs), pacienteId);
    }

    @Override
    public List<Inscripcion> candidatas(Long profesionalId, LocalDate fecha, Long turnoLiberadoId, int limite) {
        return jdbc.query("SELECT " + COLUMNAS_INSCRIPCION + """
                 FROM inscripcion_lista_espera i
                JOIN paciente pa ON pa.id = i.paciente_id
                WHERE i.estado = 'ACTIVA'
                  AND ? BETWEEN i.desde AND i.hasta
                  AND (i.profesional_id IS NULL OR i.profesional_id = ?)
                  AND pa.consentimiento_comunicaciones
                  AND NOT EXISTS (SELECT 1 FROM oferta_lista_espera o
                                  WHERE o.inscripcion_id = i.id AND o.estado = 'PENDIENTE')
                  AND NOT EXISTS (SELECT 1 FROM oferta_lista_espera o
                                  WHERE o.inscripcion_id = i.id AND o.turno_liberado_id = ?)
                ORDER BY i.inscripta_en, i.id
                LIMIT ?
                FOR UPDATE OF i SKIP LOCKED
                """, (rs, fila) -> inscripcion(rs), Date.valueOf(fecha), profesionalId, turnoLiberadoId, limite);
    }

    // ---------- ofertas ----------

    @Override
    public boolean hayPendientePara(Long turnoLiberadoId) {
        Boolean hay = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM oferta_lista_espera "
                + "WHERE turno_liberado_id = ? AND estado = 'PENDIENTE')", Boolean.class, turnoLiberadoId);
        return Boolean.TRUE.equals(hay);
    }

    @Override
    public Optional<Oferta> insertarOferta(Oferta oferta, String huellaToken, Instant ahora) {
        return jdbc.query("""
                INSERT INTO oferta_lista_espera (inscripcion_id, turno_liberado_id, profesional_id, inicio, estado,
                                                 ofrecida_en, vence_en, token_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING RETURNING id
                """, (rs, fila) -> rs.getLong("id"), oferta.inscripcionId(), oferta.turnoLiberadoId(),
                oferta.profesionalId(), Timestamp.from(oferta.inicio()), oferta.estado().name(),
                Timestamp.from(ahora), Timestamp.from(oferta.venceEn()), huellaToken).stream().findFirst()
                .map(id -> new Oferta(id, oferta.inscripcionId(), oferta.turnoLiberadoId(), oferta.profesionalId(),
                        oferta.inicio(), oferta.estado(), oferta.venceEn()));
    }

    @Override
    public Optional<Oferta> ofertaParaActualizar(String huellaToken) {
        return jdbc.query("SELECT " + COLUMNAS_OFERTA + " FROM oferta_lista_espera o WHERE o.token_hash = ? "
                + "FOR UPDATE", (rs, fila) -> oferta(rs), huellaToken).stream().findFirst();
    }

    @Override
    public Optional<Oferta> ofertaPendienteParaActualizar(Long inscripcionId) {
        return jdbc.query("SELECT " + COLUMNAS_OFERTA + " FROM oferta_lista_espera o "
                        + "WHERE o.inscripcion_id = ? AND o.estado = 'PENDIENTE' FOR UPDATE",
                (rs, fila) -> oferta(rs), inscripcionId).stream().findFirst();
    }

    @Override
    public Optional<VistaOferta> vista(String huellaToken) {
        return jdbc.query("""
                SELECT o.inicio, p.nombre || ' ' || p.apellido AS profesional_nombre, o.vence_en, o.estado
                FROM oferta_lista_espera o
                JOIN profesional p ON p.id = o.profesional_id
                WHERE o.token_hash = ?
                """, (rs, fila) -> new VistaOferta(instante(rs, "inicio"), rs.getString("profesional_nombre"),
                instante(rs, "vence_en"), EstadoOferta.valueOf(rs.getString("estado"))), huellaToken)
                .stream().findFirst();
    }

    @Override
    public void responder(Long ofertaId, EstadoOferta estado, Instant ahora) {
        jdbc.update("UPDATE oferta_lista_espera SET estado = ?, respondida_en = ? WHERE id = ?",
                estado.name(), ahora == null ? null : Timestamp.from(ahora), ofertaId);
    }

    @Override
    public void registrarTurnoCreado(Long ofertaId, Long turnoId) {
        jdbc.update("UPDATE oferta_lista_espera SET turno_creado_id = ? WHERE id = ?", turnoId, ofertaId);
    }

    @Override
    public List<Oferta> vencerOfertas(Instant ahora) {
        return jdbc.query("""
                UPDATE oferta_lista_espera o SET estado = 'VENCIDA', respondida_en = ?
                WHERE o.id IN (SELECT id FROM oferta_lista_espera
                               WHERE estado = 'PENDIENTE' AND vence_en <= ?
                               ORDER BY vence_en, id FOR UPDATE SKIP LOCKED)
                RETURNING
                """ + COLUMNAS_OFERTA, (rs, fila) -> oferta(rs), Timestamp.from(ahora), Timestamp.from(ahora));
    }

    @Override
    public List<AvisoPendiente> avisosSinEnviar(Instant ahora, int maximoIntentos) {
        return jdbc.query("""
                SELECT o.id, i.paciente_id, o.inicio, o.vence_en
                FROM oferta_lista_espera o
                JOIN inscripcion_lista_espera i ON i.id = o.inscripcion_id
                WHERE o.estado = 'PENDIENTE' AND NOT o.aviso_enviado AND o.aviso_intentos < ? AND o.vence_en > ?
                ORDER BY o.id
                FOR UPDATE OF o SKIP LOCKED
                """, (rs, fila) -> new AvisoPendiente(rs.getLong("id"), rs.getLong("paciente_id"),
                instante(rs, "inicio"), instante(rs, "vence_en")), maximoIntentos, Timestamp.from(ahora));
    }

    @Override
    public void registrarAviso(Long ofertaId, boolean enviado, String huellaNueva) {
        if (huellaNueva != null) {
            jdbc.update("UPDATE oferta_lista_espera SET token_hash = ? WHERE id = ?", huellaNueva, ofertaId);
            return;
        }
        jdbc.update("UPDATE oferta_lista_espera SET aviso_enviado = ?, aviso_intentos = aviso_intentos + 1 "
                + "WHERE id = ?", enviado, ofertaId);
    }

    // ---------- lectura ----------

    private static Inscripcion inscripcion(ResultSet rs) throws SQLException {
        return new Inscripcion(rs.getLong("id"), rs.getLong("paciente_id"), rs.getLong("prestacion_id"),
                rs.getObject("profesional_id", Long.class),
                new RangoDeFechas(rs.getDate("desde").toLocalDate(), rs.getDate("hasta").toLocalDate()),
                EstadoInscripcion.valueOf(rs.getString("estado")), instante(rs, "inscripta_en"));
    }

    private static Oferta oferta(ResultSet rs) throws SQLException {
        return new Oferta(rs.getLong("id"), rs.getLong("inscripcion_id"), rs.getLong("turno_liberado_id"),
                rs.getLong("profesional_id"), instante(rs, "inicio"), EstadoOferta.valueOf(rs.getString("estado")),
                instante(rs, "vence_en"));
    }

    private static FilaInscripcion fila(ResultSet rs) throws SQLException {
        return new FilaInscripcion(rs.getLong("id"), rs.getLong("paciente_id"), rs.getString("paciente_nombre"),
                rs.getString("dni"), rs.getLong("prestacion_id"), rs.getString("prestacion_nombre"),
                rs.getObject("profesional_id", Long.class), rs.getString("profesional_nombre"),
                rs.getDate("desde").toLocalDate(), rs.getDate("hasta").toLocalDate(), instante(rs, "inscripta_en"),
                instante(rs, "oferta_inicio"), instante(rs, "oferta_vence_en"));
    }

    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime valor = rs.getObject(columna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }
}
