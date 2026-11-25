package ar.turnos.odontologia.caja.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Conversión de fechas de JDBC para la caja. */
final class Fechas {

    private Fechas() {
    }

    static Instant instante(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime valor = rs.getObject(columna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }

    static LocalDate fecha(ResultSet rs, String columna) throws SQLException {
        return rs.getObject(columna, LocalDate.class);
    }

    static OffsetDateTime aOffset(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }
}
